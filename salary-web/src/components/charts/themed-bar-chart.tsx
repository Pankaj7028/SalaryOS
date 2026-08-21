"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useChartTheme } from "@/lib/chart-theme";

export type BarDatum = { key: string; label: string; value: number; displayValue: string };

/**
 * A single-metric bar chart (ui doc §7.6) — one hue (the data's magnitude, not its identity; the
 * axis labels already name each bar, so a second, redundant identity colour per bar would fight
 * the "colour follows the entity" rule rather than serve it). Colours come from `--chart-1` via
 * {@link useChartTheme}, never hard-coded. Thin bars, 4px rounded tops, recessive grid/axes.
 */
export function ThemedBarChart({ data, height = 240 }: { data: BarDatum[]; height?: number }) {
  const theme = useChartTheme();

  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 8 }}>
        <CartesianGrid stroke={theme.border} vertical={false} />
        <XAxis
          dataKey="label"
          stroke={theme.border}
          tick={{ fill: theme.mutedForeground, fontSize: 12, fontFamily: "var(--font-mono)" }}
          tickLine={false}
        />
        <YAxis
          stroke={theme.border}
          tick={{ fill: theme.mutedForeground, fontSize: 12, fontFamily: "var(--font-mono)" }}
          tickLine={false}
          axisLine={false}
          width={40}
        />
        <Tooltip content={<ChartTooltip />} cursor={{ fill: "var(--muted)" }} />
        <Bar dataKey="value" fill={theme.series[0]} radius={[4, 4, 0, 0]} maxBarSize={48} />
      </BarChart>
    </ResponsiveContainer>
  );
}

function ChartTooltip({ active, payload }: { active?: boolean; payload?: { payload: BarDatum }[] }) {
  if (!active || !payload?.length) return null;
  const datum = payload[0].payload;
  return (
    <div className="border-border bg-card rounded-md border px-3 py-2 shadow-md">
      <p className="type-caption text-muted-foreground">{datum.label}</p>
      <p className="figure-sm">{datum.displayValue}</p>
    </div>
  );
}
