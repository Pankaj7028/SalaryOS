import { cookies } from "next/headers";
import type { Role } from "./roles";

export type CurrentUser = {
  name: string;
  email: string;
  role: Role;
};

/**
 * This module runs on the server, so it needs an ABSOLUTE backend origin — the relative `/api/...`
 * the browser uses in production has no host to resolve against here, and routing a server-side
 * call back through our own rewrite would be a pointless round trip through Vercel's edge.
 *
 * `API_ORIGIN` is the server-only variable set in production (same value the rewrite in
 * `next.config.ts` proxies to). Locally it is unset and this falls back to the public base URL,
 * so development is unchanged.
 */
const API_BASE_URL =
  process.env.API_ORIGIN ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/**
 * Server-side identity read: forwards the incoming `sos_session` cookie to
 * `GET /api/auth/me` — never a token read in the browser (CLAUDE.md §4.4).
 *
 * Returns `null` when there is no session cookie or the backend rejects it.
 * `proxy.ts` already redirects unauthenticated requests away from every
 * route that renders `AppShell`, so callers there should never actually see
 * `null` — but they still check, since a cookie can expire between the
 * proxy's check and this fetch.
 */
export async function getCurrentUser(): Promise<CurrentUser | null> {
  const cookieStore = await cookies();
  const sessionCookie = cookieStore.get("sos_session");
  if (!sessionCookie) {
    return null;
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
    headers: { Cookie: `sos_session=${sessionCookie.value}` },
    cache: "no-store",
  });
  if (!response.ok) {
    return null;
  }

  const data = (await response.json()) as { fullName: string; email: string; role: Role };
  return { name: data.fullName, email: data.email, role: data.role };
}
