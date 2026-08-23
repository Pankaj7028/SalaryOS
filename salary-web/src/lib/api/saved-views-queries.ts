"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { savedViewKeys } from "@/lib/api/keys";
import {
  deleteSavedView,
  fetchSavedViews,
  saveView,
  type SaveViewInput,
} from "@/lib/api/saved-views";
import { ApiError } from "@/lib/api/client";
import { failure, success } from "@/lib/notify";

export function useSavedViews() {
  return useQuery({
    queryKey: savedViewKeys.list(),
    queryFn: fetchSavedViews,
  });
}

export function useSaveView() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: SaveViewInput) => saveView(input),
    onSuccess: (view) => {
      queryClient.invalidateQueries({ queryKey: savedViewKeys.list() });
      success("View saved", view.shared ? `"${view.name}" is visible to everyone` : `"${view.name}"`);
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't save view"),
  });
}

export function useDeleteSavedView() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteSavedView(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: savedViewKeys.list() });
      success("View deleted");
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't delete view"),
  });
}
