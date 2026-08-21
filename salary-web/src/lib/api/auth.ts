import type { Role } from "@/lib/auth/roles";
import { apiFetch } from "./client";

/**
 * Data fetchers for the auth domain. Know nothing about React or TanStack
 * Query (CLAUDE.md §9) — `src/lib/auth/auth-queries.ts` is the sibling that
 * wraps these as hooks.
 */

export type CurrentUserResponse = {
  id: string;
  fullName: string;
  email: string;
  role: Role;
  themePreference: string;
};

export async function login(email: string, password: string): Promise<void> {
  await apiFetch("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export async function logout(): Promise<void> {
  await apiFetch("/api/auth/logout", { method: "POST" });
}

export async function fetchCurrentUser(): Promise<CurrentUserResponse> {
  const response = await apiFetch("/api/auth/me");
  return (await response.json()) as CurrentUserResponse;
}
