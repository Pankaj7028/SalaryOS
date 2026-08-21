import type { Metadata } from "next";
import { cookies } from "next/headers";
import { IBM_Plex_Mono, IBM_Plex_Sans } from "next/font/google";
import { QueryProvider } from "@/components/providers/query-provider";
import { Toaster } from "@/components/ui/sonner";
import { THEME_COOKIE, parseTheme, themeClass } from "@/lib/theme";
import "./globals.css";

/**
 * IBM Plex Sans for the interface, IBM Plex Mono for every number
 * (docs/salary-management-ui.md §3). In a compensation tool the numerals are the
 * display type: money, dates, employee numbers and percentages are all mono and
 * tabular, so columns of figures align on the decimal without extra work.
 */
const plexSans = IBM_Plex_Sans({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-plex-sans",
  display: "swap",
});

const plexMono = IBM_Plex_Mono({
  subsets: ["latin"],
  weight: ["400", "500"],
  variable: "--font-plex-mono",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Salary OS",
  description: "Compensation management for ACME.",
};

/**
 * Runs before first paint. Two jobs, both about avoiding a visible flash:
 *  - sidebar width, from localStorage, so the rail never renders expanded then snaps shut;
 *  - theme, but ONLY when the reader is on "system" — an explicit Light/Dark choice is already
 *    on the <html> tag below, server-rendered, and this must not fight it.
 *
 * Kept tiny and dependency-free: it is inlined into every document.
 */
const PRE_PAINT = `
try{var s=localStorage.getItem("sos.sidebar");document.documentElement.dataset.sidebar=s==="collapsed"?"collapsed":"expanded"}catch(e){document.documentElement.dataset.sidebar="expanded"}
try{var r=document.documentElement;if(!r.classList.contains("app-light")&&!r.classList.contains("app-dark")){r.classList.add(matchMedia("(prefers-color-scheme: dark)").matches?"app-dark":"app-light")}}catch(e){document.documentElement.classList.add("app-light")}
`.trim();

export default async function RootLayout({ children }: LayoutProps<"/">) {
  const theme = parseTheme((await cookies()).get(THEME_COOKIE)?.value);
  const cls = themeClass(theme);

  return (
    <html
      lang="en"
      suppressHydrationWarning
      data-theme={theme}
      className={`${cls ?? ""} ${plexSans.variable} ${plexMono.variable} h-full antialiased`}
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: PRE_PAINT }} />
      </head>
      <body className="flex min-h-full flex-col">
        <QueryProvider>{children}</QueryProvider>
        {/* The ONE toast host. A second one silently shadows this and nothing
            appears and nothing errors (CLAUDE.md §9). notify.test.ts enforces it. */}
        <Toaster position="bottom-right" closeButton />
      </body>
    </html>
  );
}
