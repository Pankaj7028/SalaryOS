import type { Band, BandStatus, Money } from "@/lib/money";
import { apiFetch } from "./client";

/**
 * Data fetchers for the employee domain (CLAUDE.md §9). Know nothing about
 * React or TanStack Query — `employees-queries.ts` is the sibling that wraps
 * these as hooks.
 */

export type EmployeeSummary = {
  id: string;
  employeeNumber: string;
  firstName: string;
  lastName: string;
  workEmail: string;
  departmentId: string | null;
  locationId: string | null;
  jobLevelId: string | null;
  employmentType: string;
  fte: string;
  status: string;
  hireDate: string;
  terminationDate: string | null;
  bandMismatched: boolean;
  currentBasePay: Money | null;
  compaRatio: string | null;
  rangePenetration: string | null;
  bandStatus: BandStatus | null;
  band: Band | null;
};

export type EmployeeListFilters = {
  q?: string;
  departmentId?: string;
  locationId?: string;
  countryCode?: string;
  jobLevelId?: string;
  status?: string;
  bandStatus?: string;
};

export type EmployeeListParams = EmployeeListFilters & {
  /** "compaRatio" for FR-2.2's compa-ratio sort; omitted (or anything else) keeps the default
   * last-name order. Export has no sort param — it always matches the doc's "same filters,
   * unpaginated" contract, not the on-screen sort. */
  sortBy?: string;
  cursor?: string;
  limit?: number;
};

export type EmployeePage = {
  items: EmployeeSummary[];
  nextCursor: string | null;
};

export type CompensationComponent = {
  componentType: string;
  amount: Money;
  percentOfBase: number | null;
  isRecurring: boolean;
};

export type EmployeeDetail = {
  id: string;
  employeeNumber: string;
  firstName: string;
  lastName: string;
  workEmail: string;
  departmentId: string | null;
  locationId: string | null;
  jobFamilyId: string | null;
  jobLevelId: string | null;
  managerId: string | null;
  employmentType: string;
  fte: string;
  status: string;
  hireDate: string;
  terminationDate: string | null;
  bandMismatched: boolean;
  currentBasePay: Money | null;
  compaRatio: string | null;
  rangePenetration: string | null;
  bandStatus: BandStatus | null;
  band: Band | null;
  components: CompensationComponent[];
};

/** FR-6.6. `suppressed` is true, and every figure null, when the cohort has fewer than 5 members. */
export type CreateEmployeeInput = {
  employeeNumber: string;
  firstName: string;
  lastName: string;
  workEmail: string;
  departmentId: string;
  locationId: string;
  jobFamilyId: string;
  jobLevelId: string;
  managerId?: string;
  hireDate: string;
  employmentType: string;
  fte: string;
};

/** A new hire's first-ever pay period (FR-2.5's companion) — always annual, same convention
 * `ProposeChangeInput` uses. Only valid before the employee has any comp history at all. */
export type InitialCompensationInput = {
  amount: string;
  currency: string;
};

export type PeerComparison = {
  cohortSize: number;
  suppressed: boolean;
  p25: Money | null;
  median: Money | null;
  p75: Money | null;
  percentile: number | null;
};

/**
 * One ledger entry (FR-3.6/FR-6.7). No `note`, proposer, or approver yet — those live on the
 * `compensation_changes` row this record's `changeId` points at, and that domain doesn't exist
 * until P6.1.
 */
export type CompensationRecord = {
  id: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  base: Money;
  payFrequency: string;
  annualBaseAmount: string;
  normalizedAnnualBase: Money;
  bandId: string | null;
  compaRatio: string | null;
  rangePenetration: string | null;
  changeReason: string;
  changeId: string | null;
  supersededBy: string | null;
  createdBy: string;
  createdAt: string;
};

function buildQuery(params: EmployeeListParams): string {
  const search = new URLSearchParams();
  if (params.q) search.set("q", params.q);
  if (params.departmentId) search.set("departmentId", params.departmentId);
  if (params.locationId) search.set("locationId", params.locationId);
  if (params.countryCode) search.set("countryCode", params.countryCode);
  if (params.jobLevelId) search.set("jobLevelId", params.jobLevelId);
  if (params.status) search.set("status", params.status);
  if (params.bandStatus) search.set("bandStatus", params.bandStatus);
  if ("sortBy" in params && params.sortBy) search.set("sortBy", params.sortBy);
  if (params.cursor) search.set("cursor", params.cursor);
  if (params.limit) search.set("limit", String(params.limit));
  return search.toString();
}

export async function fetchEmployees(params: EmployeeListParams): Promise<EmployeePage> {
  const query = buildQuery(params);
  const response = await apiFetch(`/api/employees${query ? `?${query}` : ""}`);
  return (await response.json()) as EmployeePage;
}

export async function fetchEmployee(id: string): Promise<EmployeeDetail> {
  const response = await apiFetch(`/api/employees/${id}`);
  return (await response.json()) as EmployeeDetail;
}

export async function createEmployee(input: CreateEmployeeInput): Promise<EmployeeDetail> {
  const response = await apiFetch("/api/employees", { method: "POST", body: JSON.stringify(input) });
  return (await response.json()) as EmployeeDetail;
}

export async function setInitialCompensation(id: string, input: InitialCompensationInput): Promise<EmployeeDetail> {
  const response = await apiFetch(`/api/employees/${id}/initial-compensation`, {
    method: "POST",
    body: JSON.stringify(input),
  });
  return (await response.json()) as EmployeeDetail;
}

export async function fetchPeers(id: string): Promise<PeerComparison> {
  const response = await apiFetch(`/api/employees/${id}/peers`);
  return (await response.json()) as PeerComparison;
}

export async function fetchCompensationHistory(id: string): Promise<CompensationRecord[]> {
  const response = await apiFetch(`/api/employees/${id}/compensation`);
  return (await response.json()) as CompensationRecord[];
}

/**
 * The CSV export always matches the on-screen filter (FR-2.7) — same query
 * builder as the list, minus cursor/limit, hitting a different path. A plain
 * navigation to this URL downloads (the service sends
 * `Content-Disposition: attachment`); the browser carries the session cookie
 * because it is a top-level GET, which `SameSite=Lax` allows.
 */
export function employeesExportUrl(filters: EmployeeListFilters): string {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  const query = buildQuery(filters);
  return `${base}/api/employees/export${query ? `?${query}` : ""}`;
}
