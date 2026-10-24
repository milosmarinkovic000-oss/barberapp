import { calcomClientId, calcomClientSecret, calcomWebhookSecret, secretValue } from "../common/config";
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
import { hmacSha256Hex, safeEqualHex } from "./crypto";

const AUTH_URL = "https://app.cal.com/auth/oauth2/authorize";
const TOKEN_URL = "https://api.cal.com/v2/auth/oauth2/token";
const API = "https://api.cal.com/v2";
const SCOPES = ["PROFILE_READ", "EVENT_TYPE_READ", "BOOKING_READ", "BOOKING_WRITE", "WEBHOOK_WRITE"];
/** Verzije zaglavlja `cal-api-version` iz trenutne dokumentacije API v2. */
const BOOKINGS_VERSION = "2026-02-25";
const EVENT_TYPES_VERSION = "2024-06-14";

interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  expires_in?: number;
}

interface CalBooking {
  uid: string;
  start: string;
  end: string;
  status: string;
  title?: string;
  rescheduledFromUid?: string;
}

interface CalWebhookPayload {
  uid?: string;
  title?: string;
  startTime?: string;
  endTime?: string;
  organizer?: { id?: number; email?: string };
  attendees?: { name?: string }[];
  rescheduleUid?: string;
  fromReschedule?: string;
  rescheduledFromUid?: string;
}

interface CalWebhookBody {
  triggerEvent: string;
  createdAt: string;
  payload: CalWebhookPayload;
}

function tokenSet(response: TokenResponse, previousRefresh: string | null = null): TokenSet {
  return {
    accessToken: response.access_token,
    refreshToken: response.refresh_token ?? previousRefresh,
    expiresAt: Date.now() + (response.expires_in ?? 1800) * 1000,
  };
}

/** `x-cal-signature-256` = HMAC-SHA256(heks) nad sirovim telom zahteva, sa tajnom webhook-a. */
export function verifyCalcomSignature(signature: string, rawBody: Buffer | string, secret: string): boolean {
  if (!signature || !secret) return false;
  return safeEqualHex(hmacSha256Hex(secret, rawBody), signature);
}

export class CalcomProvider implements CalendarProvider {
  readonly id = "calcom" as const;

  isConfigured(): boolean {
    return calcomClientId.value().length > 0 && secretValue(calcomClientSecret).length > 0;
  }

  authorizationUrl(state: string, redirectUri: string, codeChallenge: string): string {
    const params = new URLSearchParams({
      client_id: calcomClientId.value(),
      redirect_uri: redirectUri,
      state,
      scope: SCOPES.join(" "),
      code_challenge: codeChallenge,
      code_challenge_method: "S256",
    });
    return `${AUTH_URL}?${params.toString()}`;
  }

  async exchangeCode(code: string, redirectUri: string, codeVerifier: string): Promise<AuthorizationResult> {
    const response = await httpJson<TokenResponse>(TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        client_id: calcomClientId.value(),
        client_secret: secretValue(calcomClientSecret),
        grant_type: "authorization_code",
        code,
        redirect_uri: redirectUri,
        code_verifier: codeVerifier,
      }),
    });
    const set = tokenSet(response);
    const headers = { Authorization: `Bearer ${set.accessToken}` };
    const me = await httpJson<{ data: { id: number; email: string; username?: string } }>(`${API}/me`, { headers });
    const types = await httpJson<{ data: { id: number; lengthInMinutes?: number; hidden?: boolean }[] }>(
      `${API}/event-types`,
      { headers: { ...headers, "cal-api-version": EVENT_TYPES_VERSION } },
    );
    return {
      tokens: set,
      accountId: String(me.data.id),
      accountLabel: me.data.email || me.data.username || "Cal.com",
      eventTypeId: types.data.find((t) => t.hidden !== true)?.id,
    };
  }

  async refreshTokens(current: TokenSet): Promise<TokenSet> {
    if (!current.refreshToken) throw new Error("Cal.com connection has no refresh token");
    const response = await httpJson<TokenResponse>(TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        client_id: calcomClientId.value(),
        client_secret: secretValue(calcomClientSecret),
        grant_type: "refresh_token",
        refresh_token: current.refreshToken,
      }),
    });
    return tokenSet(response, current.refreshToken);
  }

  private headers(connection: Connection, version = BOOKINGS_VERSION): Record<string, string> {
    return {
      Authorization: `Bearer ${connection.tokens.accessToken}`,
      "Content-Type": "application/json",
      "cal-api-version": version,
    };
  }

  /** Zauzetost = predstojeći i nepotvrđeni booking-i frizera u traženom opsegu. */
  async getBusy(connection: Connection, from: number, to: number): Promise<BusyRange[]> {
    const result: BusyRange[] = [];
    let cursor: string | null = null;
    for (let page = 0; page < 10; page++) {
      const params = new URLSearchParams({
        afterStart: new Date(from).toISOString(),
        beforeEnd: new Date(to).toISOString(),
        status: "upcoming,unconfirmed",
        limit: "100",
      });
      if (cursor) params.set("cursor", cursor);
      const response: { data: CalBooking[]; pagination?: { nextCursor?: string | null } } = await httpJson(
        `${API}/bookings?${params.toString()}`,
        { headers: this.headers(connection) },
      );
      for (const b of response.data) {
        result.push({ start: Date.parse(b.start), end: Date.parse(b.end) });
      }
      cursor = response.pagination?.nextCursor ?? null;
      if (!cursor) break;
    }
    return result.filter((b) => b.end > b.start);
  }

  async createEvent(connection: Connection, appointment: AppointmentPayload): Promise<string | null> {
    if (connection.eventTypeId === undefined) return null;
    const response = await httpJson<{ data: { uid: string } }>(`${API}/bookings`, {
      method: "POST",
      headers: this.headers(connection),
      body: JSON.stringify({
        start: new Date(appointment.start).toISOString(),
        eventTypeId: connection.eventTypeId,
        lengthInMinutes: Math.round((appointment.end - appointment.start) / 60_000),
        attendee: {
          name: appointment.customerName,
          timeZone: appointment.timezone,
          ...(appointment.customerEmail ? { email: appointment.customerEmail } : {}),
          ...(appointment.customerPhone ? { phoneNumber: appointment.customerPhone } : {}),
        },
        metadata: { terminAppointmentId: appointment.id },
      }),
    });
    return response.data.uid;
  }

  async updateEvent(connection: Connection, externalId: string, appointment: AppointmentPayload): Promise<string | null> {
    const response = await httpJson<{ data: { uid: string } }>(
      `${API}/bookings/${encodeURIComponent(externalId)}/reschedule`,
      {
        method: "POST",
        headers: this.headers(connection),
        body: JSON.stringify({
          start: new Date(appointment.start).toISOString(),
          reschedulingReason: "Promenjeno u aplikaciji Termin",
        }),
      },
    );
    return response.data.uid;
  }

  async deleteEvent(connection: Connection, externalId: string): Promise<void> {
    await httpJson(`${API}/bookings/${encodeURIComponent(externalId)}/cancel`, {
      method: "POST",
      headers: this.headers(connection),
      body: JSON.stringify({ cancellationReason: "Otkazano u aplikaciji Termin" }),
    });
  }

  async getBooking(connection: Connection, uid: string): Promise<CalBooking> {
    const response = await httpJson<{ data: CalBooking }>(`${API}/bookings/${encodeURIComponent(uid)}`, {
      headers: this.headers(connection),
    });
    return response.data;
  }

  async subscribe(connection: Connection, webhookUrl: string): Promise<string | null> {
    const response = await httpJson<{ data: { id: string | number } }>(`${API}/webhooks`, {
      method: "POST",
      headers: this.headers(connection),
      body: JSON.stringify({
        active: true,
        subscriberUrl: webhookUrl,
        triggers: ["BOOKING_CREATED", "BOOKING_CANCELLED", "BOOKING_RESCHEDULED"],
        secret: secretValue(calcomWebhookSecret),
      }),
    });
    return String(response.data.id);
  }

  async unsubscribe(connection: Connection, subscriptionId: string): Promise<void> {
    await httpJson(`${API}/webhooks/${encodeURIComponent(subscriptionId)}`, {
      method: "DELETE",
      headers: this.headers(connection),
    });
  }

  parseWebhook(request: WebhookRequest): WebhookEvent {
    const signature = header(request, "x-cal-signature-256");
    if (!verifyCalcomSignature(signature, request.rawBody, secretValue(calcomWebhookSecret))) {
      throw new WebhookSignatureError();
    }
    const body = JSON.parse(request.rawBody.toString("utf8")) as CalWebhookBody;
    const p = body.payload ?? {};
    const eventId = `${body.triggerEvent}:${p.uid ?? ""}:${body.createdAt}`;
    const accountId = p.organizer?.id !== undefined ? String(p.organizer.id) : "";
    if (!p.uid || !accountId) return { kind: "ignored", eventId };

    const booking = {
      externalId: p.uid,
      start: Date.parse(p.startTime ?? ""),
      end: Date.parse(p.endTime ?? ""),
      title: p.title ?? "",
      customerName: p.attendees?.[0]?.name ?? "",
    };
    switch (body.triggerEvent) {
      case "BOOKING_CREATED":
        return { kind: "created", eventId, accountId, booking };
      case "BOOKING_CANCELLED":
        return { kind: "canceled", eventId, accountId, externalId: p.uid };
      case "BOOKING_RESCHEDULED":
        return {
          kind: "rescheduled",
          eventId,
          accountId,
          booking: { ...booking, previousExternalId: p.rescheduleUid ?? p.fromReschedule ?? p.rescheduledFromUid },
        };
      default:
        return { kind: "ignored", eventId };
    }
  }
}
