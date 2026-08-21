import type { Role } from "@/lib/auth/roles";
import { NavList } from "./nav-list";
import { SidebarToggle } from "./sidebar-toggle";

/**
 * The static sidebar (§6.2). Hidden under 768px, where MobileNav's Sheet takes
 * over — see chrome.css.
 */
export function Sidebar({ role }: { role: Role }) {
  return (
    <aside className="app-sidebar flex flex-col">
      <div className="min-h-0 flex-1 overflow-x-hidden overflow-y-auto">
        <NavList role={role} />
      </div>
      <div className="border-sidebar-border border-t py-2">
        <SidebarToggle />
      </div>
    </aside>
  );
}
