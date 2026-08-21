/** `compensation_changes.change_reason` / `compensation_records.change_reason` — one shared label map (CLAUDE.md §9: no drift between the ledger's history view and the propose dialog). */
export const CHANGE_REASON_LABEL: Record<string, string> = {
  INITIAL: "Initial hire",
  MERIT: "Merit increase",
  PROMOTION: "Promotion",
  MARKET_ADJUSTMENT: "Market adjustment",
  ROLE_CHANGE: "Role change",
  LOCATION_CHANGE: "Location change",
  CORRECTION: "Correction",
  DEMOTION: "Demotion",
};

/** `INITIAL` is never proposed through a change — it only ever exists as an employee's first-ever ledger row. */
export const PROPOSABLE_CHANGE_REASONS = Object.keys(CHANGE_REASON_LABEL).filter((r) => r !== "INITIAL");
