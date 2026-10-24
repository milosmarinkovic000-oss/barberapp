import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    include: ["test/**/*.test.ts"],
    testTimeout: 30000,
    hookTimeout: 30000,
    fileParallelism: false,
    // Testovi rade samo nad emulatorima: bez traženja GCP metadata servera (i upozorenja o tome).
    env: { METADATA_SERVER_DETECTION: "none" },
  },
});
