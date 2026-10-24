// Demo režim jednom komandom: build funkcija → Firebase Emulator Suite (projekat demo-termin)
// → seed podataka. Emulatori rade dok ne pritisneš Ctrl+C.
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname } from "node:path";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const isWindows = process.platform === "win32";

function run(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd: root, stdio: "inherit", shell: isWindows, ...options });
    child.on("exit", (code) => (code === 0 ? resolve() : reject(new Error(`${command} ${args.join(" ")} → ${code}`))));
    child.on("error", reject);
  });
}

async function waitForHub(timeoutMs = 180_000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    try {
      const response = await fetch("http://127.0.0.1:4400/emulators");
      if (response.ok) {
        const emulators = await response.json();
        if (["firestore", "auth", "functions", "storage"].every((name) => name in emulators)) return;
      }
    } catch {
      // emulatori se još pokreću
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error("Emulatori se nisu pokrenuli na vreme.");
}

async function main() {
  await run("npm", ["run", "build"]);
  await run("node", ["scripts/prepare-demo.mjs"]);

  const emulators = spawn("npx", ["firebase", "emulators:start", "--project", "demo-termin"], {
    cwd: root,
    stdio: "inherit",
    shell: isWindows,
  });
  const stop = () => emulators.kill("SIGINT");
  process.on("SIGINT", stop);
  process.on("SIGTERM", stop);
  emulators.on("exit", (code) => process.exit(code ?? 0));

  await waitForHub();
  await run("npm", ["run", "seed"]);
  process.stdout.write(
    [
      "",
      "────────────────────────────────────────────────────────────",
      " Demo je spreman.",
      "   Emulator UI:   http://127.0.0.1:4000",
      "   Android:       cd android && ./gradlew installDebug",
      "   (Android emulator pristupa računaru preko 10.0.2.2)",
      " Ctrl+C zaustavlja emulatore.",
      "────────────────────────────────────────────────────────────",
      "",
    ].join("\n"),
  );
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`);
  process.exit(1);
});
