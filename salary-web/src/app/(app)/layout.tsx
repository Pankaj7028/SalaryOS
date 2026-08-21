import type { ReactNode } from "react";
import { AppShell } from "@/components/shell/app-shell";

/**
 * Route group for every signed-in page. The parentheses keep it out of the URL,
 * so `/` `/employees` `/bands` all render inside the shell while the /dev/*
 * audit surfaces stay outside it.
 */
export default function AppLayout({ children }: { children: ReactNode }) {
  return <AppShell>{children}</AppShell>;
}
