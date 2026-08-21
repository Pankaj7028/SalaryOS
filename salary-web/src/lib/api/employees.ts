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
  compaRatio: number | null;
  rangePenetration: number | null;
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
};

export type EmployeeListParams = EmployeeListFilters & {
  cursor?: string;
  limit?: number;
};

export type EmployeePage = {
  items: EmployeeSummary[];
  nextCursor: string | null;
};

function buildQuery(params: EmployeeListParams): string {
  const search = new URLSearchParams();
  if (params.q) search.set("q", params.q);
  if (params.departmentId) search.set("departmentId", params.departmentId);
  if (params.locationId) search.set("locationId", params.locationId);
  if (params.countryCode) search.set("countryCode", params.countryCode);
  if (params.jobLevelId) search.set("jobLevelId", params.jobLevelId);
  if (params.status) search.set("status", params.status);
  if (params.cursor) search.set("cursor", params.cursor);
  if (params.limit) search.set("limit", String(params.limit));
  return search.toString();
}

export async function fetchEmployees(params: EmployeeListParams): Promise<EmployeePage> {
  const query = buildQuery(params);
  const response = await apiFetch(`/api/employees${query ? `?${query}` : ""}`);
  return (await response.json()) as EmployeePage;
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
