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
| **Phase** | P4 in progress |
| **Last completed** | `P4.2` (create/edit/terminate + band-mismatch). `P4.3` code is done but marked `[~]` — owed a live browser pass, see below. |
| **Next step** | Get a browser connected, run the P4.3 visual pass, mark it `[x]`, then `P4.4`. |
| **Blockers** | No Neon project yet (`P0.3`) — not required by anything built so far. Chrome extension was not connected this session. |

`BuildPlan.md` is the authority on step status; this row is the fast path. If they disagree,
`BuildPlan.md` wins.

Done: `P0.1` `P0.2` `P0.4` · `P1` · `P2` (all) · `P3.1`–`P3.7` · `P4.1`, `P4.2`. In progress: `P4.3`
(code done, needs a browser pass). Blocked: `P0.3`. Untouched: `P4.4`–`P9`.

---

## Docker is unblocked — colima, not Docker Desktop

`DOCKER_HOST` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` are exported in `~/.zshrc`, but **the
Bash tool's shell does not source `~/.zshrc` per call** — export them explicitly (or `source
~/.zshrc`) in the same command as any Maven invocation that needs Docker:

```bash
export DOCKER_HOST=unix:///Users/pankajmandal/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

`colima status` confirms it's running; `colima start` if not. `./mvnw clean verify` is
Testcontainers-backed (Postgres 17) and currently passes **63/63**.

---

## No Neon yet — manual verification recipe (throwaway local Postgres)

`P0.3` (a real Neon `DATABASE_URL`) still needs a human. `P9` (`SeedRunner`, 10k employees) doesn't
exist yet either. For a UI step that needs real data to look at, stand up a disposable local
Postgres instead — this is what P4.3 used, then tore down at session end:

```bash
docker run -d --name salaryos-devdb -e POSTGRES_PASSWORD=devpass -e POSTGRES_DB=salaryos \
  -p 5433:5432 postgres:17
```

Create `salary-service/src/main/resources/application-local.yml` (git-ignored — the tracked
`application-local.yml.example` is the template) pointing at `localhost:5433`, with
`spring.autoconfigure.exclude: []` (the base `application.yml` still has the P0.2 stub exclusion
block — a profile file overrides it) and `app.cors.allowed-origins` set to whatever port the
frontend runs on. Run `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` — Flyway migrates
on startup. Seed a minimal dataset by hand (departments → locations → job families/levels → salary
bands → fx_rates → employees → compensation_records → employee_current_comp, in that FK order); a
worked example covering every `BandBar` state (in-band, below-min, above-max, no-band,
terminated/no-comp) is in the P4.3 done-note in `BuildPlan.md`. For a login user, `SecurityConfig`
wraps Argon2id in a `DelegatingPasswordEncoder` keyed `"argon2"`, so the stored hash must be
prefixed `{argon2}` — generate one via `jshell` on the built classpath:

```bash
./mvnw -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
echo 'import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
var enc = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
System.out.println("{argon2}" + enc.encode("Password123!"));
/exit' | jshell --class-path "target/classes:$(cat /tmp/cp.txt)" -q
```

Frontend: per the "verify against `next start`" gotcha below, `npx next start -p 3100` (after `npm
run build`), with `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` in `.env.local` and the
backend's CORS origin matching whatever port you use.

---

## Environment facts (verified 2026-08-21)

- **Java 25**; toolchain targets **17** per the pinned stack. Do not raise `maven.compiler.release`
  past 17 without an ADR.
- **No system Maven** — use `./mvnw`. **Node v26.3.0 / npm 11.16.0.**
- `~/.m2` is populated for Boot 4.0.8, so the backend builds offline.
- **`npm install @tanstack/react-table` installs v9 by default** (a breaking rewrite — no
  `useReactTable`/`getCoreRowModel` on its public API). CLAUDE.md pins v8: `npm install
  @tanstack/react-table@^8`.

---

## Decisions taken outside the docs

- **Layer docs moved to `docs/`** at P0.1; the assessment PDF stays at the repo root as an input.
- **`.claude/settings.json` is committed; `.claude/settings.local.json` is git-ignored.**
- **Spring Boot pinned to 4.0.8, not the 4.0.3 the docs first named** — same minor line, five patch
  releases of fixes. Every doc and the pom were updated together.
- **shadcn is 4.x `radix-nova`, not `new-york`** — v4 removed new-york; the CLI now takes a *base*
  (`base`|`radix`|`aria`) and a *preset* (`nova`|`vega`|…). Radix was chosen over v4's `base`
  default because the screens in the UI doc assume Radix primitives.
- **One `.gitignore`, at the root** — the generated ones in `salary-service/` and `salary-web/` were
  removed. Add new ignores at the root.
- **`getCurrentUser()` in `src/lib/auth/current-user.ts` is a placeholder** and the single seam where
  `GET /api/auth/me` lands at P2.5. Change `role` there to view the app as another role.
- **P4.3's Employees list omits column sort, a "band status" filter, "page N" jump, saved views, and
  bulk-select** — each because nothing on the backend supports it yet, not an oversight. Full
  reasoning in the P4.3 done-note in `BuildPlan.md`.

---

## Gotchas found the hard way

- **Boot 4 moved every autoconfiguration class into a per-module package** —
  `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`, not Boot 3's
  `…boot.autoconfigure.jdbc.…`. A wrong name in `spring.autoconfigure.exclude` is **ignored
  silently**. Confirm against `AutoConfiguration.imports` inside the jar.
- **Boot 4 renamed the starters**: `spring-boot-starter-webmvc` (not `-web`), test support per
  module (`…-webmvc-test`, `…-data-jpa-test`).
- **Jackson 3 lives under `tools.jackson.*`, not `com.fasterxml.jackson.*`** — the latter is only
  present transitively via JJWT (Jackson 2) and is not a Spring-managed bean. `spring-boot-
  starter-jackson` pulls only `tools.jackson.core:jackson-{core,databind}`; there is no separate
  Jackson-3 `jackson-annotations` artifact on the classpath currently, so `@JsonFormat` etc. from
  `com.fasterxml.jackson.annotation` cannot be added without pulling in a new dependency — not
  attempted yet, and not needed yet (money amounts serialize as JSON numbers, not the strings
  `lib/money.ts` was documented for, but every consumer already copes: `formatAmount` casts to
  `number` anyway and salary magnitudes are nowhere near a `double`'s precision boundary).
- **Lombok on JDK 25 prints `sun.misc.Unsafe` warnings on every compile.** Cosmetic.
- **`compensation_records.range_penetration` and `employee_current_comp.range_penetration` are
  `numeric(6,4)`** — max absolute value under 100 — but range penetration legitimately exceeds 100
  for anyone paid above band max. Not yet fixed (needs a fix-forward migration); P4.3's seed data
  had to clamp a 130% test case to 99.99 to insert.
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
`verify:components`. Backend: `./mvnw clean verify` (Testcontainers, needs Docker env vars above).
