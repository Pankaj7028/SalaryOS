"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { TooltipProvider } from "@/components/ui/tooltip";

/**
 * One `QueryClient` per browser tab, created lazily so it survives Fast
 * Refresh without losing cache — `useState`'s initializer runs once.
 *
 * `TooltipProvider` lives here too — one instance app-wide, same discipline
 * as the root `<Toaster>` (CLAUDE.md §9): a second provider would only
 * fragment the shared hover-delay behaviour, not error visibly.
 */
export function QueryProvider({ children }: { children: ReactNode }) {
  const [queryClient] = useState(() => new QueryClient());
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>{children}</TooltipProvider>
    </QueryClientProvider>
  );
}
