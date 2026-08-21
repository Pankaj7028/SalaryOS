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
| **Phase** | P4 code-complete, one verification step short of closing |
| **Last completed** | `P4.2` is `[x]`. `P4.3` and `P4.4` are both code-done but marked `[~]` — owed a live browser pass, see below. |
| **Next step** | Get a Chrome session that can reach this machine's `localhost`, run the P4.3 + P4.4 visual passes, mark both `[x]`, then `P5.1` `EffectiveDating`. |
| **Blockers** | No Neon project yet (`P0.3`, not required by anything built so far). Chrome extension cannot reach `localhost` on this machine — see gotcha below. |

`BuildPlan.md` is the authority on step status; this row is the fast path. If they disagree,
`BuildPlan.md` wins.

Done: `P0.1` `P0.2` `P0.4` · `P1` · `P2` (all) · `P3.1`–`P3.7` · `P4.1`, `P4.2`. In progress: `P4.3`,
`P4.4` (both code done, both need a browser pass). Blocked: `P0.3`. Untouched: `P5`–`P9`.

---

## Docker is unblocked — colima, not Docker Desktop

`DOCKER_HOST`/`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` are in `~/.zshrc`, but **the Bash tool's shell
doesn't source it per call** — export both explicitly alongside any Maven command that needs Docker:
`export DOCKER_HOST=unix:///Users/pankajmandal/.colima/default/docker.sock` and
`export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`. `colima status`/`colima start`
as needed. `./mvnw clean verify` (Testcontainers, Postgres 17) currently passes **63/63**.

---

## No Neon yet — manual verification recipe (throwaway local Postgres)

`P0.3` (a real Neon `DATABASE_URL`) and `P9` (`SeedRunner`) don't exist yet. For a UI step that
needs real data: `docker run -d --name salaryos-devdb -e POSTGRES_PASSWORD=devpass -e
POSTGRES_DB=salaryos -p 5433:5432 postgres:17`. Create `salary-service/src/main/resources/
application-local.yml` (git-ignored; `application-local.yml.example` is the template) pointing at
`localhost:5433`, with `spring.autoconfigure.exclude: []` (overriding the P0.2 stub in
`application.yml`) and `app.cors.allowed-origins` matching the frontend's port. Run `./mvnw
spring-boot:run -Dspring-boot.run.profiles=local` — Flyway migrates on startup. Seed by hand in FK
order (departments → locations → job families/levels → salary bands → fx_rates → employees →
compensation_records → employee_current_comp); a worked example covering every `BandBar` state plus
a 5-person peer cohort is in the P4.3/P4.4 done-notes in `BuildPlan.md`. Login user password hash
needs the `{argon2}` prefix (`DelegatingPasswordEncoder` keyed `"argon2"`) — generate via `jshell`:
`./mvnw -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt` then `echo 'import
org.springframework.security.crypto.argon2.Argon2PasswordEncoder; var enc =
Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(); System.out.println("{argon2}" +
enc.encode("Password123!")); /exit' | jshell --class-path "target/classes:$(cat /tmp/cp.txt)" -q`.
Frontend: `npx next start -p 3100` (after `npm run build` — see "verify against `next start`" gotcha
below), `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` in `.env.local`.

---

## Environment facts (verified 2026-08-21)

- **Java 25**; toolchain targets **17**. Do not raise `maven.compiler.release` without an ADR.
- **No system Maven** — use `./mvnw`. **Node v26.3.0 / npm 11.16.0.** `~/.m2` is populated for Boot
  4.0.8, so the backend builds offline.
- **`npm install @tanstack/react-table` installs v9 by default** (breaking rewrite — no
  `useReactTable`/`getCoreRowModel`). CLAUDE.md pins v8: `npm install @tanstack/react-table@^8`.

---

## Decisions taken outside the docs

- **Layer docs moved to `docs/`** at P0.1; the assessment PDF stays at the repo root as an input.
- **`.claude/settings.json` is committed; `.claude/settings.local.json` is git-ignored.**
- **Spring Boot pinned to 4.0.8**, not the docs' original 4.0.3 — same minor line, five patch
  releases of fixes. Every doc and the pom were updated together.
- **shadcn is 4.x `radix-nova`, not `new-york`** (v4 removed new-york) — Radix chosen over v4's
  `base` default because the UI doc's screens assume Radix primitives.
- **One `.gitignore`, at the root.** Add new ignores there, not in a package subdirectory.
- **`getCurrentUser()` in `src/lib/auth/current-user.ts` is a placeholder**, the seam where
  `GET /api/auth/me` lands at P2.5. Change `role` there to view the app as another role.
- **P4.3's Employees list omits column sort, a "band status" filter, "page N" jump, saved views, and
  bulk-select** — each because nothing on the backend supports it yet. Reasoning in its done-note.
- **`GET /employees/{id}/peers` (FR-6.6) was built at P4.4, not P7.5** — Technical-Requirements.md
  §5 places the route under Employees, and `EmployeeController` already owned the stub. P7.5's real
  remaining scope is just `increase-cycle` (FR-6.5).
- **`TooltipProvider` lives in `query-provider.tsx`** (added P4.4) — one instance at the app root,
  same discipline as the root `<Toaster>`. Don't add a second one around a feature.

---

## Gotchas found the hard way

- **Boot 4 moved every autoconfiguration class into a per-module package** and **renamed the
  starters** (`spring-boot-starter-webmvc`, not `-web`; test support per module). A wrong class name
  in `spring.autoconfigure.exclude` is **ignored silently** — confirm against `AutoConfiguration.
  imports` inside the jar.
- **Jackson 3 lives under `tools.jackson.*`, not `com.fasterxml.jackson.*`** (the latter is only
  transitive via JJWT/Jackson 2, not a Spring bean). No Jackson-3 `jackson-annotations` artifact is
  on the classpath, so `@JsonFormat` etc. isn't available without a new dependency — not needed yet
  (money serializes as a JSON number; every consumer already copes).
- **Lombok on JDK 25 prints `sun.misc.Unsafe` warnings on every compile.** Cosmetic.
- **The Claude-in-Chrome extension cannot reach this machine's `localhost`**, confirmed twice (P4.3,
  P4.4): a connected tab loads `https://example.com` fine but errors on both `localhost:3100` and
  `localhost:8080/actuator/health`. Don't retry past 2 attempts — confirm with the health-check URL,
  then fall back to `curl` verification and say plainly that no visual pass happened.
- **`compensation_records`/`employee_current_comp`.`range_penetration` are `numeric(6,4)`** (max
  abs value under 100) but range penetration legitimately exceeds 100 above band max. Not yet fixed
  (needs a fix-forward migration); P4.3's seed data had to clamp a 130% case to 99.99 to insert.
- **Verify UI steps against `next start`, not `next dev`** — the dev overlay swallows clicks (ate
  the sidebar Collapse button). Use `BASE_URL=…:3100`. Playwright: `waitUntil: "load"`, not
  `"networkidle"` (never fires against `next start`).
- **The collapsed sidebar peeks open on hover** — move the pointer away before measuring its width.
- **Two shadcn components were repaired in place** (ours to edit): `command.tsx` never wrapped
  children in `<Command>`; `sonner.tsx` used next-themes instead of our theme system.
- **`salary-web/AGENTS.md`/`CLAUDE.md` are generated by `next dev`** and committed deliberately —
  deleting them only produces churn.
- **Scanning-style tests must exclude test files and strip comments**, or they report their own
  prose (`notify.test.ts` did both before it was right).

---

## Verification entry points

`npm run verify` (tokens + contrast + lint + typecheck + tests + build) closes a UI step. Browser-
driven checks need `next start` on :3100 — `verify:visual` `verify:shell` `verify:topbar`
`verify:components`. Backend: `./mvnw clean verify` (Testcontainers, needs the Docker env vars above).
