// Priprema za demo režim: tajne za Functions emulator označene kao nepodešene
// ("not-configured", vidi functions/src/common/config.ts). Integracije su u demo režimu
// namerno isključene, a emulator tako ne pokušava da čita Google Secret Manager.
import { existsSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const file = join(root, "functions", ".secret.local");
const secrets = [
  "GOOGLE_OAUTH_CLIENT_SECRET",
  "CALENDLY_CLIENT_SECRET",
  "CALENDLY_WEBHOOK_SIGNING_KEY",
  "CALCOM_CLIENT_SECRET",
  "CALCOM_WEBHOOK_SECRET",
  "TOKEN_ENCRYPTION_KEY",
];
if (!existsSync(file)) {
  writeFileSync(file, `${secrets.map((s) => `${s}=not-configured`).join("\n")}\n`);
  process.stdout.write(`Napravljen ${file} (nepodešene tajne za emulator)\n`);
}
