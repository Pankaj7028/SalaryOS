# Salary OS

Single-tenant compensation management for ACME — 10,000 employees, multiple countries, one
authoritative record of what everyone is paid.

| Piece | Tech | Responsibility |
|---|---|---|
| `salary-service/` | Spring Boot 4.0.8 (Java 17+) | Domain, persistence, auth, analytics, seeding |
| `salary-web/` | Next.js 16 (App Router) + shadcn/ui | The only user interface |

## Documentation

| Read this | For |
|---|---|
| `requirements-one-pager.md` | Scope contract — goal, the seven questions, exclusions |
| `Technical-Requirements.md` | FR/NFR, data model, API contract, acceptance criteria |
| `CLAUDE.md` | Architecture, auth model, cross-cutting invariants, conventions |
| `BuildPlan.md` | Build tracker — the first `[ ]` step is what happens next |
| `docs/salary-management-backend.md` | **Binding** for `salary-service/` |
| `docs/salary-management-ui.md` | **Binding** for `salary-web/` (design system) |
| `docs/STATE.md` | Current build state, decisions, and gotchas |

Read one section instead of a whole doc: `scripts/doc.sh ui 7.1` · `scripts/doc.sh be 2.3` ·
`scripts/doc.sh toc ui`.

## Running

Full run instructions, seeded credentials, and a map of where each of the seven questions is
answered land here at step **P9.7**. Until then:

```bash
cd salary-service && ./mvnw spring-boot:run     # API
cd salary-web && npm install && npm run dev     # UI
```

## Status

Build progress is tracked in `BuildPlan.md`. See its Progress log for the current step.
