import { defineSecret, defineString } from "firebase-functions/params";

/** Region svih funkcija (najbliži Srbiji sa punom podrškom za 2. generaciju). */
export const REGION = "europe-west1";

export const isEmulator = process.env.FUNCTIONS_EMULATOR === "true";

/**
 * App Check se zahteva u produkciji (Play Integrity). Emulator ne izdaje App Check tokene,
 * pa se tamo ne proverava.
 */
export const enforceAppCheck = !isEmulator;

/** Javna adresa HTTP funkcija (za OAuth redirect i webhookove), npr. https://europe-west1-PROJEKAT.cloudfunctions.net */
export const publicFunctionsUrl = defineString("PUBLIC_FUNCTIONS_URL", { default: "" });

/** Šema za povratak u aplikaciju posle OAuth-a. */
export const appDeepLinkBase = defineString("APP_DEEP_LINK_BASE", { default: "termin://integrations" });

export const googleClientId = defineString("GOOGLE_OAUTH_CLIENT_ID", { default: "" });
export const calendlyClientId = defineString("CALENDLY_CLIENT_ID", { default: "" });
export const calcomClientId = defineString("CALCOM_CLIENT_ID", { default: "" });

/** Tajne: postoje samo na serveru (Firebase secrets / Secret Manager). */
export const googleClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");
export const calendlyClientSecret = defineSecret("CALENDLY_CLIENT_SECRET");
export const calendlyWebhookSigningKey = defineSecret("CALENDLY_WEBHOOK_SIGNING_KEY");
export const calcomClientSecret = defineSecret("CALCOM_CLIENT_SECRET");
export const calcomWebhookSecret = defineSecret("CALCOM_WEBHOOK_SECRET");
/** 32 bajta (base64) za AES-256-GCM šifrovanje OAuth tokena. */
export const tokenEncryptionKey = defineSecret("TOKEN_ENCRYPTION_KEY");

export const integrationSecrets = [
  googleClientSecret,
  calendlyClientSecret,
  calendlyWebhookSigningKey,
  calcomClientSecret,
  calcomWebhookSecret,
  tokenEncryptionKey,
];

/**
 * Vrednost kojom demo režim (functions/.secret.local) označava nepodešenu tajnu. Emulator
 * prazne vrednosti tretira kao nepostojeće i tada pokušava Secret Manager, zato koristimo ovu.
 */
export const UNSET_SECRET = "not-configured";

/** Bezbedno čitanje tajne: nepodešena tajna je prazan string. */
export function secretValue(secret: { value(): string }): string {
  try {
    const value = secret.value() ?? "";
    return value === UNSET_SECRET ? "" : value;
  } catch {
    return "";
  }
}
