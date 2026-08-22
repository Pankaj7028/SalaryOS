"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fxRateKeys } from "@/lib/api/keys";
import { addFxRate, fetchFxRateAdmin, type CreateFxRateInput } from "@/lib/api/fx-rates";
import { ApiError } from "@/lib/api/client";
import { failure, success } from "@/lib/notify";

export function useFxRateAdmin() {
  return useQuery({
    queryKey: fxRateKeys.admin(),
    queryFn: fetchFxRateAdmin,
  });
}

export function useAddFxRate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateFxRateInput) => addFxRate(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: fxRateKeys.admin() });
      success("Rate added");
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't add rate"),
  });
}
