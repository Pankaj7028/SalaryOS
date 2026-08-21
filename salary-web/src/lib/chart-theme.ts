"use client";

import { useEffect, useState } from "react";

/**
 * ui doc §7.6: chart colours come from `--chart-1…6`, read via `getComputedStyle` — never
 * hard-coded, or the chart is the one element that ignores the theme. Re-reads whenever `<html>`'s
 * class changes (the theme toggle adds/removes `.app-light`/`.app-dark`, §5.3), so a chart already
 * on screen recolours immediately instead of needing a reload.
 */
const CHART_VARS = ["--chart-1", "--chart-2", "--chart-3", "--chart-4", "--chart-5", "--chart-6"] as const;

export type ChartTheme = {
  series: string[];
  border: string;
  mutedForeground: string;
};

const FALLBACK: ChartTheme = {
  series: ["#0b6e4f", "#0e7490", "#4338ca", "#86198f", "#9a3412", "#4d7c0f"],
  border: "#e4e4e7",
  mutedForeground: "#71717a",
};

export function useChartTheme(): ChartTheme {
  const [theme, setTheme] = useState<ChartTheme>(FALLBACK);

  useEffect(() => {
    function read() {
      const styles = getComputedStyle(document.documentElement);
      const series = CHART_VARS.map((v) => styles.getPropertyValue(v).trim()).filter(Boolean);
      const border = styles.getPropertyValue("--border").trim();
      const mutedForeground = styles.getPropertyValue("--muted-foreground").trim();
      setTheme({
        series: series.length === CHART_VARS.length ? series : FALLBACK.series,
        border: border || FALLBACK.border,
        mutedForeground: mutedForeground || FALLBACK.mutedForeground,
      });
    }
    read();
    const observer = new MutationObserver(read);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
    return () => observer.disconnect();
  }, []);

  return theme;
}
