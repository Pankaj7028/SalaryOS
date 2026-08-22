"use client";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/components/feedback/states";
import { QuestionCard } from "@/components/insights/question-card";
import { useHeadcount } from "@/lib/api/analytics-queries";
import { useSession } from "@/lib/auth/auth-queries";
import { canAccess } from "@/lib/auth/roles";
import { employeesExportUrl } from "@/lib/api/employees";
import { auditExportUrl } from "@/lib/api/audit";
import type { HeadcountGroup } from "@/lib/api/analytics";

function GroupTable({ title, rows }: { title: string; rows: HeadcountGroup[] }) {
  return (
    <div className="space-y-2">
      <h3 className="type-subsection">{title}</h3>
      <div className="border-border overflow-x-auto rounded-lg border">
        <Table>
          <TableHeader className="bg-muted/40">
            <TableRow className="h-9">
              <TableHead className="type-label text-muted-foreground">Group</TableHead>
              <TableHead className="type-label text-muted-foreground">Headcount</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.key} className="h-9">
                <TableCell className="type-body-sm">{row.label}</TableCell>
                <TableCell className="figure-sm">{row.headcount}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}

/**
 * `/insights/reports` (ui doc §6's nav shell). The seven FR-6 questions already live on Pay
 * analysis and Equity (§8.7/§8.8) — this screen is where the headcount breakdown that backs the
 * Overview stat card gets its full view, plus quick links to the two CSV exports that exist
 * (employees, audit log). Not a saved-report builder — v1 deliberately ships a saved-question
 * library instead of free-form reporting (requirements-one-pager.md's exclusions list).
 */
export function ReportsScreen() {
  const headcount = useHeadcount();
  const session = useSession();
  const canReadAudit = session.data ? canAccess(session.data.role, "/admin/audit") : false;

  return (
    <div className="space-y-4">
      <header>
        <h1 className="type-title">Reports</h1>
        <p className="type-caption text-muted-foreground mt-1">
          Headcount breakdown, and exports of the underlying data.
        </p>
      </header>

      <QuestionCard
        question="How is headcount distributed?"
        headline={headcount.data ? <span>{headcount.data.population.headcount}</span> : "—"}
        defaultExpanded
      >
        {headcount.isLoading ? (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-40 w-full" />
            ))}
          </div>
        ) : headcount.isError || !headcount.data ? (
          <ErrorState
            title="Couldn't load headcount"
            detail="Check your connection and try again."
            action={<Button size="sm" variant="outline" onClick={() => headcount.refetch()}>Retry</Button>}
          />
        ) : (
          <>
            <p className="type-caption text-muted-foreground">
              As at {headcount.data.asAtDate} · {headcount.data.population.headcount} employees
              {headcount.data.population.excluded.terminated
                ? ` · ${headcount.data.population.excluded.terminated} terminated excluded`
                : ""}
            </p>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <GroupTable title="By country" rows={headcount.data.byCountry} />
              <GroupTable title="By department" rows={headcount.data.byDepartment} />
              <GroupTable title="By level" rows={headcount.data.byLevel} />
              <GroupTable title="By status" rows={headcount.data.byStatus} />
            </div>
          </>
        )}
      </QuestionCard>

      <Card>
        <CardHeader>
          <CardTitle className="type-section">Exports</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" asChild>
            <a href={employeesExportUrl({})}>Employees CSV</a>
          </Button>
          {canReadAudit ? (
            <Button size="sm" variant="outline" asChild>
              <a href={auditExportUrl({})}>Audit log CSV</a>
            </Button>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
