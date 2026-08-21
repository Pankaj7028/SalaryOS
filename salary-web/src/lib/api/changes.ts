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

export type Change = {
  id: string;
  employeeId: string;
  status: string;
  effectiveDate: string;
  currentBase: Money;
  newBase: Money;
  changeReason: string;
  performanceRating: string | null;
  note: string | null;
};

/**
 * ui doc §8.4's live impact panel — every figure here is computed server-side (CLAUDE.md §6.1),
 * the same math applying this exact proposal would use. Nothing is persisted by fetching this.
 */
export type ChangeImpactPreview = {
  currentBase: Money;
  proposedBase: Money;
  deltaAmount: Money;
  deltaPercent: number;
  currentCompaRatio: number | null;
  proposedCompaRatio: number | null;
  currentRangePenetration: number | null;
  proposedRangePenetration: number | null;
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
