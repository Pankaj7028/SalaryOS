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
| **Phase** | P0 — Foundations |
| **Last completed** | `P0.2` Spring Boot 4.0.8 scaffold |
| **Next step** | `P0.3` Neon + `salary_schema` |
| **Blockers** | `P0.3` needs a Neon project and `DATABASE_URL` from the human |

`BuildPlan.md` remains the authority on step status. This row is the fast path, not a second source
of truth — if they disagree, `BuildPlan.md` wins.

---

## Environment facts (verified 2026-08-21)

- **Java 25** (`java -version` → 25+37-LTS). Toolchain target stays **17** per the pinned stack;
  Spring Boot 4.0.8 requires 17+, so 25 runs it fine. Do not raise `maven.compiler.release` past 17
  without an ADR.
- **Node v26.3.0**, **npm 11.16.0**. Next.js 16 is happy here.
- **No system Maven.** Use `./mvnw` — the wrapper is generated in P0.2. `mvn` will not resolve.
- **Neon / `DATABASE_URL`:** not yet provisioned — this is what blocks P0.3. A human must create
  the Neon project; the pooled connection string goes in the git-ignored
  `salary-service/src/main/resources/application-local.yml`.
- **Build works offline after P0.2** — `~/.m2` is populated for Boot 4.0.8.
- **Docker** is required from P0.5 onward (Testcontainers Postgres 17). Confirm it is running
  before starting P0.5, not halfway through.

---

## Decisions taken outside the docs

- **Layer docs moved to `docs/`** at P0.1 (`git mv`). They were at the repo root; `CLAUDE.md §2`
  specifies `docs/`. All references in `CLAUDE.md` already pointed at `docs/…`, so nothing broke.
- **`Salary Management Assessment- Candidates.pdf` stays at the repo root.** It is the original
  brief — an input, not part of the source tree that `CLAUDE.md §2` describes.
- **`.claude/settings.json` is committed; `.claude/settings.local.json` is git-ignored.** Shared
  permissions travel with the repo; personal overrides do not.
- **Spring Boot pinned to 4.0.8, not the 4.0.3 the docs originally named** (decided 2026-08-21).
  Same minor line, five patch releases of fixes, no API change. Every doc and the pom were updated
  together — if you find a `4.0.3` anywhere, it is a leftover.
- **`application.yml` carries a `spring.autoconfigure.exclude` block that must be deleted at P0.3.**
  It exists only so the context starts with no database. Left in place it silently disables
  repositories and Flyway while the app still boots clean.
- **`config/SecurityConfig.java` is a P0.2 stub** — health permitted, everything else authenticated
  via HTTP Basic. P2.1 replaces it entirely with the cookie/JWT chain in `CLAUDE.md §4`.

---

## Gotchas found the hard way

_Add to this list whenever something costs more than ten minutes to work out. One line each._

- **Boot 4 moved every autoconfiguration class into a per-module package.** It is
  `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`, *not* Boot 3's
  `org.springframework.boot.autoconfigure.jdbc.…`. Same for `…boot.flyway.autoconfigure.…`,
  `…boot.hibernate.autoconfigure.…`, `…boot.data.jpa.autoconfigure.…`. A wrong name in an
  `spring.autoconfigure.exclude` list is **ignored silently** — nothing warns. Confirm against
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` inside the jar.
- **Boot 4 renamed the starters.** Initializr emits `spring-boot-starter-webmvc` (not `-web`), and
  test support is per-module: `spring-boot-starter-{webmvc,data-jpa,security,…}-test`.
- **Lombok on JDK 25 prints `sun.misc.Unsafe` warnings on every compile.** Cosmetic; the build
  passes. Do not "fix" it by downgrading the JDK.

---

## Open questions for the human

- **P0.3 needs a Neon project + database, and its pooled connection string** (the host containing
  `-pooler`, with `?sslmode=require`). Everything that does not need the credential is already
  committed: `.env.example` and `salary-service/src/main/resources/application-local.yml.example`
  (Hikari and schema settings per backend §2.1). To finish P0.3: copy the example to
  `application-local.yml`, fill in url/username/password, create `salary_schema`, **delete the
  `spring.autoconfigure.exclude` block from `application.yml`**, then run the step's Verify.
