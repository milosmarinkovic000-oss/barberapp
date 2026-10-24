import { HttpsError } from "firebase-functions/https";
import type { FunctionsErrorCode } from "firebase-functions/https";

/** Kodovi grešaka koje aplikacija prevodi u poruke na srpskom. */
export type AppErrorCode = "SLOT_TAKEN" | "POLICY_VIOLATION" | "NOT_ALLOWED" | "VALIDATION" | "NOT_FOUND" | "RATE_LIMITED" | "INTEGRATION_NOT_CONFIGURED";

const HTTPS_CODE: Record<AppErrorCode, FunctionsErrorCode> = {
  SLOT_TAKEN: "aborted",
  POLICY_VIOLATION: "failed-precondition",
  NOT_ALLOWED: "permission-denied",
  VALIDATION: "invalid-argument",
  NOT_FOUND: "not-found",
  RATE_LIMITED: "resource-exhausted",
  INTEGRATION_NOT_CONFIGURED: "failed-precondition",
};

export class AppError extends Error {
  constructor(
    readonly code: AppErrorCode,
    message: string,
    readonly details: Record<string, unknown> = {},
  ) {
    super(message);
    this.name = "AppError";
  }
}

export function toHttpsError(error: unknown): HttpsError {
  if (error instanceof HttpsError) return error;
  if (error instanceof AppError) {
    return new HttpsError(HTTPS_CODE[error.code], error.message, { code: error.code, ...error.details });
  }
  return new HttpsError("internal", "Unexpected server error", { code: "INTERNAL" });
}

export const notAllowed = (message = "Not allowed") => new AppError("NOT_ALLOWED", message);
export const notFound = (what: string) => new AppError("NOT_FOUND", `${what} not found`);
