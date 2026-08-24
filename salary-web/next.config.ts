import type { NextConfig } from "next";

/**
 * The backend origin, as seen from the Vercel server (not from the browser). Absolute, no trailing
 * slash — e.g. `https://salary-service.onrender.com`.
 */
const apiOrigin = process.env.API_ORIGIN?.replace(/\/$/, "");

const nextConfig: NextConfig = {
  /**
   * Proxy every `/api/*` request through to salary-service, so the browser only ever talks to one
   * origin.
   *
   * **This is what keeps authentication working in production, and it is not optional.** The
   * session, refresh and CSRF cookies are all issued `SameSite=Lax` (AuthController). Lax cookies
   * are not sent on cross-site subresource requests — so with the app on `*.vercel.app` calling an
   * API on `*.onrender.com` directly, the browser would withhold `sos_session` on every fetch and
   * every request would 401. Nothing would look broken in the network tab except the missing
   * cookie header.
   *
   * Routing through here makes those requests same-origin from the browser's point of view: it
   * sends the cookies, the backend's `Set-Cookie` comes back through the proxy and is stored
   * against the Vercel host, and the `sos_refresh` cookie's `Path=/api/auth` still lines up. It
   * also removes the need for CORS entirely, which is why `APP_CORS_ORIGINS` is empty in the
   * production profile.
   *
   * The alternative — reissuing the cookies as `SameSite=None` — would work too, but it widens the
   * cookie's exposure to any cross-site context for a deployment topology the proxy already solves.
   *
   * When `API_ORIGIN` is unset (local development) no rewrite is registered: the browser talks to
   * `http://localhost:8080` directly via `NEXT_PUBLIC_API_BASE_URL`, which is same-site over
   * localhost and works as it always has.
   */
  async rewrites() {
    if (!apiOrigin) {
      return [];
    }
    return [
      {
        source: "/api/:path*",
        destination: `${apiOrigin}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
