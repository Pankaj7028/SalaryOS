"use client";

import { useState } from "react";
import { Menu } from "lucide-react";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import type { Role } from "@/lib/auth/roles";
import { NavList } from "./nav-list";

/**
 * Under 768px the sidebar becomes a Sheet opened by the topbar hamburger (§6.2).
 * The trigger is hidden at ≥768px, where the static rail is used instead.
 *
 * Closing on navigation is why NavList takes onNavigate — otherwise the sheet
 * stays open over the page you just asked for.
 */
export function MobileNav({ role }: { role: Role }) {
  const [open, setOpen] = useState(false);

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <button
          type="button"
          aria-label="Open navigation"
          className="hover:bg-accent focus-visible:ring-ring -ml-1 rounded-md p-2 outline-none focus-visible:ring-2 md:hidden"
        >
          <Menu aria-hidden className="size-5" />
        </button>
      </SheetTrigger>
      <SheetContent side="left" className="bg-sidebar w-[280px] p-0">
        <SheetHeader className="border-sidebar-border border-b px-4 py-3">
          <SheetTitle className="type-subsection text-left">Salary OS</SheetTitle>
        </SheetHeader>
        <div className="overflow-y-auto">
          <NavList role={role} onNavigate={() => setOpen(false)} />
        </div>
      </SheetContent>
    </Sheet>
  );
}
