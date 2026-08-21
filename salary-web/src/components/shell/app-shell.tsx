import type { ReactNode } from "react";
import { redirect } from "next/navigation";
import { getCurrentUser } from "@/lib/auth/current-user";
import { Sidebar } from "./sidebar";
import { Topbar } from "./topbar";

/**
 * The frame every signed-in page renders inside (§6).
 *
 * The role is resolved once here and threaded down, so the sidebar and the
 * topbar cannot disagree about who is signed in.
 */
export async function AppShell({ children }: { children: ReactNode }) {
  const user = await getCurrentUser();
  // proxy.ts already redirects an unauthenticated request before it gets
  // here; this only fires if the cookie expired in the gap between that
  // check and this fetch.
  if (!user) {
    redirect("/sign-in");
  }

  return (
    <div className="app-shell">
      <Topbar user={user} />
      <div className="app-body">
        <Sidebar role={user.role} />
        <main className="app-content">{children}</main>
      </div>
    </div>
  );
}
