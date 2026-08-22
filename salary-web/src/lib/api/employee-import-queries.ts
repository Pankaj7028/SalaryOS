"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { importEmployeesCsv } from "@/lib/api/employee-import";
import { ApiError } from "@/lib/api/client";
import { failure } from "@/lib/notify";

/** No `onSuccess` toast here -- the screen itself renders the diff, which is a richer result than
 * a toast could carry, and a dry run succeeding isn't something worth a toast anyway. */
export function useImportEmployeesCsv() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ file, dryRun }: { file: File; dryRun: boolean }) => importEmployeesCsv(file, dryRun),
    onSuccess: (result) => {
      if (!result.dryRun) {
        queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] === "employees" });
      }
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Import failed"),
  });
}
