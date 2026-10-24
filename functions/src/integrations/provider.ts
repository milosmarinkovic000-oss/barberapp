import type { Provider } from "../domain/types";

export interface TokenSet {
  accessToken: string;
  refreshToken: string | null;
  /** Trenutak isteka access tokena (ms). */
  expiresAt: number;
}

export interface BusyRange {
  start: number;
  end: number;
}

/** Povezan nalog jednog frizera kod jednog provajdera (tokeni su dešifrovani samo u memoriji). */
export interface Connection {
  provider: Provider;
  salonId: string;
  stylistId: string;
  tokens: TokenSet;
  /** Google: id kalendara; Calendly: URI korisnika; Cal.com: id korisnika. */
  accountId: string;
  accountLabel: string;
  /** Google: kalendar u koji se upisuju termini (podrazumevano "primary"). */
  calendarId?: string;
  /** Calendly: URI event type-a za Scheduling API. */
  eventTypeUri?: string;
  /** Cal.com: id event type-a za kreiranje booking-a. */
  eventTypeId?: number;
}

export interface AppointmentPayload {
  id: string;
  title: string;
  description: string;
  start: number;
  end: number;
  timezone: string;
  customerName: string;
  customerEmail: string | null;
  customerPhone: string | null;
  location: string;
}

export interface ExternalBooking {
  externalId: string;
  start: number;
  end: number;
  title: string;
  customerName: string;
  /** Za reschedule kod Cal.com-a: id prethodnog booking-a. */
  previousExternalId?: string;
}

export type WebhookEvent =
  | { kind: "created"; eventId: string; accountId: string; booking: ExternalBooking }
  | { kind: "canceled"; eventId: string; accountId: string; externalId: string }
  | { kind: "rescheduled"; eventId: string; accountId: string; booking: ExternalBooking }
  | { kind: "changed"; eventId: string; accountId: string }
  | { kind: "ignored"; eventId: string };

export interface WebhookRequest {
  headers: Record<string, string | string[] | undefined>;
  rawBody: Buffer;
}

export interface AuthorizationResult {
  tokens: TokenSet;
  accountId: string;
  accountLabel: string;
  calendarId?: string;
  eventTypeUri?: string;
  eventTypeId?: number;
}

export class WebhookSignatureError extends Error {
  constructor(message = "Invalid webhook signature") {
    super(message);
    this.name = "WebhookSignatureError";
  }
}

/** Zajednički interfejs svih spoljnih kalendara. */
export interface CalendarProvider {
  readonly id: Provider;
  /** Da li su na serveru podešeni client id, tajna i javna adresa funkcija. */
  isConfigured(): boolean;
  authorizationUrl(state: string, redirectUri: string, codeChallenge: string): string;
  exchangeCode(code: string, redirectUri: string, codeVerifier: string): Promise<AuthorizationResult>;
  refreshTokens(tokens: TokenSet): Promise<TokenSet>;
  getBusy(connection: Connection, from: number, to: number): Promise<BusyRange[]>;
  createEvent(connection: Connection, appointment: AppointmentPayload): Promise<string | null>;
  updateEvent(connection: Connection, externalId: string, appointment: AppointmentPayload): Promise<string | null>;
  deleteEvent(connection: Connection, externalId: string): Promise<void>;
  parseWebhook(request: WebhookRequest): WebhookEvent;
  /** Posle povezivanja: pretplata na webhookove / kanal obaveštenja. Vraća id pretplate. */
  subscribe(connection: Connection, webhookUrl: string): Promise<string | null>;
  unsubscribe(connection: Connection, subscriptionId: string): Promise<void>;
}

export class ProviderHttpError extends Error {
  constructor(
    readonly status: number,
    readonly body: string,
  ) {
    super(`Provider HTTP ${status}: ${body.slice(0, 300)}`);
    this.name = "ProviderHttpError";
  }
}

export async function httpJson<T>(url: string, init: RequestInit & { timeoutMs?: number } = {}): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), init.timeoutMs ?? 8000);
  try {
    const response = await fetch(url, { ...init, signal: controller.signal });
    const text = await response.text();
    if (!response.ok) throw new ProviderHttpError(response.status, text);
    return (text.length > 0 ? JSON.parse(text) : {}) as T;
  } finally {
    clearTimeout(timer);
  }
}

export function header(request: WebhookRequest, name: string): string {
  const value = request.headers[name.toLowerCase()];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}
