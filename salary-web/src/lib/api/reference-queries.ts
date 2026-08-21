"use client";

import { useQuery } from "@tanstack/react-query";
import { referenceKeys } from "@/lib/api/keys";
import { fetchCountries, fetchDepartments, fetchJobLevels, fetchLocations } from "@/lib/api/reference";

/** Reference data changes rarely (an HR Admin edits it, not day to day) — a long staleTime is correct here. */
const REFERENCE_STALE_TIME = 5 * 60_000;

export function useDepartments() {
  return useQuery({
    queryKey: referenceKeys.departments(),
    queryFn: fetchDepartments,
    staleTime: REFERENCE_STALE_TIME,
  });
}

export function useLocations() {
  return useQuery({
    queryKey: referenceKeys.locations(),
    queryFn: fetchLocations,
    staleTime: REFERENCE_STALE_TIME,
  });
}

export function useJobLevels() {
  return useQuery({
    queryKey: referenceKeys.jobLevels(),
    queryFn: fetchJobLevels,
    staleTime: REFERENCE_STALE_TIME,
  });
}

export function useCountries() {
  return useQuery({
    queryKey: referenceKeys.countries(),
    queryFn: fetchCountries,
    staleTime: REFERENCE_STALE_TIME,
  });
}
