import type { CallableRequest } from "firebase-functions/https";
import { HttpsError } from "firebase-functions/https";
import { notAllowed } from "./errors";

export interface Caller {
  uid: string;
  roles: string[];
  salonId: string | null;
  stylistId: string | null;
  phone: string | null;
  email: string | null;
  displayName: string | null;
}

export function requireCaller(request: CallableRequest<unknown>): Caller {
  const token = request.auth?.token;
  if (!request.auth || !token) {
    throw new HttpsError("unauthenticated", "Sign in required", { code: "NOT_ALLOWED" });
  }
  const roles = Array.isArray(token.roles) ? token.roles.filter((r): r is string => typeof r === "string") : [];
  return {
    uid: request.auth.uid,
    roles,
    salonId: typeof token.salonId === "string" ? token.salonId : null,
    stylistId: typeof token.stylistId === "string" ? token.stylistId : null,
    phone: typeof token.phone_number === "string" ? token.phone_number : null,
    email: typeof token.email === "string" ? token.email : null,
    displayName: typeof token.name === "string" ? token.name : null,
  };
}

export function isOwnerOf(caller: Caller, salonId: string): boolean {
  return caller.roles.includes("owner") && caller.salonId === salonId;
}

export function isStaffOf(caller: Caller, salonId: string): boolean {
  return (caller.roles.includes("owner") || caller.roles.includes("stylist")) && caller.salonId === salonId;
}

/** Vlasnik upravlja svim frizerima salona; frizer samo sobom. */
export function canManageStylist(caller: Caller, salonId: string, stylistId: string): boolean {
  if (isOwnerOf(caller, salonId)) return true;
  return caller.roles.includes("stylist") && caller.salonId === salonId && caller.stylistId === stylistId;
}

export function assertOwner(caller: Caller, salonId: string): void {
  if (!isOwnerOf(caller, salonId)) throw notAllowed("Only the salon owner can do this");
}

export function assertStaff(caller: Caller, salonId: string): void {
  if (!isStaffOf(caller, salonId)) throw notAllowed("Only salon staff can do this");
}

export function assertCanManageStylist(caller: Caller, salonId: string, stylistId: string): void {
  if (!canManageStylist(caller, salonId, stylistId)) throw notAllowed("Not allowed for this stylist");
}
