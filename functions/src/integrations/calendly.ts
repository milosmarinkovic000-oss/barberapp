import { calendlyClientId, calendlyClientSecret, calendlyWebhookSigningKey, secretValue } from "../common/config";
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
import { ProviderHttpError, WebhookSignatureError, header, httpJson } from "./provider";
import { hmacSha256Hex, safeEqualHex } from "./crypto";

const AUTH_URL = "https://auth.calendly.com/oauth/authorize";
const TOKEN_URL = "https://auth.calendly.com/oauth/token";
const API = "https://api.calendly.com";
const WEEK_MS = 7 * 86_400_000;
/** Calendly preporučuje toleranciju od 3 minuta za vremenski žig potpisa. */
const SIGNATURE_TOLERANCE_MS = 3 * 60_000;

interface TokenResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  owner: string;
  organization: string;
}

interface CalendlyUser {
  resource: { uri: string; name: string; email: string; current_organization: string };
}

interface EventTypeList {
  collection: { uri: string; name: string; active: boolean; duration: number }[];
}

interface ScheduledEvent {
  uri: string;
  name: string;
  start_time: string;
  end_time: string;
  event_memberships?: { user: string }[];
}

interface InviteePayload {
  uri: string;
  name?: string;
  email?: string;
  old_invitee?: string | null;
  rescheduled?: boolean;
  scheduled_event?: ScheduledEvent;
}

interface WebhookBody {
  event: string;
  created_at: string;
  payload: InviteePayload;
}

function basicAuth(): string {
  return `Basic ${Buffer.from(`${calendlyClientId.value()}:${secretValue(calendlyClientSecret)}`).toString("base64")}`;
}

function tokens(response: TokenResponse): TokenSet {
  return {
    accessToken: response.access_token,
    refreshToken: response.refresh_token,
    expiresAt: Date.now() + response.expires_in * 1000,
  };
}

/** Provera `Calendly-Webhook-Signature: t=…,v1=…` (HMAC-SHA256 nad "t.telo"). */
export function verifyCalendlySignature(signatureHeader: string, rawBody: string, signingKey: string, nowMs: number): boolean {
  const parts = Object.fromEntries(
    signatureHeader.split(",").map((p) => {
      const [k, ...rest] = p.split("=");
      return [k.trim(), rest.join("=").trim()];
    }),
  ) as Record<string, string | undefined>;
  const t = parts.t;
  const v1 = parts.v1;
  if (!t || !v1 || !signingKey) return false;
  if (Number(t) * 1000 < nowMs - SIGNATURE_TOLERANCE_MS) return false;
  return safeEqualHex(hmacSha256Hex(signingKey, `${t}.${rawBody}`), v1);
}

export class CalendlyProvider implements CalendarProvider {
  readonly id = "calendly" as const;

  isConfigured(): boolean {
    return calendlyClientId.value().length > 0 && secretValue(calendlyClientSecret).length > 0;
  }

  authorizationUrl(state: string, redirectUri: string, codeChallenge: string): string {
    const params = new URLSearchParams({
      client_id: calendlyClientId.value(),
      response_type: "code",
      redirect_uri: redirectUri,
      state,
      code_challenge_method: "S256",
      code_challenge: codeChallenge,
    });
    return `${AUTH_URL}?${params.toString()}`;
  }

  async exchangeCode(code: string, redirectUri: string, codeVerifier: string): Promise<AuthorizationResult> {
    const response = await httpJson<TokenResponse>(TOKEN_URL, {
      method: "POST",
      headers: { Authorization: basicAuth(), "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "authorization_code",
        code,
        redirect_uri: redirectUri,
        code_verifier: codeVerifier,
      }).toString(),
    });
    const set = tokens(response);
    const headers = { Authorization: `Bearer ${set.accessToken}` };
    const me = await httpJson<CalendlyUser>(`${API}/users/me`, { headers });
    const types = await httpJson<EventTypeList>(
      `${API}/event_types?${new URLSearchParams({ user: me.resource.uri, active: "true", count: "20" }).toString()}`,
      { headers },
    );
    return {
      tokens: set,
      accountId: me.resource.uri,
      accountLabel: me.resource.email || me.resource.name,
      eventTypeUri: types.collection.find((t) => t.active)?.uri,
    };
  }

  async refreshTokens(current: TokenSet): Promise<TokenSet> {
    if (!current.refreshToken) throw new Error("Calendly connection has no refresh token");
    const response = await httpJson<TokenResponse>(TOKEN_URL, {
      method: "POST",
      headers: { Authorization: basicAuth(), "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ grant_type: "refresh_token", refresh_token: current.refreshToken }).toString(),
    });
    return tokens(response);
  }

  private headers(connection: Connection): Record<string, string> {
    return { Authorization: `Bearer ${connection.tokens.accessToken}`, "Content-Type": "application/json" };
  }

  /** `GET /user_busy_times` prima najviše 7 dana po pozivu i ne prima prošlost. */
  async getBusy(connection: Connection, from: number, to: number): Promise<BusyRange[]> {
    const result: BusyRange[] = [];
    let cursor = Math.max(from, Date.now() + 60_000);
    while (cursor < to) {
      const chunkEnd = Math.min(cursor + WEEK_MS - 60_000, to);
      const params = new URLSearchParams({
        user: connection.accountId,
        start_time: new Date(cursor).toISOString(),
        end_time: new Date(chunkEnd).toISOString(),
      });
      const response = await httpJson<{
        collection: { start_time: string; end_time: string; buffered_start_time?: string; buffered_end_time?: string }[];
      }>(`${API}/user_busy_times?${params.toString()}`, { headers: this.headers(connection) });
      for (const b of response.collection) {
        result.push({
          start: Date.parse(b.buffered_start_time ?? b.start_time),
          end: Date.parse(b.buffered_end_time ?? b.end_time),
        });
      }
      cursor = chunkEnd;
    }
    return result.filter((b) => b.end > b.start);
  }

  /**
   * Scheduling API (`POST /invitees`) radi samo na plaćenim Calendly planovima i traži e-poštu
   * klijenta. Kad to nije moguće, aplikacija ostaje izvor istine, a termin se upisuje u kalendar
   * povezan sa Calendly-jem (Google Calendar integracija), pa ga Calendly vidi kao zauzeće.
   */
  async createEvent(connection: Connection, appointment: AppointmentPayload): Promise<string | null> {
    if (!connection.eventTypeUri || !appointment.customerEmail) return null;
    try {
      const response = await httpJson<{ resource: { uri: string; event: string } }>(`${API}/invitees`, {
        method: "POST",
        headers: this.headers(connection),
        body: JSON.stringify({
          event_type: connection.eventTypeUri,
          start_time: new Date(appointment.start).toISOString(),
          invitee: {
            name: appointment.customerName,
            email: appointment.customerEmail,
            timezone: appointment.timezone,
          },
        }),
      });
      return response.resource.event;
    } catch (error) {
      if (error instanceof ProviderHttpError && error.status === 403) return null;
      throw error;
    }
  }

  async updateEvent(connection: Connection, externalId: string, appointment: AppointmentPayload): Promise<string | null> {
    await this.deleteEvent(connection, externalId);
    return this.createEvent(connection, appointment);
  }

  async deleteEvent(connection: Connection, externalId: string): Promise<void> {
    const uuid = externalId.split("/").pop() ?? externalId;
    await httpJson(`${API}/scheduled_events/${encodeURIComponent(uuid)}/cancellation`, {
      method: "POST",
      headers: this.headers(connection),
      body: JSON.stringify({ reason: "Otkazano u aplikaciji Termin" }),
    });
  }

  async subscribe(connection: Connection, webhookUrl: string): Promise<string | null> {
    const me = await httpJson<CalendlyUser>(`${API}/users/me`, { headers: this.headers(connection) });
    const response = await httpJson<{ resource: { uri: string } }>(`${API}/webhook_subscriptions`, {
      method: "POST",
      headers: this.headers(connection),
      body: JSON.stringify({
        url: webhookUrl,
        events: ["invitee.created", "invitee.canceled"],
        organization: me.resource.current_organization,
        user: me.resource.uri,
        scope: "user",
      }),
    });
    return response.resource.uri;
  }

  async unsubscribe(connection: Connection, subscriptionId: string): Promise<void> {
    const uuid = subscriptionId.split("/").pop() ?? subscriptionId;
    await httpJson(`${API}/webhook_subscriptions/${encodeURIComponent(uuid)}`, {
      method: "DELETE",
      headers: this.headers(connection),
    });
  }

  parseWebhook(request: WebhookRequest, nowMs = Date.now()): WebhookEvent {
    const raw = request.rawBody.toString("utf8");
    const signature = header(request, "calendly-webhook-signature");
    if (!verifyCalendlySignature(signature, raw, secretValue(calendlyWebhookSigningKey), nowMs)) {
      throw new WebhookSignatureError();
    }
    const body = JSON.parse(raw) as WebhookBody;
    const invitee = body.payload;
    const event = invitee.scheduled_event;
    const eventId = `${body.event}:${invitee.uri}`;
    const accountId = event?.event_memberships?.[0]?.user ?? "";
    if (!event || !accountId) return { kind: "ignored", eventId };

    if (body.event === "invitee.created") {
      return {
        kind: "created",
        eventId,
        accountId,
        booking: {
          externalId: event.uri,
          start: Date.parse(event.start_time),
          end: Date.parse(event.end_time),
          title: event.name,
          customerName: invitee.name ?? "",
        },
      };
    }
    if (body.event === "invitee.canceled") {
      // Kod promene termina Calendly šalje canceled (rescheduled=true) za stari i created za novi.
      return { kind: "canceled", eventId, accountId, externalId: event.uri };
    }
    return { kind: "ignored", eventId };
  }
}
