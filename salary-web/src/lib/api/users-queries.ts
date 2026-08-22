"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { userKeys } from "@/lib/api/keys";
import {
  createUser,
  fetchUsers,
  issueResetToken,
  updateUser,
  type CreateUserInput,
  type UpdateUserInput,
} from "@/lib/api/users";
import { ApiError } from "@/lib/api/client";
import { failure, success } from "@/lib/notify";

export function useUsers() {
  return useQuery({ queryKey: userKeys.list(), queryFn: fetchUsers });
}

export function useCreateUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateUserInput) => createUser(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.list() });
      success("User created", "Issue a reset token so they can set a password.");
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't create user"),
  });
}

export function useUpdateUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: UpdateUserInput }) => updateUser(id, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.list() });
      success("User updated");
    },
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't update user"),
  });
}

/** No success toast -- the caller shows the raw token inline, which is the real confirmation. */
export function useIssueResetToken() {
  return useMutation({
    mutationFn: (id: string) => issueResetToken(id),
    onError: (error) => failure(error instanceof ApiError ? error.problem : error, "Couldn't issue reset token"),
  });
}
