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
| **Last completed** | `P0.1` repo skeleton |
| **Next step** | `P0.2` Spring Boot 4.0.3 project |
| **Blockers** | none |

`BuildPlan.md` remains the authority on step status. This row is the fast path, not a second source
of truth — if they disagree, `BuildPlan.md` wins.

---

## Environment facts (verified 2026-08-21)

- **Java 25** (`java -version` → 25+37-LTS). Toolchain target stays **17** per the pinned stack;
  Spring Boot 4.0.3 requires 17+, so 25 runs it fine. Do not raise `maven.compiler.release` past 17
  without an ADR.
- **Node v26.3.0**, **npm 11.16.0**. Next.js 16 is happy here.
- **No system Maven.** Use `./mvnw` — the wrapper is generated in P0.2. `mvn` will not resolve.
- **Neon / `DATABASE_URL`:** not yet provisioned. P0.3 needs a human to create the Neon project;
  the connection string goes in the git-ignored `salary-service/src/main/resources/application-local.yml`.
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

---

## Gotchas found the hard way

_Add to this list whenever something costs more than ten minutes to work out. One line each._

- (none yet)

---

## Open questions for the human

- **P0.3** needs a Neon project + database created, and the pooled connection string
  (`?sslmode=require`) handed over. Everything up to P0.2 proceeds without it.
