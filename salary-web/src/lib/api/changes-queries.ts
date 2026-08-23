"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { changeKeys, employeeKeys } from "@/lib/api/keys";
import {
  approveChange,
  discardDraft,
  fetchChangeImpactPreview,
  fetchChanges,
  proposeChange,
  rejectChange,
  submitDraft,
  type ChangeImpactPreviewParams,
  type ChangeStatus,
  type ProposeChangeInput,
} from "@/lib/api/changes";
import { bulkUploadChangesCsv } from "@/lib/api/changes-bulk-upload";
import { ApiError } from "@/lib/api/client";
import { failure, success } from "@/lib/notify";

export function useChangeImpactPreview(params: ChangeImpactPreviewParams | null) {
  return useQuery({
    queryKey: changeKeys.impactPreview(params ?? {}),
    queryFn: () => fetchChangeImpactPreview(params!),
    enabled: params !== null,
  });
}

export function useChanges(status: ChangeStatus) {
  return useQuery({
    queryKey: changeKeys.list(status),
    queryFn: () => fetchChanges(status),
  });
}

export function useProposeChange() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ProposeChangeInput) => proposeChange(input),
    onSuccess: (change) => {
      queryClient.invalidateQueries({ queryKey: employeeKeys.detail(change.employeeId) });
      queryClient.invalidateQueries({ queryKey: changeKeys.list("DRAFT") });
      success("Change proposed", "Submit it for approval when you're ready.");
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't propose change"),
  });
}

/** Every mutation below touches the change lifecycle, so all five list tabs (a status can move
 * between two of them) are invalidated together rather than guessing which two apply. */
function invalidateChangeLists(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] === "changes" && query.queryKey[1] === "list" });
}

export function useApproveChange() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, decisionNote }: { id: string; decisionNote?: string }) => approveChange(id, decisionNote),
    onSuccess: () => {
      invalidateChangeLists(queryClient);
      success("Change approved");
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't approve change"),
  });
}

export function useRejectChange() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, decisionNote }: { id: string; decisionNote: string }) => rejectChange(id, decisionNote),
    onSuccess: () => {
      invalidateChangeLists(queryClient);
      success("Change rejected");
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't reject change"),
  });
}

export function useSubmitDraft() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => submitDraft(id),
    onSuccess: () => {
      invalidateChangeLists(queryClient);
      success("Change submitted", "It's now awaiting approval.");
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't submit change"),
  });
}

export function useDiscardDraft() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => discardDraft(id),
    onSuccess: () => {
      invalidateChangeLists(queryClient);
      success("Draft discarded");
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't discard draft"),
  });
}

/** No success toast — the screen renders the per-row diff itself, a richer result than a toast
 * could carry (same reasoning as `useImportEmployeesCsv`/`useImportBandsCsv`). */
export function useBulkUploadChangesCsv() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ file, effectiveDate }: { file: File; effectiveDate: string }) => bulkUploadChangesCsv(file, effectiveDate),
    onSuccess: (result) => {
      if (result.proposed > 0) {
        invalidateChangeLists(queryClient);
      }
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Bulk upload failed"),
  });
}
