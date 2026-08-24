# Deploying Salary OS

Three hosts, because the stack needs three things and no one platform does all of them well:

| Piece | Host | Why there |
|---|---|---|
| `salary-web` (Next.js 16) | **Vercel** | Native target |
| `salary-service` (Spring Boot 4.0.8) | **Render** (Docker) | Vercel has no JVM runtime — see below |
| PostgreSQL 17 | **Neon** | The pinned database (CLAUDE.md §3); provisioned through the Vercel Marketplace |

> **Vercel cannot host `salary-service`.** Vercel is a full compute platform — it runs Node, Python,
> Go, Bun and Rust natively — but there is no JVM runtime. Beyond the language, this service holds a
> Hikari connection pool, runs Flyway migrations at startup, and carries a `@Scheduled` job that
> applies due compensation changes daily at 02:00 UTC. None of that fits a function invocation
> model. It needs a container that stays up, which is what Render provides.

Everything in this repository is ready to deploy. What remains is authenticating to three services,
which only you can do.

---

## What is already done

| | |
|---|---|
| `salary-service/Dockerfile` | Multi-stage build, Temurin 21 JRE, non-root user, container-aware heap. **Verified: image builds (570 MB), boots, validates 16 Flyway migrations, serves `/actuator/health`, and answers an authenticated request.** |
| `salary-service/src/main/resources/application-prod.yml` | Production profile. Notably overrides the `autoconfigure.exclude` block from P0.2 — without that override the app starts cleanly with **no persistence and no Flyway**, which is the exact silent failure `application.yml` warns about. |
| `render.yaml` | Render blueprint: Docker service, health check, every secret marked `sync: false`. |
| `salary-web/next.config.ts` | The `/api/*` proxy. **This is what keeps authentication working** — see "The cookie problem" below. |
| `salary-web/vercel.ts` | Typed project config (supersedes `vercel.json`): framework, region, security headers. |
| `salary-web/src/lib/auth/current-user.ts` | Now prefers the server-only `API_ORIGIN`, since server-side code cannot resolve a relative URL. |
| `salary-web/.env.example` | Documents both variables and what each is set to in production. |

Verified before commit: backend `206/206`, frontend `45/45`, production build clean, and the whole
authenticated request path exercised through the proxy locally (login, session cookie, authenticated
read, CSRF-protected write, and a CSRF-less write correctly refused).

---

## The cookie problem, and why `next.config.ts` has a rewrite

`AuthController` issues `sos_session`, `sos_refresh` and `sos_csrf` as `SameSite=Lax`.

**Lax cookies are not sent on cross-site subresource requests.** With the app on `*.vercel.app`
calling an API on `*.onrender.com` directly, the browser would silently withhold `sos_session` on
every `fetch`, and every request would come back 401. Nothing would look broken except a missing
header — no CORS error, no exception, just a signed-in user who appears signed out.

The fix is the rewrite in `salary-web/next.config.ts`: the browser only ever talks to the Vercel
origin, and `/api/*` is proxied to Render server-side. Requests become same-origin, the cookies are
sent, `Set-Cookie` comes back through the proxy and binds to the Vercel host, and `sos_refresh`'s
`Path=/api/auth` still lines up.

**The proxy does not remove the need for CORS, and assuming it did cost a broken first sign-in.** A
Next.js rewrite forwards the incoming `Origin` header, so the service still sees
`Origin: https://<app>.vercel.app` and still runs its CORS check against it. With
`APP_CORS_ORIGINS` empty that check fails and every mutating request — including login — comes back
`403 Invalid CORS request`. Set `APP_CORS_ORIGINS` to the exact Vercel origin.

The two mechanisms are independent and both are required: the proxy is what makes the browser *send*
the cookie, and `APP_CORS_ORIGINS` is what makes the service *accept* the request it arrives on.

This was tested end-to-end locally, not assumed. The alternative — reissuing the cookies as
`SameSite=None` — also works, but widens their exposure to every cross-site context to solve a
problem the proxy already solves.

> **Note:** `CLAUDE.md` §10 lists `APP_COOKIE_DOMAIN` and `APP_COOKIE_SAMESITE` as configuration
> keys. **They are not implemented** — the cookie attributes are hardcoded in `AuthController`. That
> does not block this deployment, but if you ever split the frontend and backend across two
> *registrable* domains without the proxy, that is the gap to close first.

---

## Provisioned already (2026-08-24)

| | |
|---|---|
| Vercel project | `pankajmandal7028-8091s-projects/salary-os`, linked from `salary-web/` |
| Neon database | `salary-os-db`, Free plan, region `pdx1` — pooled endpoint in `us-west-2`, database `neondb` (the exact host and credentials live in the Vercel project's environment variables, not in this repository) |
| Roles | `neondb_owner` (Flyway) and `salaryos_app` (the running service), created with a generated password |
| Schema | All 15 migrations applied to `salary_schema` (now at `v15`) by running the production Docker image against Neon |
| Admin user | `admin@acme.test`, `HR_ADMIN`, with a generated 20-character password — **not** the seed's `Password123!` |
| Vercel deployment | **https://salary-os.vercel.app** — live, with `API_ORIGIN` and an empty `NEXT_PUBLIC_API_BASE_URL` set across production, preview and development. |
| Render service | **https://salary-service-p209.onrender.com** — live. Note the suffix: `salary-service` was taken, so the URL is not what `render.yaml` implies. Whatever Render assigns is what `API_ORIGIN` must be. |

**Verified against the real Neon database, not assumed:** login succeeds with the generated password,
`/api/auth/me` returns the admin, the employee list answers (0 rows — the database is empty), and
`salaryos_app` gets `permission denied for table audit_events` on an `UPDATE`. That last one is the
proof that the owner/app role split worked: had Flyway run as the app role, the `REVOKE` would have
been a no-op and the tamper would have silently succeeded.

Two things worth knowing about what was provisioned:

- **Neon Auth was explicitly turned off** (`-m auth=false`). It defaults to *on* and would add a
  second identity provider with profiles synced into Postgres — directly against CLAUDE.md §4's
  "database-backed, no external identity provider".
- **Neon runs PostgreSQL 18.6**, while the stack pins 17 and the Testcontainers suite runs 17. Every
  feature the schema depends on (`btree_gist`, `daterange` exclusion constraints, `citext`) is stable
  across that gap, and all 16 migrations applied cleanly — but it is a version skew between what is
  tested and what runs, and it should be recorded rather than discovered later.

### The `salaryos_app` password

`V8` creates the role with a **hardcoded placeholder password** if it does not already exist — its own
comment says the real credential is "issued out-of-band and never lives in this file". On a public
repository that placeholder is a known password on a role with full CRUD over salary data, so the role
was created on Neon **before** migrations ran, with a generated 32-character password. `V8`'s block
saw it existed and no-opped, exactly as designed.

---

## Step 1 — Neon (the database)

The preferred path provisions Neon through the Vercel Marketplace, which creates the database and
injects the connection variables into the Vercel project automatically. Do step 2 first if you would
rather link the project before adding storage; the order does not matter much.

```bash
vercel login                      # you must run this — I cannot authenticate for you
cd salary-web && vercel link      # creates/links the Vercel project
vercel integration add neon       # provisions Neon, injects env vars
vercel env pull .env.vercel       # pull the values down so you can copy them to Render
```

If the CLI hands off to the browser to finish the handshake, complete it there and re-run
`vercel integration list` to confirm.

**Then create the two roles.** Flyway must *not* run as the application role:

> Postgres owners always retain full DML on tables they create, so `V8`'s `REVOKE` against the app
> role is a no-op if that role also owns the tables — the audit log silently stops being
> append-only, and nothing tells you.

- **Owner role** — Neon's project-default role. Flyway uses this. `V8` grants the app role what it
  needs.
- **App role** (`salaryos_app`) — what the running service uses.

Convert Neon's connection string to a JDBC URL. Neon shows a `postgresql://user:pass@host/db` URI;
that is **not** a JDBC URL. Use the **pooled** endpoint (the host containing `-pooler`):

```
jdbc:postgresql://ep-xxxx-pooler.REGION.aws.neon.tech/salaryos?sslmode=require
```

Credentials go in `DATABASE_USER` / `DATABASE_PASSWORD`, not inline in the URL.

---

## Step 2 — Render (the backend)

1. Push this branch to GitHub (Render deploys from a repository).
2. Render dashboard → **New → Blueprint** → point at this repo. It reads `render.yaml`.
3. Fill in the five secrets marked `sync: false`:

| Variable | Value |
|---|---|
| `APP_CORS_ORIGINS` | The exact Vercel origin, e.g. `https://salary-os.vercel.app` — no trailing slash |
| `DATABASE_URL` | The JDBC URL from step 1 (pooled endpoint, `sslmode=require`) |
| `DATABASE_USER` / `DATABASE_PASSWORD` | The **app** role |
| `FLYWAY_USER` / `FLYWAY_PASSWORD` | The **owner** role — different credentials, on purpose |

`APP_JWT_SIGNING_KEY` is set to `generateValue: true`, so Render generates it. If you set it
yourself, use `openssl rand -base64 48` — the production profile has no fallback, so a missing value
fails startup loudly rather than signing real tokens with the dev key committed in `application.yml`.

4. Deploy. Watch the logs for `Successfully validated N migrations` and `Started
   SalaryServiceApplication`. Health lives at `/actuator/health`, which the security config permits
   unauthenticated precisely so a platform probe needs no credential.
5. Note the service URL: `https://<name>.onrender.com`.

> **Free plan caveat:** the instance sleeps after ~15 minutes idle and cold-starts in roughly 50
> seconds — and this is a JVM, so it is at the slow end of that. The first request after a quiet
> period will look like a hang. Upgrade to a paid instance if that matters.

### Seeding

Seeding is deliberately **not** part of a deploy — `APP_SEED_FORCE` is `false` in `render.yaml`, so a
redeploy can never regenerate 10,000 employees over real data. To seed a fresh database, run the seed
profile once as a one-off job against that database:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
```

---

## Step 3 — Vercel (the frontend)

Set the **Root Directory** to `salary-web` in project settings — this is a monorepo and the Next.js
app is not at the repo root.

Environment variables (Production, Preview, Development):

| Variable | Value | Why |
|---|---|---|
| `API_ORIGIN` | `https://<name>.onrender.com` | Server-only. The rewrite destination and the server-side identity read. No trailing slash. |
| `NEXT_PUBLIC_API_BASE_URL` | *(empty string)* | Browser-facing. Empty makes every request relative, so it goes through the proxy. **Setting this to the Render URL is exactly the mistake that breaks auth** — the browser would then call Render cross-site and the Lax cookies would not be sent. |

```bash
cd salary-web
vercel env add API_ORIGIN production          # paste the Render URL
vercel env add NEXT_PUBLIC_API_BASE_URL production   # submit an empty value
vercel deploy --prod
```

---

## Step 4 — Verify the deployment

Run these against the live URL. The point of each is noted, because a green status code alone does
not prove the interesting part.

```bash
APP=https://<your-app>.vercel.app
API=https://<your-service>.onrender.com

# 0. The backend is up. Checked against Render directly, NOT through the app: the rewrite only
#    covers /api/*, and actuator lives at /actuator/*, so health is deliberately not reachable
#    from the public app at all. That is the right default — a health probe is for the platform.
curl -s -o /dev/null -w "%{http_code}\n" $API/actuator/health          # expect 200

# 1. The proxy reaches the backend. An unauthenticated call must come back as the service's own
#    RFC 7807 body — `{"detail":"Authentication required",...}`. A Vercel 404 or 502 here means
#    the rewrite is not wired; a 401 with that JSON means it is.
curl -s $APP/api/auth/me                                               # expect the ProblemDetail

# 2. Login sets all three cookies ON THE VERCEL ORIGIN — this is the SameSite fix working.
curl -s -c jar.txt -D - -o /dev/null -X POST $APP/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@acme.test","password":"Password123!"}' | grep -i set-cookie

# 3. An authenticated read: the cookie went back out again.
curl -s -b jar.txt $APP/api/auth/me

# 4. Real data through the whole chain: browser → Vercel → Render → Neon.
curl -s -b jar.txt "$APP/api/employees?limit=1"

# 5. CSRF is still enforced — this MUST return 403.
curl -s -o /dev/null -w "%{http_code}\n" -b jar.txt -X POST $APP/api/saved-views \
  -H 'Content-Type: application/json' -d '{"name":"x","route":"/employees","queryString":"","shared":false}'
```

Then open the app and sign in. If sign-in appears to succeed but every screen is empty, that is the
cookie problem: check that `NEXT_PUBLIC_API_BASE_URL` is **empty**, not the Render URL.

---

## Before the first real deploy

- [ ] **Change the seeded passwords.** Every seeded account uses `Password123!`. Fine for a demo,
      not for anything reachable on the internet.
- [ ] Run the suites — the Docker build deliberately skips tests, because Testcontainers needs a
      Docker daemon that is not available inside a build container. The gate is before you push:
      `cd salary-service && ./mvnw clean verify` and `cd salary-web && npm run verify`.
- [ ] Decide whether the free Render instance's cold start is acceptable.
- [ ] Consider a Content-Security-Policy header in `vercel.ts`. It is deliberately absent — a CSP
      added without testing against the real bundle tends to break the app in ways that only show up
      in production.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| **Login works from `curl` but the browser gets `403 Invalid CORS request`** | `APP_CORS_ORIGINS` is empty or wrong on Render. The `/api/*` proxy makes requests same-origin *to the browser*, which is what fixes the cookies — but the rewrite still forwards the browser's `Origin` header, so Spring's CORS filter sees it and rejects it against an empty allow-list. `curl` sends no `Origin`, which is why this passes every terminal check and fails on the first real sign-in. Set it to the exact Vercel origin, no trailing slash. |
| Sign-in "succeeds" but every request 401s | `NEXT_PUBLIC_API_BASE_URL` is set to the Render URL. It must be empty so requests are relative and proxied. |
| `502` from `/api/*` on Vercel | Render instance asleep (free plan) or `API_ORIGIN` wrong / has a trailing slash. |
| App boots but every list is empty and no error | The `autoconfigure.exclude` override did not apply — confirm `SPRING_PROFILES_ACTIVE=prod`. Look for `HikariPool-1 - Start completed` in the logs; if it is absent, persistence is off. |
| `NumberFormatException` at startup | A Hikari timeout given as `10s` instead of milliseconds. Hikari's setters take a raw `long`. |
| Flyway `validate` fails | The schema was changed outside Flyway, or `FLYWAY_USER` is not the owner role. |
| Audit log turns out to be mutable | Flyway ran as the app role. Postgres owners keep DML on their own tables, so `V8`'s `REVOKE` did nothing. Re-run migrations as the owner. |
