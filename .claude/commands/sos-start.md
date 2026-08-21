---
description: Start or resume a Salary OS build session on the current BuildPlan step
allowed-tools: Bash(git*), Bash(./scripts/doc.sh:*), Read, Grep, Glob
---

# Salary OS — session start

Live state, already gathered (do **not** re-run these):

- Last commit: !`git log --oneline -1`
- Working tree: !`git status --short | head -20`
- Current step: !`grep -nE '^- \[[ ~]\]' BuildPlan.md | head -1`
- Carry-over: !`sed -n '/^## Where we are/,/^---/p' docs/STATE.md`

## Do this

1. **Read only what the step needs.** `CLAUDE.md` is already in context. For the step's layer doc,
   pull the specific section with `scripts/doc.sh` — do **not** `cat` a whole doc:
   - backend step → `scripts/doc.sh be <section>` (`scripts/doc.sh toc be` to find it)
   - UI step → `scripts/doc.sh ui <section>` (`scripts/doc.sh toc ui`)
   - the FR it implements → `scripts/doc.sh tr FR-<n>`
2. **Confirm the step aloud in one line** — its ID and its *Verify* clause — then implement it.
3. **Run the step's Verify.** Only that. Not the full suite (`CLAUDE.md §2B`).
4. **Report the numbers you actually observed.** Never "all tests pass".
5. **Mark `[x]`** in `BuildPlan.md`, update its Progress log and the "Where we are" table in
   `docs/STATE.md`, and commit as `<ID> <short desc>`.
6. **Continue to the next `[ ]`.** Stop only on a failed Verify (mark `[~]` with a one-line blocker),
   or when context runs short — then run `/sos-wrap`.

Scope check: if the work is not in `BuildPlan.md`, read `requirements-one-pager.md` §"Deliberately
out of scope" before building anything.
