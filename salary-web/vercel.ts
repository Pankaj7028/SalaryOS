import type { VercelConfig } from "@vercel/config/v1";

/**
 * Vercel project configuration for salary-web.
 *
 * `vercel.ts` supersedes `vercel.json` — same settings, but typed and able to read the environment.
 *
 * **The `/api/*` proxy is NOT configured here**, deliberately. It lives in `next.config.ts` as a
 * Next.js rewrite instead, for two reasons: `next dev` and `next start` honour it, so the
 * production request path can be exercised locally (and was, before this was committed); and it
 * survives a move off Vercel, which a platform-level rewrite would not. See that file for why the
 * proxy is what keeps cookie authentication working across two hosts.
 */
export const config: VercelConfig = {
  framework: "nextjs",

  // Portland — the same region as the Render service in render.yaml. Every API call is proxied
  // through this function to the backend, so putting the two on opposite coasts would add a
  // round trip to each one.
  regions: ["pdx1"],

  headers: [
    {
      source: "/(.*)",
      headers: [
        { key: "X-Content-Type-Options", value: "nosniff" },
        // This app has no embedding use case, and salary data is the last thing that should be
        // framed by someone else's page.
        { key: "X-Frame-Options", value: "DENY" },
        { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
        {
          key: "Strict-Transport-Security",
          value: "max-age=63072000; includeSubDomains; preload",
        },
        {
          key: "Permissions-Policy",
          value: "camera=(), microphone=(), geolocation=(), payment=()",
        },
      ],
    },
  ],
};

export default config;
