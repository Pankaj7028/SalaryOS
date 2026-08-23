import { apiFetch } from "./client";

/** `action` mirrors `ChangeService#bulkUpload` exactly (P6.3) — `PROPOSED` (a DRAFT change was
 * created) or `ERROR` (row rejected, nothing created). */
export type ChangeBulkUploadRowResult = {
  rowNumber: number;
  action: "PROPOSED" | "ERROR";
  employeeNumber: string | null;
  newAmount: string | null;
  changeReason: string | null;
  changeId: string | null;
  error: string | null;
};

export type ChangeBulkUploadResult = {
  totalRows: number;
  proposed: number;
  errors: number;
  rows: ChangeBulkUploadRowResult[];
};

/** No dry run (FR-5.8) — a DRAFT is cheap to discard, so there's no separate preview step by
 * design. CSV shape: `employeeNumber,newAmount,changeReason[,note]`; `effectiveDate` applies to
 * every row in the file, not the CSV itself (a row's currency always comes from the employee's
 * own current pay, never a column here). */
export async function bulkUploadChangesCsv(file: File, effectiveDate: string): Promise<ChangeBulkUploadResult> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await apiFetch(`/api/changes/bulk-upload?effectiveDate=${effectiveDate}`, {
    method: "POST",
    body: formData,
  });
  return (await response.json()) as ChangeBulkUploadResult;
}
