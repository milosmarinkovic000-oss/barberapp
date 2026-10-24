import { secretValue, tokenEncryptionKey } from "../common/config";
import type { Provider } from "../domain/types";
import { CalcomProvider } from "./calcom";
import { CalendlyProvider } from "./calendly";
import { GoogleCalendarProvider } from "./google";
import type { CalendarProvider } from "./provider";

const providers: Record<Provider, CalendarProvider> = {
  google: new GoogleCalendarProvider(() => secretValue(tokenEncryptionKey)),
  calendly: new CalendlyProvider(),
  calcom: new CalcomProvider(),
};

export function providerFor(id: Provider): CalendarProvider {
  return providers[id];
}

/** Integracija radi samo ako je provajder podešen i postoji ključ za šifrovanje tokena. */
export function isProviderReady(id: Provider): boolean {
  return providers[id].isConfigured() && secretValue(tokenEncryptionKey).length > 0;
}
