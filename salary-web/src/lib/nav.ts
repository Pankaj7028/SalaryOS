import type { LucideIcon } from "lucide-react";
import {
  Building2,
  ClipboardList,
  Coins,
  FileBarChart,
  LayoutDashboard,
  Layers3,
  ScrollText,
  Scale,
  Ruler,
  Upload,
  Users,
  UsersRound,
} from "lucide-react";

/**
 * The four sidebar groups from docs/salary-management-ui.md §6.
 *
 * Structure only. Role filtering arrives at P3.5 via NAV_VISIBILITY in
 * src/lib/auth/roles.ts — and note that hiding a nav item is NOT access control
 * (CLAUDE.md §7): the boundary is the @PreAuthorize on the controller.
 */
export type NavItem = {
  label: string;
  href: string;
  icon: LucideIcon;
};

export type NavGroup = {
  caption: string;
  items: NavItem[];
};

export const NAV_GROUPS: NavGroup[] = [
  {
    caption: "Workspace",
    items: [
      { label: "Overview", href: "/", icon: LayoutDashboard },
      { label: "Employees", href: "/employees", icon: Users },
      { label: "Changes", href: "/changes", icon: ClipboardList },
    ],
  },
  {
    caption: "Pay structure",
    items: [
      { label: "Salary bands", href: "/bands", icon: Layers3 },
      { label: "Job levels", href: "/levels", icon: Ruler },
      { label: "Locations", href: "/locations", icon: Building2 },
    ],
  },
  {
    caption: "Insights",
    items: [
      { label: "Pay analysis", href: "/insights/pay", icon: FileBarChart },
      { label: "Equity", href: "/insights/equity", icon: Scale },
      { label: "Reports", href: "/insights/reports", icon: ScrollText },
    ],
  },
  {
    caption: "Admin",
    items: [
      { label: "Users", href: "/admin/users", icon: UsersRound },
      { label: "Import", href: "/admin/import", icon: Upload },
      { label: "FX rates", href: "/admin/fx-rates", icon: Coins },
      { label: "Audit log", href: "/admin/audit", icon: ScrollText },
    ],
  },
];

/** True when `href` is the section the current path sits in. */
export function isActive(pathname: string, href: string): boolean {
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}
