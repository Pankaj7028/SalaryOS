import type { ReactNode } from "react";
import { Sidebar } from "./sidebar";
import { Topbar } from "./topbar";

/** The frame every signed-in page renders inside (§6). */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="app-shell">
      {/* async server component — reads the theme cookie */}
      <Topbar />
      <div className="app-body">
        <Sidebar />
        <main className="app-content">{children}</main>
      </div>
    </div>
  );
}
