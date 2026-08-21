"use client";

import { PanelLeftClose } from "lucide-react";

export const SIDEBAR_STORAGE_KEY = "sos.sidebar";

/**
 * Collapse control. It writes the `data-sidebar` attribute on <html> and mirrors
 * it to localStorage; the width itself is CSS (chrome.css).
 *
 * Deliberately holds no React state. The attribute is set before first paint by
 * the inline script in the root layout, so state here would only duplicate what
 * the DOM already knows — and would reintroduce the hydration mismatch and the
 * flash of a wrong-width rail that the attribute exists to prevent.
 */
export function SidebarToggle() {
  function toggle() {
    const root = document.documentElement;
    const next = root.dataset.sidebar === "collapsed" ? "expanded" : "collapsed";
    root.dataset.sidebar = next;
    try {
      localStorage.setItem(SIDEBAR_STORAGE_KEY, next);
    } catch {
      // Private mode or a full quota: the rail still toggles, it just will not persist.
    }
  }

  return (
    <button
      type="button"
      onClick={toggle}
      className="type-body-sm text-sidebar-foreground hover:bg-accent hover:text-accent-foreground focus-visible:ring-ring mx-2 flex items-center gap-3 rounded-md px-2 py-2 outline-none focus-visible:ring-2"
    >
      <PanelLeftClose aria-hidden className="collapse-icon size-4 shrink-0 transition-transform" />
      <span className="nav-label">Collapse</span>
    </button>
  );
}
