import { googleClientId, googleClientSecret, secretValue } from "../common/config";
import type {
  AppointmentPayload,
  AuthorizationResult,
  BusyRange,
  CalendarProvider,
  Connection,
  TokenSet,
  WebhookEvent,
  WebhookRequest,
} from "./provider";
import { WebhookSignatureError, header, httpJson } from "./provider";
import { hmacSha256Hex, randomToken, safeEqualHex } from "./crypto";

const AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const API = "https://www.googleapis.com/calendar/v3";
const SCOPES = [
  "https://www.googleapis.com/auth/calendar.events",
  "https://www.googleapis.com/auth/calendar.freebusy",
  "openid",
  "email",
];

interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  expires_in: number;
  id_token?: string;
}

function tokenSet(response: TokenResponse, previousRefresh: string | null = null): TokenSet {
  return {
    accessToken: response.access_token,
    refreshToken: response.refresh_token ?? previousRefresh,
    expiresAt: Date.now() + response.expires_in * 1000,
  };
}

function emailFromIdToken(idToken: string | undefined): string | null {
  if (!idToken) return null;
  const parts = idToken.split(".");
  if (parts.length < 2) return null;
  try {
    const payload = JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8")) as { email?: string };
    return payload.email ?? null;
  } catch {
    return null;
  }
}

function eventBody(appointment: AppointmentPayload): Record<string, unknown> {
  return {
    summary: appointment.title,
    description: appointment.description,
    location: appointment.location,
    start: { dateTime: new Date(appointment.start).toISOString(), timeZone: appointment.timezone },
    end: { dateTime: new Date(appointment.end).toISOString(), timeZone: appointment.timezone },
    extendedProperties: { private: { terminAppointmentId: appointment.id } },
    reminders: { useDefault: true },
  };
}

/** Kanal za Google push obaveštenja nosi tajni token (HMAC id-a kanala) koji proveravamo pri svakom pozivu. */
export function channelToken(channelId: string, secret: string): string {
  return hmacSha256Hex(secret, `google-channel:${channelId}`);
}

export class GoogleCalendarProvider implements CalendarProvider {
  readonly id = "google" as const;

  constructor(private readonly channelSecret: () => string) {}

  isConfigured(): boolean {
    return googleClientId.value().length > 0 && secretValue(googleClientSecret).length > 0;
  }

  authorizationUrl(state: string, redirectUri: string, codeChallenge: string): string {
    const params = new URLSearchParams({
      client_id: googleClientId.value(),
      redirect_uri: redirectUri,
      response_type: "code",
      scope: SCOPES.join(" "),
      access_type: "offline",
      prompt: "consent",
      include_granted_scopes: "true",
      state,
      code_challenge: codeChallenge,
      code_challenge_method: "S256",
    });
    return `${AUTH_URL}?${params.toString()}`;
  }

  async exchangeCode(code: string, redirectUri: string, codeVerifier: string): Promise<AuthorizationResult> {
    const response = await httpJson<TokenResponse>(TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        code,
        client_id: googleClientId.value(),
        client_secret: secretValue(googleClientSecret),
        redirect_uri: redirectUri,
        grant_type: "authorization_code",
        code_verifier: codeVerifier,
      }).toString(),
    });
    const email = emailFromIdToken(response.id_token);
    return {
      tokens: tokenSet(response),
      accountId: email ?? "primary",
      accountLabel: email ?? "Google Calendar",
      calendarId: "primary",
    };
  }

  async refreshTokens(tokens: TokenSet): Promise<TokenSet> {
    if (!tokens.refreshToken) throw new Error("Google connection has no refresh token");
    const response = await httpJson<TokenResponse>(TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: googleClientId.value(),
        client_secret: secretValue(googleClientSecret),
        refresh_token: tokens.refreshToken,
        grant_type: "refresh_token",
      }).toString(),
    });
    return tokenSet(response, tokens.refreshToken);
  }

  private auth(connection: Connection): Record<string, string> {
    return { Authorization: `Bearer ${connection.tokens.accessToken}`, "Content-Type": "application/json" };
  }

  async getBusy(connection: Connection, from: number, to: number): Promise<BusyRange[]> {
    const calendarId = connection.calendarId ?? "primary";
    const response = await httpJson<{ calendars?: Record<string, { busy?: { start: string; end: string }[] }> }>(
      `${API}/freeBusy`,
      {
        method: "POST",
        headers: this.auth(connection),
        body: JSON.stringify({
          timeMin: new Date(from).toISOString(),
          timeMax: new Date(to).toISOString(),
          items: [{ id: calendarId }],
        }),
      },
    );
    const busy = response.calendars?.[calendarId]?.busy ?? [];
    return busy.map((b) => ({ start: Date.parse(b.start), end: Date.parse(b.end) })).filter((b) => b.end > b.start);
  }

  async createEvent(connection: Connection, appointment: AppointmentPayload): Promise<string | null> {
    const calendarId = encodeURIComponent(connection.calendarId ?? "primary");
    const event = await httpJson<{ id: string }>(`${API}/calendars/${calendarId}/events`, {
      method: "POST",
      headers: this.auth(connection),
      body: JSON.stringify(eventBody(appointment)),
    });
    return event.id;
  }

  async updateEvent(connection: Connection, externalId: string, appointment: AppointmentPayload): Promise<string | null> {
    const calendarId = encodeURIComponent(connection.calendarId ?? "primary");
    await httpJson(`${API}/calendars/${calendarId}/events/${encodeURIComponent(externalId)}`, {
      method: "PATCH",
      headers: this.auth(connection),
      body: JSON.stringify(eventBody(appointment)),
    });
    return externalId;
  }

  async deleteEvent(connection: Connection, externalId: string): Promise<void> {
    const calendarId = encodeURIComponent(connection.calendarId ?? "primary");
    await httpJson(`${API}/calendars/${calendarId}/events/${encodeURIComponent(externalId)}`, {
      method: "DELETE",
      headers: this.auth(connection),
    });
  }

  async subscribe(connection: Connection, webhookUrl: string): Promise<string | null> {
    const calendarId = encodeURIComponent(connection.calendarId ?? "primary");
    const channelId = `termin-${randomToken(12)}`;
    const channel = await httpJson<{ resourceId: string }>(`${API}/calendars/${calendarId}/events/watch`, {
      method: "POST",
      headers: this.auth(connection),
      body: JSON.stringify({
        id: channelId,
        type: "web_hook",
        address: webhookUrl,
        token: channelToken(channelId, this.channelSecret()),
      }),
    });
    return `${channelId}|${channel.resourceId}`;
  }

  async unsubscribe(connection: Connection, subscriptionId: string): Promise<void> {
    const [id, resourceId] = subscriptionId.split("|");
    await httpJson(`${API}/channels/stop`, {
      method: "POST",
      headers: this.auth(connection),
      body: JSON.stringify({ id, resourceId }),
    });
  }

  /**
   * Google šalje samo signal da se kalendar promenio (bez sadržaja). Potpis je tajni token
   * kanala (X-Goog-Channel-Token) koji smo sami zadali pri pretplati.
   */
  parseWebhook(request: WebhookRequest): WebhookEvent {
    const channelId = header(request, "x-goog-channel-id");
    const token = header(request, "x-goog-channel-token");
    const messageNumber = header(request, "x-goog-message-number");
    const state = header(request, "x-goog-resource-state");
    if (!channelId || !token || !safeEqualHex(token, channelToken(channelId, this.channelSecret()))) {
      throw new WebhookSignatureError();
    }
    const eventId = `${channelId}:${messageNumber}`;
    if (state === "sync") return { kind: "ignored", eventId };
    return { kind: "changed", eventId, accountId: channelId };
  }
}
