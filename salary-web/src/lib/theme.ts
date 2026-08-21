/**
 * Theme selection (docs/salary-management-ui.md §6.1, CLAUDE.md §5.3).
 *
 * The choice is stored in a cookie rather than localStorage so the *server* can
 * render the right theme class into the HTML. That is what makes an explicit
 * Light/Dark choice arrive with no flash: the correct colours are in the first
 * byte of the document, not applied by script afterwards.
 *
 * "System" is the default, and is the one case the server cannot resolve — it
 * depends on the reader's OS. There, and only there, a pre-paint inline script
 * settles it from `prefers-color-scheme` before anything is drawn.
 */
export const THEME_COOKIE = "sos.theme";

export type Theme = "light" | "dark" | "system";

export function parseTheme(value: string | undefined): Theme {
  return value === "light" || value === "dark" ? value : "system";
}

/** The class to put on <html>, or null when the client must decide. */
export function themeClass(theme: Theme): "app-light" | "app-dark" | null {
  if (theme === "light") return "app-light";
  if (theme === "dark") return "app-dark";
  return null;
}
