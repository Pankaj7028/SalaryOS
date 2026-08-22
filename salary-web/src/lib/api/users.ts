import type { Role } from "@/lib/auth/roles";
import { apiFetch } from "./client";

/** P8.1's backend, given a screen at P8's QA pass. HR_ADMIN-only, both to read and to write
 * (CLAUDE.md §7's "Manage users & roles" row). */
export type User = {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  status: "ACTIVE" | "INACTIVE";
  createdAt: string;
};

export type CreateUserInput = {
  email: string;
  fullName: string;
  role: Role;
};

export type UpdateUserInput = {
  fullName: string;
  role: Role;
  status: "ACTIVE" | "INACTIVE";
};

export type ResetToken = {
  token: string;
  expiresAt: string;
};

export async function fetchUsers(): Promise<User[]> {
  const response = await apiFetch("/api/admin/users");
  return (await response.json()) as User[];
}

export async function createUser(input: CreateUserInput): Promise<User> {
  const response = await apiFetch("/api/admin/users", { method: "POST", body: JSON.stringify(input) });
  return (await response.json()) as User;
}

export async function updateUser(id: string, input: UpdateUserInput): Promise<User> {
  const response = await apiFetch(`/api/admin/users/${id}`, { method: "PATCH", body: JSON.stringify(input) });
  return (await response.json()) as User;
}

/** FR-1.6: single-use, 30-minute, shown exactly once — the caller must display it now, there is
 * no way to fetch it again. */
export async function issueResetToken(id: string): Promise<ResetToken> {
  const response = await apiFetch(`/api/admin/users/${id}/reset-token`, { method: "POST" });
  return (await response.json()) as ResetToken;
}
