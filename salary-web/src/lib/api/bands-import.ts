import { apiFetch } from "./client";

/** `action` mirrors `BandService#importCsv` exactly (P5.3) — `CREATE` (first band for this
 * level × country), `VERSION` (supersedes an in-force one), or `ERROR` (row rejected, nothing
 * applied even outside a dry run). */
export type BandImportRowResult = {
  rowNumber: number;
  action: "CREATE" | "VERSION" | "ERROR";
  jobLevelId: string | null;
  countryCode: string | null;
  currency: string | null;
  minAmount: string | null;
  midAmount: string | null;
  maxAmount: string | null;
  effectiveFrom: string | null;
  error: string | null;
};

export type BandImportResult = {
  dryRun: boolean;
  totalRows: number;
  created: number;
  versioned: number;
  errors: number;
  rowsApplied: number;
  rows: BandImportRowResult[];
};

/** `dryRun=true` diffs the file without writing anything (FR-4.6). CSV shape: `jobLevelId,
 * countryCode,currency,minAmount,midAmount,maxAmount,effectiveFrom,note` (note optional). */
export async function importBandsCsv(file: File, dryRun: boolean): Promise<BandImportResult> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await apiFetch(`/api/bands/import?dryRun=${dryRun}`, {
    method: "POST",
    body: formData,
  });
  return (await response.json()) as BandImportResult;
}
