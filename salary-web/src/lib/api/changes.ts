import type { Band, BandStatus, Money } from "@/lib/money";
import { apiFetch } from "./client";

/**
 * Data fetchers for the change-lifecycle domain (CLAUDE.md §9): DRAFT → PENDING → APPROVED →
 * APPLIED, or PENDING → REJECTED (CLAUDE.md §8). Know nothing about React — `changes-queries.ts`
 * is the sibling that wraps these as hooks.
 */

export type ProposeChangeInput = {
  employeeId: string;
  effectiveDate: string;
  newBaseAmount: string;
  currency: string;
  changeReason: string;
  performanceRating?: string;
  note?: string;
};

export type ChangeStatus = "DRAFT" | "PENDING" | "APPROVED" | "APPLIED" | "REJECTED";

/**
 * ui doc §8.5: the Changes screen's row. Employee identity and proposer/decider names arrive
 * pre-resolved from the server (same discipline as P4.3's CSV export — never a raw id on a
 * display surface), since a HR_MANAGER/COMP_ANALYST viewer has no `/admin/users` access to
 * resolve a user id itself.
 */
export type Change = {
  id: string;
  employeeId: string;
  employeeFirstName: string;
  employeeLastName: string;
  employeeNumber: string;
  status: ChangeStatus;
  effectiveDate: string;
  currentBase: Money;
  newBase: Money;
  deltaAmount: Money;
  deltaPercent: string;
  changeReason: string;
  performanceRating: string | null;
  note: string | null;
  outOfBand: boolean;
  proposedBy: string;
  proposedByName: string | null;
  proposedAt: string | null;
  decidedBy: string | null;
  decidedByName: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  appliedAt: string | null;
  appliedRecordId: string | null;
};

/**
 * ui doc §8.4's live impact panel — every figure here is computed server-side (CLAUDE.md §6.1),
 * the same math applying this exact proposal would use. Nothing is persisted by fetching this.
 */
export type ChangeImpactPreview = {
  currentBase: Money;
  proposedBase: Money;
  deltaAmount: Money;
  deltaPercent: string;
  currentCompaRatio: string | null;
  proposedCompaRatio: string | null;
  currentRangePenetration: string | null;
  proposedRangePenetration: string | null;
  currentBandStatus: BandStatus;
  proposedBandStatus: BandStatus;
  band: Band | null;
  noteRequired: boolean;
  peerCohortSize: number;
  peerSuppressed: boolean;
  peerPercentileBefore: number | null;
  peerPercentileAfter: number | null;
};

export type ChangeImpactPreviewParams = {
  employeeId: string;
  effectiveDate: string;
  newBaseAmount: string;
  currency: string;
};

export async function fetchChangeImpactPreview(params: ChangeImpactPreviewParams): Promise<ChangeImpactPreview> {
  const search = new URLSearchParams(params);
  const response = await apiFetch(`/api/changes/impact-preview?${search.toString()}`);
  return (await response.json()) as ChangeImpactPreview;
}

export async function proposeChange(input: ProposeChangeInput): Promise<Change> {
  const response = await apiFetch("/api/changes", { method: "POST", body: JSON.stringify(input) });
  return (await response.json()) as Change;
}

/**
 * P10.5's bulk select → propose. `percentIncrease` and never an amount: everyone selected is on a
 * different salary, so the per-person figures are computed server-side in `BigDecimal`. Doing that
 * arithmetic here would be the money-in-TypeScript CLAUDE.md §6.1 forbids, and the result lands in
 * an insert-only ledger that cannot be quietly corrected.
 */
export type BulkProposeInput = {
  employeeIds: string[];
  effectiveDate: string;
  percentIncrease: string;
  changeReason: string;
  note?: string;
};

/** Partial success is the expected outcome — `rows` says which people were skipped and why. */
export type BulkProposeRow = {
  rowNumber: number;
  action: "PROPOSED" | "ERROR";
  employeeNumber: string | null;
  newAmount: string | null;
  changeReason: string | null;
  changeId: string | null;
  error: string | null;
};

export type BulkProposeResult = {
  totalRows: number;
  proposed: number;
  errors: number;
  rows: BulkProposeRow[];
};

export async function bulkProposeChanges(input: BulkProposeInput): Promise<BulkProposeResult> {
  const response = await apiFetch("/api/changes/bulk-propose", {
    method: "POST",
    body: JSON.stringify(input),
  });
  return (await response.json()) as BulkProposeResult;
}

export async function fetchChanges(status: ChangeStatus): Promise<Change[]> {
  const response = await apiFetch(`/api/changes?status=${status}`);
  return (await response.json()) as Change[];
}

/** ui doc §8.5: a decision note is encouraged, not required, when approving. */
export async function approveChange(id: string, decisionNote?: string): Promise<Change> {
  const response = await apiFetch(`/api/changes/${id}/approve`, {
    method: "POST",
    body: JSON.stringify({ decisionNote: decisionNote || null }),
  });
  return (await response.json()) as Change;
}

/** ui doc §8.5: rejecting requires a note — enforced server-side too (400 without one). */
export async function rejectChange(id: string, decisionNote: string): Promise<Change> {
  const response = await apiFetch(`/api/changes/${id}/reject`, {
    method: "POST",
    body: JSON.stringify({ decisionNote }),
  });
  return (await response.json()) as Change;
}

/** Moves a DRAFT to PENDING — how a change proposed via `ProposeChangeDialog` (which only ever
 * creates a DRAFT) actually reaches the approval queue. */
export async function submitDraft(id: string): Promise<Change> {
  const response = await apiFetch(`/api/changes/${id}/submit`, { method: "POST" });
  return (await response.json()) as Change;
}

export async function discardDraft(id: string): Promise<void> {
  await apiFetch(`/api/changes/${id}`, { method: "DELETE" });
}
