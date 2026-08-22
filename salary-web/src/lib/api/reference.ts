import { apiFetch } from "./client";

/**
 * Reference lookups — departments, locations, job levels. Small, unpaginated
 * lists that back filter dropdowns and CSV export column resolution.
 */

export type Department = { id: string; name: string; code: string; parentId: string | null };
export type Location = { id: string; countryCode: string; city: string; name: string; isActive: boolean };
export type JobLevel = { id: string; jobFamilyId: string; levelCode: string; title: string; sortOrder: number };
export type JobFamily = { id: string; name: string; code: string };
export type Country = { code: string; name: string; defaultCurrency: string };

export async function fetchDepartments(): Promise<Department[]> {
  const response = await apiFetch("/api/reference/departments");
  return (await response.json()) as Department[];
}

export async function fetchLocations(): Promise<Location[]> {
  const response = await apiFetch("/api/reference/locations");
  return (await response.json()) as Location[];
}

export async function fetchJobLevels(): Promise<JobLevel[]> {
  const response = await apiFetch("/api/reference/job-levels");
  return (await response.json()) as JobLevel[];
}

export async function fetchJobFamilies(): Promise<JobFamily[]> {
  const response = await apiFetch("/api/reference/job-families");
  return (await response.json()) as JobFamily[];
}

export async function fetchCountries(): Promise<Country[]> {
  const response = await apiFetch("/api/reference/countries");
  return (await response.json()) as Country[];
}
