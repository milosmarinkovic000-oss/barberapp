#!/usr/bin/env node
// Pravi hosting/public/.well-known/assetlinks.json za proveru App Links (https://<projekat>.web.app/s/...).
// Upotreba: node scripts/assetlinks.mjs <SHA-256 otisak ključa za potpis> [paket]
// Otisak: Play Console → Integritet aplikacije → Potpisivanje aplikacije (ili `keytool -list -v -keystore ...`).
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const [fingerprint, pkg = "rs.termin.app"] = process.argv.slice(2);
if (!fingerprint || !/^([0-9A-F]{2}:){31}[0-9A-F]{2}$/i.test(fingerprint)) {
  console.error("Upotreba: node scripts/assetlinks.mjs AA:BB:...:FF (32 bajta, SHA-256) [paket]");
  process.exit(1);
}
const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const target = join(root, "hosting", "public", ".well-known", "assetlinks.json");
const content = [
  {
    relation: ["delegate_permission/common.handle_all_urls"],
    target: { namespace: "android_app", package_name: pkg, sha256_cert_fingerprints: [fingerprint.toUpperCase()] },
  },
];
mkdirSync(dirname(target), { recursive: true });
writeFileSync(target, JSON.stringify(content, null, 2) + "\n");
console.log(`Upisano: ${target}`);
