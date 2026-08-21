"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { bandKeys } from "@/lib/api/keys";
import {
  createBand,
  fetchBands,
  previewBandVersionImpact,
  updateBand,
  type CreateBandInput,
  type UpdateBandInput,
} from "@/lib/api/bands";
import { ApiError } from "@/lib/api/client";
import { failure, success } from "@/lib/notify";

export function useBands() {
  return useQuery({
    queryKey: bandKeys.list(),
    queryFn: fetchBands,
  });
}

export function useCreateBand() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateBandInput) => createBand(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: bandKeys.list() });
      success("Band created");
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't create band"),
  });
}

export function useUpdateBand(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdateBandInput) => updateBand(id, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: bandKeys.list() });
      success("New band version saved");
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't save band version"),
  });
}

export function useBandVersionImpact(id: string, amounts: { minAmount: string; midAmount: string; maxAmount: string } | null) {
  return useQuery({
    queryKey: ["bands", "versionImpact", id, amounts],
    queryFn: () => previewBandVersionImpact(id, amounts!),
    enabled: amounts !== null,
  });
}
