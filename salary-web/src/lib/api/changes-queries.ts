"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { changeKeys, employeeKeys } from "@/lib/api/keys";
import {
  fetchChangeImpactPreview,
  proposeChange,
  type ChangeImpactPreviewParams,
  type ProposeChangeInput,
} from "@/lib/api/changes";
import { ApiError } from "@/lib/api/client";
import { failure, success } from "@/lib/notify";

export function useChangeImpactPreview(params: ChangeImpactPreviewParams | null) {
  return useQuery({
    queryKey: changeKeys.impactPreview(params ?? {}),
    queryFn: () => fetchChangeImpactPreview(params!),
    enabled: params !== null,
  });
}

export function useProposeChange() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ProposeChangeInput) => proposeChange(input),
    onSuccess: (change) => {
      queryClient.invalidateQueries({ queryKey: employeeKeys.detail(change.employeeId) });
      success("Change proposed", "Submit it for approval when you're ready.");
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't propose change"),
  });
}
