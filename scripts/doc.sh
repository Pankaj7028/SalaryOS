#!/usr/bin/env bash
# doc.sh — print ONE section of a Salary OS doc.
#
# Why: the binding docs total ~70KB (~18k tokens). Reading a whole one to answer a
# question about a single component burns context that the build step itself needs.
# Section numbers are stable; line numbers are not, so this resolves headings live.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

resolve() {
  case "$1" in
    ui|web|frontend)          echo "$here/docs/salary-management-ui.md" ;;
    be|backend|svc|service)   echo "$here/docs/salary-management-backend.md" ;;
    tr|tech|req)              echo "$here/Technical-Requirements.md" ;;
    one|scope|onepager)       echo "$here/requirements-one-pager.md" ;;
    claude|ctx)               echo "$here/CLAUDE.md" ;;
    plan|build)               echo "$here/BuildPlan.md" ;;
    state)                    echo "$here/docs/STATE.md" ;;
    *) echo "unknown doc alias: $1" >&2; return 1 ;;
  esac
}

usage() {
  cat <<'EOF'
Usage:
  scripts/doc.sh <doc> <section>   print one section   e.g. doc.sh ui 7.1 · doc.sh be 2.3
  scripts/doc.sh toc <doc>         print the headings  e.g. doc.sh toc ui
  scripts/doc.sh grep <pattern>    search every doc, with file:line
  scripts/doc.sh docs              list the aliases

Aliases: ui | be | tr | one | claude | plan | state
Sections are the numbers in the headings: 7.1, 2.3, FR-6, 2B, 12 ...
EOF
}

case "${1:-}" in
  ""|-h|--help|help) usage; exit 0 ;;
  docs)
    printf '%-10s %s\n' ui docs/salary-management-ui.md be docs/salary-management-backend.md \
      tr Technical-Requirements.md one requirements-one-pager.md claude CLAUDE.md \
      plan BuildPlan.md state docs/STATE.md
    exit 0 ;;
  toc)
    f="$(resolve "${2:?doc alias required}")"
    echo "── $(basename "$f") ($(wc -l < "$f" | tr -d ' ') lines) ──"
    grep -nE '^#{1,4} ' "$f"
    exit 0 ;;
  grep)
    pat="${2:?pattern required}"
    grep -rniE --include='*.md' "$pat" "$here"/*.md "$here"/docs 2>/dev/null \
      | sed "s|^$here/||" || echo "no match: $pat"
    exit 0 ;;
esac

f="$(resolve "$1")"
sec="${2:?section required — try: scripts/doc.sh toc $1}"

awk -v sec="$sec" -v src="$(basename "$f")" '
  BEGIN {
    esc = sec; gsub(/\./, "[.]", esc)
    pat = "^#+ +" esc "[.]?( |$)"
    found = 0
  }
  {
    lvl = match($0, /^#+/) ? RLENGTH : 0
    if (!found) {
      if (lvl && $0 ~ pat) { found = 1; start = lvl; print "── " src " §" sec " ──"; print }
      next
    }
    if (lvl && lvl <= start) exit
    print
  }
  END { if (!found) { print "no section \"" sec "\" in " src "; try: scripts/doc.sh toc <doc>" > "/dev/stderr"; exit 1 } }
' "$f"
