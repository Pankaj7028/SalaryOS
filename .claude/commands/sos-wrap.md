---
description: Close a Salary OS session — persist state so the next one starts cheap
allowed-tools: Bash(git*), Read, Edit, Grep
---

# Salary OS — session wrap

Current state: !`git log --oneline -3` / !`git status --short | head -20`

Persist what the next session cannot re-derive. Be brief — every line here is re-read forever.

1. **`docs/STATE.md` → "Where we are"**: update phase, last completed, next step, blockers.
2. **`docs/STATE.md` → "Gotchas"**: add a one-line entry for anything that cost more than ten
   minutes to work out. Delete entries that the code now records on its own.
3. **`docs/STATE.md` → "Decisions taken outside the docs"**: add any choice a reader could not infer
   from the code, with its reason.
4. **`docs/STATE.md` → "Open questions"**: anything needing the human (credentials, a Neon project,
   an approval).
5. **`BuildPlan.md`**: every touched step is `[x]` or `[~]`-with-blocker. Progress log matches.
6. **A contested or reversed decision** goes in `docs/adr/NNN-slug.md`, not in STATE.md.
7. **Commit** the state update.

Then give the user a four-line summary: what was completed, what was verified (with the observed
numbers), what is next, and what is blocked.
