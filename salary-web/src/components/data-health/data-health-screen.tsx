"use client";

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ErrorState, GoodNewsState, TableSkeleton } from "@/components/feedback/states";
import { useDataHealth } from "@/lib/api/analytics-queries";
import { dataHealthDrillThroughUrl, type DataHealthCheck, type DataHealthSeverity } from "@/lib/api/analytics";
import { downloadCsv } from "@/lib/csv";

/**
 * `/admin/data-health` (P11.2, F11).
 *
 * <p>Salary OS exists to replace spreadsheets, and the day-one job of that migration is finding
 * what the spreadsheets got wrong. Every other screen assumes the data is right and answers a
 * question with it; this one asks whether the data can carry the question at all. It is the screen
 * an HR Manager should open first on their first real day, which is why nothing here is a
 * dashboard tile — each row is a defect, a count, and a way to go and look at the people.
 *
 * <p><b>Passing checks stay on screen.</b> A console that hides them is indistinguishable from one
 * that never ran them, and "we checked for that and it's clean" is most of the value of a health
 * check. They are dimmed, not deleted.
 */
export function DataHealthScreen() {
  const health = useDataHealth();

  if (health.isLoading) {
    return (
      <Shell>
        <TableSkeleton columns={["90px", "100%", "80px", "110px"]} rows={6} />
      </Shell>
    );
  }

  if (health.isError || !health.data) {
    return (
      <Shell>
        <ErrorState
          title="Couldn't load data health"
          detail="Check your connection and try again."
          action={
            <Button size="sm" variant="outline" onClick={() => health.refetch()}>
              Retry
            </Button>
          }
        />
      </Shell>
    );
  }

  const data = health.data;
  // Severity first, then the biggest problem within a severity — the reading order is "what should
  // I fix", never the order the checks happen to be declared in.
  const rank: Record<DataHealthSeverity, number> = { CRITICAL: 0, WARNING: 1, INFO: 2 };
  const failing = [...data.checks]
    .filter((check) => check.count > 0)
    .sort((a, b) => rank[a.severity] - rank[b.severity] || b.count - a.count);
  const passing = data.checks.filter((check) => check.count === 0);

  function exportChecks() {
    downloadCsv(
      `data-health-${data.asAtDate}.csv`,
      ["check", "label", "severity", "count", "explanation", "drillThrough"],
      data.checks.map((check) => [
        check.key,
        check.label,
        check.severity,
        String(check.count),
        check.explanation,
        dataHealthDrillThroughUrl(check),
      ]),
    );
  }

  return (
    <Shell
      asAt={data.asAtDate}
      population={data.totalEmployees}
      action={
        <Button size="sm" variant="outline" onClick={exportChecks}>
          Export
        </Button>
      }
    >
      {failing.length === 0 ? (
        <GoodNewsState
          title="Every check passes"
          population={`${data.checks.length} checks over ${data.totalEmployees.toLocaleString()} employees`}
        />
      ) : (
        <>
          <p className="type-body-sm">
            <span className="figure-sm text-critical">{data.failingChecks}</span> of{" "}
            <span className="figure-sm">{data.checks.length}</span> checks found something.
          </p>
          <CheckTable checks={failing} />
        </>
      )}

      {passing.length > 0 ? (
        <section className="space-y-2">
          <h2 className="type-label text-muted-foreground">
            Passing ({passing.length})
          </h2>
          <ul className="border-border divide-border divide-y rounded-lg border">
            {passing.map((check) => (
              <li key={check.key} className="flex items-baseline gap-3 px-4 py-2.5">
                <span className="figure-sm text-muted-foreground w-10 shrink-0">0</span>
                <span className="type-body-sm text-muted-foreground">{check.label}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </Shell>
  );
}

function Shell({
  children,
  asAt,
  population,
  action,
}: {
  children: React.ReactNode;
  asAt?: string;
  population?: number;
  action?: React.ReactNode;
}) {
  return (
    <div className="space-y-4">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="type-title">Data health</h1>
          <p className="type-caption text-muted-foreground mt-1">
            {asAt
              ? `As at ${asAt} · ${population?.toLocaleString()} employees, terminated excluded`
              : "What the records can't currently answer, and who is affected."}
          </p>
        </div>
        {action}
      </header>
      {children}
    </div>
  );
}

const SEVERITY_LABEL: Record<DataHealthSeverity, string> = {
  CRITICAL: "Critical",
  WARNING: "Warning",
  INFO: "Info",
};

/** CLAUDE.md §5.1 defines exactly two non-neutral tones; INFO gets neither, and says so quietly. */
const SEVERITY_TONE: Record<DataHealthSeverity, string> = {
  CRITICAL: "text-critical",
  WARNING: "text-attention",
  INFO: "text-muted-foreground",
};

function CheckTable({ checks }: { checks: DataHealthCheck[] }) {
  return (
    <>
      <div className="border-border hidden overflow-hidden rounded-lg border md:block">
        <Table>
          <TableHeader className="bg-muted/40">
            <TableRow className="h-10">
              <TableHead className="type-label text-muted-foreground">Severity</TableHead>
              <TableHead className="type-label text-muted-foreground">Check</TableHead>
              <TableHead className="type-label text-muted-foreground">Affected</TableHead>
              <TableHead className="type-label text-muted-foreground sr-only">Drill through</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {checks.map((check) => (
              <TableRow key={check.key} className="h-10">
                <TableCell>
                  <span className={`type-label ${SEVERITY_TONE[check.severity]}`}>
                    {SEVERITY_LABEL[check.severity]}
                  </span>
                </TableCell>
                <TableCell>
                  <span className="type-body-sm block">{check.label}</span>
                  <span className="type-caption text-muted-foreground block">{check.explanation}</span>
                </TableCell>
                <TableCell className="figure-sm">{check.count.toLocaleString()}</TableCell>
                <TableCell className="text-right">
                  <DrillThroughLink check={check} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {/* §12.10: the table becomes cards under 768px rather than scrolling sideways — the
          explanation is the longest column and is the first thing a horizontal scroll hides. */}
      <ul className="flex flex-col gap-3 md:hidden">
        {checks.map((check) => (
          <li key={check.key} className="border-border bg-card flex flex-col gap-2 rounded-lg border p-5">
            <div className="flex items-baseline justify-between gap-3">
              <span className={`type-label ${SEVERITY_TONE[check.severity]}`}>
                {SEVERITY_LABEL[check.severity]}
              </span>
              <span className="figure-sm">{check.count.toLocaleString()}</span>
            </div>
            <span className="type-body-sm">{check.label}</span>
            <span className="type-caption text-muted-foreground">{check.explanation}</span>
            <DrillThroughLink check={check} />
          </li>
        ))}
      </ul>
    </>
  );
}

function DrillThroughLink({ check }: { check: DataHealthCheck }) {
  return (
    <Button asChild size="sm" variant="outline">
      <Link href={dataHealthDrillThroughUrl(check)}>
        See the {check.count === 1 ? "employee" : `${check.count.toLocaleString()} employees`}
      </Link>
    </Button>
  );
}
