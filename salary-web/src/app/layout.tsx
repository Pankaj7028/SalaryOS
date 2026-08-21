import type { Metadata } from "next";
import { IBM_Plex_Mono, IBM_Plex_Sans } from "next/font/google";
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
 * Runs before first paint so the sidebar is already the right width when the page
 * appears. Without it the rail renders expanded and snaps closed a frame later,
 * and the width would have to be reconciled during hydration.
 *
 * Kept tiny and dependency-free on purpose — it is inlined into every document.
 */
const SIDEBAR_INIT = `try{var s=localStorage.getItem("sos.sidebar");document.documentElement.dataset.sidebar=s==="collapsed"?"collapsed":"expanded"}catch(e){document.documentElement.dataset.sidebar="expanded"}`;

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={`app-light ${plexSans.variable} ${plexMono.variable} h-full antialiased`}
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: SIDEBAR_INIT }} />
      </head>
      <body className="flex min-h-full flex-col">{children}</body>
    </html>
  );
}
