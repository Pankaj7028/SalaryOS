import { Suspense } from "react";
import { cookies } from "next/headers";
import type { CurrentUser } from "@/lib/auth/current-user";
import { THEME_COOKIE, parseTheme } from "@/lib/theme";
import { Brand } from "./brand";
import { CommandPalette } from "./command-palette";
import { CurrencyToggle } from "./currency-toggle";
import { MobileNav } from "./mobile-nav";
import { ThemeMenu } from "./theme-menu";
import { UserMenu } from "./user-menu";

/**
 * Topbar (§6.1). Right cluster in the order the doc specifies: search, currency,
 * theme, avatar.
 *
 * The theme is read here on the server so the menu opens already showing the
 * current choice, matching the class the server put on <html>.
 */
export async function Topbar({ user }: { user: CurrentUser }) {
  const theme = parseTheme((await cookies()).get(THEME_COOKIE)?.value);

  return (
    <header className="app-topbar flex items-center gap-3 px-4">
      <MobileNav role={user.role} />
      <Brand />

      <div className="flex-1" />

      <div className="flex items-center gap-2">
        <CommandPalette />
        <Suspense fallback={<div className="h-8 w-[104px]" />}>
          <CurrencyToggle />
        </Suspense>
        <ThemeMenu initial={theme} />
        <UserMenu user={user} />
      </div>
    </header>
  );
}
