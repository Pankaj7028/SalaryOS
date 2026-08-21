import type { Money } from "@/lib/money";
import { apiFetch } from "./client";

/** FR-4.1/FR-4.5: a band is effective-dated per (job level × country), never mutated in place. */
export type Band = {
  id: string;
  jobLevelId: string;
  countryCode: string;
  min: Money;
  mid: Money;
  max: Money;
  effectiveFrom: string;
  effectiveTo: string | null;
  note: string | null;
  headcount: number;
};

export type CreateBandInput = {
  jobLevelId: string;
  countryCode: string;
  currency: string;
  minAmount: string;
  midAmount: string;
  maxAmount: string;
  effectiveFrom: string;
  note?: string;
};

export type UpdateBandInput = {
  currency: string;
  minAmount: string;
  midAmount: string;
  maxAmount: string;
  effectiveFrom: string;
  note?: string;
};

/** ui doc §8.6: shown before saving a new version — "the most useful number on the screen." */
export type BandVersionImpact = {
  cohortSize: number;
  changingStatus: number;
};

export async function fetchBands(): Promise<Band[]> {
  const response = await apiFetch("/api/bands");
  return (await response.json()) as Band[];
}

export async function createBand(input: CreateBandInput): Promise<Band> {
  const response = await apiFetch("/api/bands", { method: "POST", body: JSON.stringify(input) });
  return (await response.json()) as Band;
}

export async function updateBand(id: string, input: UpdateBandInput): Promise<Band> {
  const response = await apiFetch(`/api/bands/${id}`, { method: "PATCH", body: JSON.stringify(input) });
  return (await response.json()) as Band;
}

export async function previewBandVersionImpact(
  id: string,
  amounts: { minAmount: string; midAmount: string; maxAmount: string },
): Promise<BandVersionImpact> {
  const search = new URLSearchParams(amounts);
  const response = await apiFetch(`/api/bands/${id}/preview-version-impact?${search.toString()}`);
  return (await response.json()) as BandVersionImpact;
}
