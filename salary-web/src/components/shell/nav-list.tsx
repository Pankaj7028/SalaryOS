"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { NAV_GROUPS, isActive } from "@/lib/nav";
import { cn } from "@/lib/utils";

/**
 * The sidebar's groups and items. Client-side only because it reads the current
 * pathname to mark the active item.
 *
 * An empty group disappears rather than rendering a header with nothing under it
 * (§6.2) — that matters once P3.5 filters items by role.
 */
export function NavList({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();

  return (
    <nav aria-label="Main" className="flex flex-col gap-6 py-4">
      {NAV_GROUPS.filter((group) => group.items.length > 0).map((group) => (
        <div key={group.caption} className="flex flex-col gap-1">
          <p className="type-label text-muted-foreground nav-caption px-4 pb-1 transition-opacity">
            <span className="nav-label inline-block">{group.caption}</span>
          </p>

          {group.items.map((item) => {
            const active = isActive(pathname, item.href);
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={onNavigate}
                aria-current={active ? "page" : undefined}
                title={item.label}
                className={cn(
                  "type-body-sm mx-2 flex items-center gap-3 rounded-md px-2 py-2",
                  "focus-visible:ring-ring outline-none focus-visible:ring-2",
                  active
                    ? "bg-primary-subtle text-primary border-primary border-l-2 font-medium"
                    : "text-sidebar-foreground hover:bg-accent hover:text-accent-foreground border-l-2 border-transparent",
                )}
              >
                <Icon aria-hidden className="size-4 shrink-0" />
                <span className="nav-label">{item.label}</span>
              </Link>
            );
          })}
        </div>
      ))}
    </nav>
  );
}
