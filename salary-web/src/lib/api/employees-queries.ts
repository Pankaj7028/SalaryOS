"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { employeeKeys } from "@/lib/api/keys";
import { fetchEmployees, type EmployeeListParams } from "@/lib/api/employees";

/**
 * `keepPreviousData` keeps the current rows on screen while the next page or
 * filter loads — a table that blanks to a skeleton on every keystroke reads
 * as broken, not fresh.
 */
export function useEmployees(params: EmployeeListParams) {
  return useQuery({
    queryKey: employeeKeys.list(params as Record<string, string | number | undefined>),
    queryFn: () => fetchEmployees(params),
    placeholderData: keepPreviousData,
  });
}
