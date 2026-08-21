# STATE.md — carry-over between sessions

**Purpose:** everything a fresh session needs that is *not* derivable from the code, the docs, or
`git log`. This file lives in the repo on purpose — it survives a new Claude account, a new machine,
a different model, and a cleared context window. Assistant memory directories do not.

**Contract:** keep it under ~120 lines. It is loaded every session, so every line costs tokens
forever. When a fact becomes true in the code, delete it from here — the code is then the record.

---

## Where we are

| | |
|---|---|
| **Phase** | P0 partly blocked · **P3 complete** |
| **Last completed** | `P3.7` — Money, Delta, BandStatusBadge, BandBar |
| **Next step** | Nothing is unblocked. See "Blocked on the environment" below. |
| **Blockers** | **Docker not installed** and **no Neon database** |

`BuildPlan.md` is the authority on step status; this row is the fast path. If they disagree,
`BuildPlan.md` wins.

Done: `P0.1` `P0.2` `P0.4` · `P3.1`–`P3.7`. Blocked: `P0.3` `P0.5`. Untouched: `P1` `P2` `P4`–`P9`.

---

## Blocked on the environment — read this first

Everything remaining needs one of two things that only a human can provide.

1. **Docker is not installed** (`docker: command not found`). Testcontainers cannot start, so `P0.5`
   and every `P1.*` migration test are blocked, and `P2` depends on `P1`. Install Docker Desktop or
   colima, start it, then run `P0.5`'s Verify.
   **Do not work around this by adding H2.** `CLAUDE.md §3` and §12.12 forbid it: the schema uses a
   `daterange` exclusion constraint and `btree_gist`, so an H2 suite would pass while production
   fails.
2. **No Neon project or `DATABASE_URL`** — blocks `P0.3`. Templates are already committed
   (`.env.example`, `salary-service/src/main/resources/application-local.yml.example` with the
   Hikari settings from backend §2.1). To finish: copy the example to `application-local.yml`, fill
   in url/username/password, create `salary_schema`, **delete the `spring.autoconfigure.exclude`
   block from `application.yml`**, then run the Verify.

`P2.5` is frontend but still needs the API running, so it is blocked behind both.

---

## Environment facts (verified 2026-08-21)

- **Java 25**; toolchain targets **17** per the pinned stack. Do not raise `maven.compiler.release`
  past 17 without an ADR.
- **No system Maven** — use `./mvnw`. **Node v26.3.0 / npm 11.16.0.**
- `~/.m2` is populated for Boot 4.0.8, so the backend builds offline.

---

## Decisions taken outside the docs

- **Layer docs moved to `docs/`** at P0.1; the assessment PDF stays at the repo root as an input.
- **`.claude/settings.json` is committed; `.claude/settings.local.json` is git-ignored.**
- **Spring Boot pinned to 4.0.8, not the 4.0.3 the docs first named** — same minor line, five patch
  releases of fixes. Every doc and the pom were updated together.
- **shadcn is 4.x `radix-nova`, not `new-york`** — v4 removed new-york; the CLI now takes a *base*
  (`base`|`radix`|`aria`) and a *preset* (`nova`|`vega`|…). Radix was chosen over v4's `base`
  default because the screens in the UI doc assume Radix primitives.
- **Build order deviates from BuildPlan while P0.3/P0.5 are blocked.** P3 ran first because it needs
  neither Neon nor Docker. Nothing in P3 pre-empts the skipped steps.
- **One `.gitignore`, at the root** — the generated ones in `salary-service/` and `salary-web/` were
  removed. Add new ignores at the root.
- **`config/SecurityConfig.java` is a P0.2 stub** (health open, everything else authenticated);
  P2.1 replaces it with the cookie/JWT chain in `CLAUDE.md §4`.
- **`getCurrentUser()` in `src/lib/auth/current-user.ts` is a placeholder** and the single seam where
  `GET /api/auth/me` lands at P2.5. Change `role` there to view the app as another role.

---

## Gotchas found the hard way

- **Boot 4 moved every autoconfiguration class into a per-module package** —
  `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`, not Boot 3's
  `…boot.autoconfigure.jdbc.…`. A wrong name in `spring.autoconfigure.exclude` is **ignored
  silently**. Confirm against `AutoConfiguration.imports` inside the jar.
- **Boot 4 renamed the starters**: `spring-boot-starter-webmvc` (not `-web`), test support per
  module (`…-webmvc-test`, `…-data-jpa-test`).
- **Lombok on JDK 25 prints `sun.misc.Unsafe` warnings on every compile.** Cosmetic.
- **Verify UI steps against `next start`, not `next dev`** — the dev overlay covers the bottom-left
  corner and swallows clicks (it ate the sidebar Collapse button). Use `BASE_URL=…:3100`.
- **Playwright: use `waitUntil: "load"`, not `"networkidle"`** — networkidle never fires against
  `next start` and every navigation times out.
- **The collapsed sidebar peeks open on hover**, so a test measuring its width right after clicking
  Collapse reads 240px and is reading it *correctly*. Move the pointer away first.
- **Two shadcn components shipped broken and were repaired in place** (they are ours to edit):
  `command.tsx` never wrapped its children in `<Command>`, so the palette threw and silently never
  opened; `sonner.tsx` resolved its theme with next-themes, which is not our theme system.
- **`salary-web/AGENTS.md` and `salary-web/CLAUDE.md` are generated by `next dev`** and are committed
  deliberately — deleting them only produces churn.
- **Scanning-style tests must exclude test files and strip comments**, or they report themselves and
  their own prose (`notify.test.ts` did both before it was right).

---

## Verification entry points

`npm run verify` (tokens + contrast + lint + typecheck + tests + build) closes a UI step.
The browser-driven ones need `next start` on :3100 — `verify:visual` `verify:shell` `verify:topbar`
`verify:components`. Backend: `./mvnw clean package`.
