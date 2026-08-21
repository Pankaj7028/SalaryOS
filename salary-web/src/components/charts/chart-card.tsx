"use client";

import { useState, type ReactNode } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { downloadCsv } from "@/lib/csv";

export type ChartCsvExport = { filename: string; headers: string[]; rows: string[][] };

/**
 * ui doc §7.6/§8.7: every chart card carries a basis line under the title, a "View as table"
 * toggle, and — "the table is the evidence, and the evidence is what gets exported" — a CSV
 * export of that same table, built client-side from data already in memory (no backend
 * round-trip; nothing here computes a new figure, only serialises the server's own).
 */
export function ChartCard({
  title,
  basisLine,
  chart,
  table,
  csv,
  action,
}: {
  title: string;
  basisLine: string;
  chart: ReactNode;
  table: ReactNode;
  csv?: ChartCsvExport;
  action?: ReactNode;
}) {
  const [view, setView] = useState<"chart" | "table">("chart");

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div>
          <CardTitle className="type-section">{title}</CardTitle>
          <CardDescription className="type-caption mt-0.5">{basisLine}</CardDescription>
        </div>
        <div className="flex items-center gap-2">
          {action}
          {csv && csv.rows.length > 0 ? (
            <Button size="sm" variant="outline" onClick={() => downloadCsv(csv.filename, csv.headers, csv.rows)}>
              Export CSV
            </Button>
          ) : null}
          <div className="flex gap-1">
            <Button size="sm" variant={view === "chart" ? "default" : "outline"} onClick={() => setView("chart")}>
              Chart
            </Button>
            <Button size="sm" variant={view === "table" ? "default" : "outline"} onClick={() => setView("table")}>
              Table
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>{view === "chart" ? chart : table}</CardContent>
    </Card>
  );
}
