"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { authKeys } from "@/lib/api/keys";
import { fetchCurrentUser, login, logout } from "@/lib/api/auth";

/**
 * The client-side read of "who is signed in" (CLAUDE.md §4.4 — a component
 * that needs the current user calls `GET /api/auth/me`, never a token read).
 * Server Components use `getCurrentUser()` in `current-user.ts` instead; this
 * is for client islands that need it reactively (the avatar menu's sign-out,
 * anything that must notice a session ending without a full page reload).
 */
export function useSession() {
  return useQuery({
    queryKey: authKeys.me(),
    queryFn: fetchCurrentUser,
    retry: false,
    staleTime: 60_000,
  });
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) => login(email, password),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: authKeys.me() }),
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: logout,
    onSuccess: () => queryClient.clear(),
  });
}
