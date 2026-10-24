import { beforeAll, describe, expect, it } from "vitest";
import { createHmac, randomBytes } from "node:crypto";
import { CalendlyProvider, verifyCalendlySignature } from "../../src/integrations/calendly";
import { CalcomProvider, verifyCalcomSignature } from "../../src/integrations/calcom";
import { GoogleCalendarProvider, channelToken } from "../../src/integrations/google";
import { decrypt, encrypt, pkceChallenge } from "../../src/integrations/crypto";
import { WebhookSignatureError } from "../../src/integrations/provider";
import { dropOwnEchoes } from "../../src/booking/availabilityService";

const CALENDLY_KEY = "calendly-signing-key-test";
const CALCOM_SECRET = "calcom-webhook-secret-test";

beforeAll(() => {
  process.env.CALENDLY_WEBHOOK_SIGNING_KEY = CALENDLY_KEY;
  process.env.CALCOM_WEBHOOK_SECRET = CALCOM_SECRET;
});

function calendlyRequest(body: unknown, tSec: number, key = CALENDLY_KEY) {
  const raw = JSON.stringify(body);
  const v1 = createHmac("sha256", key).update(`${tSec}.${raw}`).digest("hex");
  return { headers: { "calendly-webhook-signature": `t=${tSec},v1=${v1}` }, rawBody: Buffer.from(raw) };
}

const invitee = {
  event: "invitee.created",
  created_at: "2026-11-01T10:00:00.000000Z",
  payload: {
    uri: "https://api.calendly.com/scheduled_events/EV1/invitees/INV1",
    name: "Marko Marković",
    email: "marko@example.com",
    scheduled_event: {
      uri: "https://api.calendly.com/scheduled_events/EV1",
      name: "Šišanje",
      start_time: "2026-11-10T09:00:00.000000Z",
      end_time: "2026-11-10T09:30:00.000000Z",
      event_memberships: [{ user: "https://api.calendly.com/users/U1" }],
    },
  },
};

describe("Calendly webhook", () => {
  const now = Date.parse("2026-11-01T10:00:30Z");

  it("prihvata ispravan potpis i čita invitee.created", () => {
    const event = new CalendlyProvider().parseWebhook(calendlyRequest(invitee, Math.floor(now / 1000)), now);
    expect(event).toMatchObject({
      kind: "created",
      accountId: "https://api.calendly.com/users/U1",
      booking: {
        externalId: "https://api.calendly.com/scheduled_events/EV1",
        start: Date.parse("2026-11-10T09:00:00Z"),
        end: Date.parse("2026-11-10T09:30:00Z"),
        customerName: "Marko Marković",
      },
    });
  });

  it("invitee.canceled postaje otkazivanje", () => {
    const body = { ...invitee, event: "invitee.canceled" };
    const event = new CalendlyProvider().parseWebhook(calendlyRequest(body, Math.floor(now / 1000)), now);
    expect(event).toMatchObject({ kind: "canceled", externalId: "https://api.calendly.com/scheduled_events/EV1" });
  });

  it("odbija pogrešan potpis, pogrešan ključ i stari vremenski žig", () => {
    const provider = new CalendlyProvider();
    expect(() => provider.parseWebhook(calendlyRequest(invitee, Math.floor(now / 1000), "wrong"), now)).toThrow(WebhookSignatureError);
    expect(() => provider.parseWebhook(calendlyRequest(invitee, Math.floor(now / 1000) - 600), now)).toThrow(WebhookSignatureError);
    const tampered = calendlyRequest(invitee, Math.floor(now / 1000));
    tampered.rawBody = Buffer.from(tampered.rawBody.toString().replace("Marko", "Petar"));
    expect(() => provider.parseWebhook(tampered, now)).toThrow(WebhookSignatureError);
  });

  it("verifyCalendlySignature bez ključa vraća false", () => {
    expect(verifyCalendlySignature("t=1,v1=abc", "{}", "", 1000)).toBe(false);
  });
});

describe("Cal.com webhook", () => {
  function calcomRequest(body: unknown, secret = CALCOM_SECRET) {
    const raw = Buffer.from(JSON.stringify(body));
    return { headers: { "x-cal-signature-256": createHmac("sha256", secret).update(raw).digest("hex") }, rawBody: raw };
  }
  const payload = {
    uid: "booking-1",
    title: "Feniranje",
    startTime: "2026-11-10T09:00:00Z",
    endTime: "2026-11-10T09:45:00Z",
    organizer: { id: 42, email: "frizer@example.com" },
    attendees: [{ name: "Jelena" }],
  };

  it("BOOKING_CREATED, BOOKING_CANCELLED i BOOKING_RESCHEDULED", () => {
    const provider = new CalcomProvider();
    const created = provider.parseWebhook(calcomRequest({ triggerEvent: "BOOKING_CREATED", createdAt: "x", payload }));
    expect(created).toMatchObject({ kind: "created", accountId: "42", booking: { externalId: "booking-1", customerName: "Jelena" } });
    const canceled = provider.parseWebhook(calcomRequest({ triggerEvent: "BOOKING_CANCELLED", createdAt: "y", payload }));
    expect(canceled).toMatchObject({ kind: "canceled", externalId: "booking-1" });
    const moved = provider.parseWebhook(
      calcomRequest({ triggerEvent: "BOOKING_RESCHEDULED", createdAt: "z", payload: { ...payload, uid: "booking-2", rescheduleUid: "booking-1" } }),
    );
    expect(moved).toMatchObject({ kind: "rescheduled", booking: { externalId: "booking-2", previousExternalId: "booking-1" } });
  });

  it("različiti događaji imaju različit id (idempotentnost po događaju)", () => {
    const provider = new CalcomProvider();
    const a = provider.parseWebhook(calcomRequest({ triggerEvent: "BOOKING_CREATED", createdAt: "1", payload }));
    const b = provider.parseWebhook(calcomRequest({ triggerEvent: "BOOKING_CANCELLED", createdAt: "2", payload }));
    expect(a.eventId).not.toBe(b.eventId);
  });

  it("odbija pogrešan potpis", () => {
    const provider = new CalcomProvider();
    expect(() => provider.parseWebhook(calcomRequest({ triggerEvent: "BOOKING_CREATED", payload }, "wrong"))).toThrow(WebhookSignatureError);
    expect(verifyCalcomSignature("", "{}", CALCOM_SECRET)).toBe(false);
  });
});

describe("Google Calendar push kanal", () => {
  const secret = "channel-secret";
  const provider = new GoogleCalendarProvider(() => secret);

  it("prihvata kanal sa ispravnim tokenom i ignoriše sync poruku", () => {
    const headers = {
      "x-goog-channel-id": "termin-abc",
      "x-goog-channel-token": channelToken("termin-abc", secret),
      "x-goog-message-number": "2",
      "x-goog-resource-state": "exists",
    };
    expect(provider.parseWebhook({ headers, rawBody: Buffer.alloc(0) })).toEqual({
      kind: "changed",
      eventId: "termin-abc:2",
      accountId: "termin-abc",
    });
    expect(
      provider.parseWebhook({ headers: { ...headers, "x-goog-resource-state": "sync" }, rawBody: Buffer.alloc(0) }).kind,
    ).toBe("ignored");
  });

  it("odbija lažan token", () => {
    const headers = { "x-goog-channel-id": "termin-abc", "x-goog-channel-token": "nope" };
    expect(() => provider.parseWebhook({ headers, rawBody: Buffer.alloc(0) })).toThrow(WebhookSignatureError);
  });
});

describe("šifrovanje tokena", () => {
  it("AES-256-GCM: dešifruje isto, a izmenjen šifrat odbija", () => {
    const key = randomBytes(32).toString("base64");
    const secret = JSON.stringify({ accessToken: "a", refreshToken: "r", expiresAt: 1 });
    const enc = encrypt(secret, key);
    expect(enc).not.toContain("accessToken");
    expect(decrypt(enc, key)).toBe(secret);
    const bad = Buffer.from(enc, "base64");
    bad[bad.length - 1] ^= 1;
    expect(() => decrypt(bad.toString("base64"), key)).toThrow();
  });

  it("odbija ključ pogrešne dužine", () => {
    expect(() => encrypt("x", Buffer.alloc(16).toString("base64"))).toThrow();
  });

  it("PKCE S256 prema RFC 7636 primeru", () => {
    expect(pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")).toBe("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
  });
});

describe("spoljni kalendar ne duplira naše termine", () => {
  it("događaj koji se poklapa sa našim terminom se izbacuje", () => {
    const own = [{ id: "a", start: 1_000_000, end: 2_800_000, kind: "appt" as const }];
    const ext = [
      { id: "e1", start: 1_000_000, end: 2_800_000, kind: "external" as const },
      { id: "e2", start: 5_000_000, end: 6_000_000, kind: "external" as const },
    ];
    expect(dropOwnEchoes(ext, own).map((e) => e.id)).toEqual(["e2"]);
  });
});
