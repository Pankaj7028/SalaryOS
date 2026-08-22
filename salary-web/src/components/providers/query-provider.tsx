"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { TooltipProvider } from "@/components/ui/tooltip";
import { ApiError } from "@/lib/api/client";

/**
 * One `QueryClient` per browser tab, created lazily so it survives Fast
 * Refresh without losing cache — `useState`'s initializer runs once.
 *
 * `TooltipProvider` lives here too — one instance app-wide, same discipline
 * as the root `<Toaster>` (CLAUDE.md §9): a second provider would only
 * fragment the shared hover-delay behaviour, not error visibly.
 *
 * `retry` skips TanStack Query's default 3-attempt retry for any 4xx —
 * a 403/404 will not stop being one on the second try. Found during P8's QA
 * pass: a role-gated page hit directly (a stale bookmark, browser back, a
 * shared link) retried its failing query 3 times, each retry re-triggering
 * `apiFetch`'s 403 toast — three stacked "Access denied" toasts and a
 * multi-second stuck skeleton before the real error state ever showed.
 * 5xx/network failures still get the default retry — those genuinely can
 * succeed a moment later.
 */
export function QueryProvider({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: (failureCount, error) => {
              if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
                return false;
              }
              return failureCount < 3;
            },
          },
        },
      }),
  );
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>{children}</TooltipProvider>
    </QueryClientProvider>
  );
}
