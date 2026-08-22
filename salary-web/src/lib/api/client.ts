import { failure } from "@/lib/notify";

/**
 * The one seam every request to salary-service crosses. Callers never call
 * `fetch` directly against the API — that is how CSRF headers, credentials,
 * and 401/403/network handling drift apart between features.
 */

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

export type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
};

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null, options?: ErrorOptions) {
    super(problem?.detail ?? `Request failed with status ${status}`, options);
    this.status = status;
    this.problem = problem;
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Fetches a path under the API's base URL, always cookie-authenticated
 * (`credentials: "include"`), always echoing the readable `sos_csrf` cookie
 * back as `X-CSRF-Token` on mutating requests (CLAUDE.md §4.1's double-submit
 * pattern — never a token read for auth, only for this echo).
 *
 * 401 sends the browser to sign-in (the session is gone; nothing else to do
 * with it). 403 and network failures are reported once, here, via the one
 * toast host (`notify.ts`) — a feature calling this never re-reports either.
 * A caller that wants to react beyond that (a field error from 400, a
 * specific 404) still gets the thrown `ApiError` to catch.
 */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);

  if (MUTATING_METHODS.has(method)) {
    const csrfToken = readCookie("sos_csrf");
    if (csrfToken) {
      headers.set("X-CSRF-Token", csrfToken);
    }
  }
  // A FormData body (a CSV upload) must NOT get an explicit Content-Type here -- the browser
  // sets multipart/form-data with the correct boundary itself only when the header is absent.
  if (init.body !== undefined && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers, credentials: "include" });
  } catch (cause) {
    const networkError = new ApiError(0, null, { cause });
    failure(networkError, "Connection problem");
    throw networkError;
  }

  if (response.status === 401 && typeof window !== "undefined" && !path.startsWith("/api/auth/login")) {
    const redirectTarget = `${window.location.pathname}${window.location.search}`;
    // A hard navigation, not router.push: the session just died, so every
    // client-side query cache and component state built on "signed in" needs
    // to go with it, not just the URL.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.href = `/sign-in?redirect=${encodeURIComponent(redirectTarget)}`;
  }

  if (!response.ok) {
    let problem: ProblemDetail | null = null;
    try {
      problem = (await response.json()) as ProblemDetail;
    }
    catch {
      // No JSON body — leave problem null.
    }
    const apiError = new ApiError(response.status, problem);
    if (response.status === 403) {
      failure(apiError, "Access denied");
    }
    throw apiError;
  }

  return response;
}
