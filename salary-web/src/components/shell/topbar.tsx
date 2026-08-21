import { Brand } from "./brand";
import { MobileNav } from "./mobile-nav";

/**
 * Topbar (§6.1). The right cluster — ⌘K search, currency toggle, theme menu and
 * avatar — is built at P3.4; the slots below hold its shape so the shell can be
 * checked at 375px now.
 */
export function Topbar() {
  return (
    <header className="app-topbar flex items-center gap-3 px-4">
      <MobileNav />
      <Brand />

      <div className="flex-1" />

      <div aria-hidden className="flex items-center gap-2">
        <div className="border-border text-muted-foreground type-body-sm hidden h-8 w-[320px] items-center justify-between rounded-md border px-3 lg:flex">
          <span>Search employees</span>
          <kbd className="figure-sm text-muted-foreground">⌘K</kbd>
        </div>
        <div className="border-border text-muted-foreground type-body-sm flex h-8 items-center rounded-md border px-3">
          USD
        </div>
        <div className="bg-muted size-8 rounded-full" />
      </div>
    </header>
  );
}
