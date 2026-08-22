import { apiFetch } from "./client";

/** P8.4: same create-vs-update shape the CSV carries — `action` is never derived client-side. */
export type EmployeeImportRowResult = {
  rowNumber: number;
  action: "CREATE" | "UPDATE" | "ERROR";
  employeeNumber: string | null;
  firstName: string | null;
  lastName: string | null;
  error: string | null;
};

export type EmployeeImportResult = {
  dryRun: boolean;
  totalRows: number;
  created: number;
  updated: number;
  errors: number;
  rowsApplied: number;
  rows: EmployeeImportRowResult[];
};

/** `dryRun=true` diffs the file without writing anything (FR-4.6's contract, reused here). */
export async function importEmployeesCsv(file: File, dryRun: boolean): Promise<EmployeeImportResult> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await apiFetch(`/api/employees/import?dryRun=${dryRun}`, {
    method: "POST",
    body: formData,
  });
  return (await response.json()) as EmployeeImportResult;
}
