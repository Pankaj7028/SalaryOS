"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { employeeKeys } from "@/lib/api/keys";
import {
  createEmployee,
  fetchCompensationHistory,
  fetchEmployee,
  fetchEmployees,
  fetchPeers,
  setInitialCompensation,
  type CreateEmployeeInput,
  type EmployeeListParams,
  type InitialCompensationInput,
} from "@/lib/api/employees";
import { ApiError } from "@/lib/api/client";
import { failure, success } from "@/lib/notify";

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

export function useEmployee(id: string, opts?: { enabled?: boolean }) {
  return useQuery({
    queryKey: employeeKeys.detail(id),
    queryFn: () => fetchEmployee(id),
    enabled: opts?.enabled ?? true,
  });
}

export function usePeers(id: string) {
  return useQuery({
    queryKey: employeeKeys.peers(id),
    queryFn: () => fetchPeers(id),
  });
}

export function useCompensationHistory(id: string) {
  return useQuery({
    queryKey: employeeKeys.compensationHistory(id),
    queryFn: () => fetchCompensationHistory(id),
  });
}

export function useCreateEmployee() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateEmployeeInput) => createEmployee(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] === "employees" && query.queryKey[1] === "list" });
      success("Employee created", "Set their starting salary next.");
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't create employee"),
  });
}

/** No success toast — the panel it replaces (an empty "no compensation record" state) is
 * confirmation enough once the employee's current pay actually renders. */
export function useSetInitialCompensation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: InitialCompensationInput }) => setInitialCompensation(id, input),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: employeeKeys.detail(id) });
      queryClient.invalidateQueries({ queryKey: employeeKeys.compensationHistory(id) });
      queryClient.invalidateQueries({ queryKey: employeeKeys.peers(id) });
      queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] === "employees" && query.queryKey[1] === "list" });
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't set starting salary"),
  });
}
