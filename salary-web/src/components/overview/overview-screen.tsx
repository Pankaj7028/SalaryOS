"use client";

import Link from "next/link";
import { Money } from "@/components/comp/money";
import { EmptyState, ErrorState, GoodNewsState } from "@/components/feedback/states";
import { ThemedBarChart, type BarDatum } from "@/components/charts/themed-bar-chart";
import { ChartCard } from "@/components/charts/chart-card";
import { StatCard, StatCardSkeleton } from "@/components/overview/stat-card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useCompaRatioDistribution,
  useHeadcount,
  useIncreaseCycle,
  useOutOfBand,
  usePayrollCost,
} from "@/lib/api/analytics-queries";
import { useChanges } from "@/lib/api/changes-queries";
import { CHANGE_REASON_LABEL } from "@/lib/change-reasons";
import { formatAmount, formatCompaRatio, formatPercent } from "@/lib/money";

function yearToDateRange(): { fromDate: string; toDate: string } {
  const now = new Date();
  const fromDate = `${now.getFullYear()}-01-01`;
  const toDate = now.toISOString().slice(0, 10);
  return { fromDate, toDate };
}

/**
 * `/` (ui doc §8.1). Six headline figures, base-pay-by-country and compa-ratio-distribution
 * charts, and the approval queue as a compact table. Every request runs unfiltered — this screen
 * answers "how is the org doing right now," not a drill-down; `/insights/pay` (P7.6's sibling) is
 * where filters live.
 */
export function OverviewScreen() {
  const payrollCost = usePayrollCost();
  const headcount = useHeadcount();
  const compaRatio = useCompaRatioDistribution({});
  const outOfBand = useOutOfBand();
  const pendingChanges = useChanges("PENDING");
  const { fromDate, toDate } = yearToDateRange();
  const increaseCycle = useIncreaseCycle({ fromDate, toDate });

  const loading = payrollCost.isLoading || headcount.isLoading || compaRatio.isLoading || outOfBand.isLoading;
  const anyError = payrollCost.isError || headcount.isError || compaRatio.isError || outOfBand.isError;

  const basisLine = payrollCost.data
    ? `As at ${payrollCost.data.asAtDate} · normalised to ${payrollCost.data.baseCurrency} · ${payrollCost.data.population.headcount} employees`
    : undefined;

  return (
    <div className="space-y-6">
      <header>
        <h1 className="type-title">Overview</h1>
        <p className="type-caption text-muted-foreground mt-1">{basisLine ?? "Loading the current basis…"}</p>
      </header>

      {anyError ? (
        <ErrorState
          title="Couldn't load the overview"
          detail="Check your connection and try again."
        />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-6">
            {loading ? (
              Array.from({ length: 6 }).map((_, i) => <StatCardSkeleton key={i} />)
            ) : (
              <>
                <StatCard
                  label="Total annualised base"
                  value={<Money value={payrollCost.data!.overall.totalAnnualBase} size="figure-xl" whole />}
                  comparison={`${payrollCost.data!.overall.headcount} employees`}
                />
                <StatCard
                  label="Headcount"
                  value={<span>{headcount.data!.population.headcount}</span>}
                  comparison={
                    headcount.data!.population.excluded.terminated
                      ? `${headcount.data!.population.excluded.terminated} terminated excluded`
                      : "active employees"
                  }
                />
                <StatCard
                  label="Median compa-ratio"
                  value={<span>{formatCompaRatio(Number(compaRatio.data!.median))}</span>}
                  comparison={`p25 ${formatCompaRatio(Number(compaRatio.data!.p25))} · p75 ${formatCompaRatio(Number(compaRatio.data!.p75))}`}
                />
                <StatCard
                  label="Outside band"
                  value={<span>{outOfBand.data!.belowMinCount + outOfBand.data!.aboveMaxCount}</span>}
                  comparison={`cost to min ${formatAmount(outOfBand.data!.totalCostToMinimum, { whole: true })} ${outOfBand.data!.totalCostToMinimum.currency}`}
                  href="/insights/pay"
                />
                <StatCard
                  label="Awaiting approval"
                  value={<span>{pendingChanges.data?.length ?? "—"}</span>}
                  comparison="changes"
                  href="/changes"
                />
                <StatCard
                  label="Increase spend YTD"
                  value={
                    increaseCycle.data ? (
                      <Money value={increaseCycle.data.totalIncrease} size="figure-xl" whole />
                    ) : (
                      <span>—</span>
                    )
                  }
                  comparison={increaseCycle.data ? `avg ${formatPercent(Number(increaseCycle.data.avgIncreasePercent) * 100)}` : undefined}
                />
              </>
            )}
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <ChartCard
              title="Base pay by country"
              basisLine={basisLine ?? ""}
              chart={
                payrollCost.data ? (
                  <ThemedBarChart data={toCountryBars(payrollCost.data.byCountry)} />
                ) : (
                  <Skeleton className="h-[240px] w-full" />
                )
              }
              table={<PayrollByCountryTable rows={payrollCost.data?.byCountry ?? []} />}
              csv={{
                filename: "base-pay-by-country.csv",
                headers: ["Country", "Headcount", "Total annual base", "Average annual base"],
                rows: (payrollCost.data?.byCountry ?? []).map((r) => [
                  r.label,
                  String(r.headcount),
                  `${r.totalAnnualBase.amount} ${r.totalAnnualBase.currency}`,
                  `${r.averageAnnualBase.amount} ${r.averageAnnualBase.currency}`,
                ]),
              }}
            />

            <ChartCard
              title="Compa-ratio distribution"
              basisLine={
                compaRatio.data
                  ? `${compaRatio.data.population.headcount} employees with a band · median ${formatCompaRatio(Number(compaRatio.data.median))}`
                  : ""
              }
              chart={
                compaRatio.data ? (
                  <ThemedBarChart
                    data={compaRatio.data.histogram.map((b) => ({
                      key: b.bucket,
                      label: b.bucket,
                      value: b.count,
                      displayValue: `${b.count} employees`,
                    }))}
                  />
                ) : (
                  <Skeleton className="h-[240px] w-full" />
                )
              }
              table={<HistogramTable buckets={compaRatio.data?.histogram ?? []} />}
              csv={{
                filename: "compa-ratio-distribution.csv",
                headers: ["Compa-ratio bucket", "Employees"],
                rows: (compaRatio.data?.histogram ?? []).map((b) => [b.bucket, String(b.count)]),
              }}
            />
          </div>

          <section className="space-y-3">
            <div className="flex items-baseline justify-between">
              <h2 className="type-section">Awaiting approval</h2>
              <Link href="/changes" className="type-caption text-primary hover:underline">
                View all
              </Link>
            </div>
            <ApprovalQueuePreview />
          </section>
        </>
      )}
    </div>
  );
}

function toCountryBars(groups: { key: string; label: string; totalAnnualBase: { amount: string; currency: string } }[]): BarDatum[] {
  return groups.map((g) => ({
    key: g.key,
    label: g.label,
    value: Number(g.totalAnnualBase.amount),
    displayValue: `${formatAmount(g.totalAnnualBase, { whole: true })} ${g.totalAnnualBase.currency}`,
  }));
}

function PayrollByCountryTable({ rows }: { rows: { key: string; label: string; headcount: number; totalAnnualBase: { amount: string; currency: string }; averageAnnualBase: { amount: string; currency: string } }[] }) {
  if (rows.length === 0) {
    return <EmptyState title="No countries yet" detail="Base pay by country appears once employees have current comp records." />;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow className="h-10">
          <TableHead className="type-label text-muted-foreground">Country</TableHead>
          <TableHead className="type-label text-muted-foreground">Headcount</TableHead>
          <TableHead className="type-label text-muted-foreground">Total</TableHead>
          <TableHead className="type-label text-muted-foreground">Average</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((r) => (
          <TableRow key={r.key} className="h-10">
            <TableCell className="type-body-sm">{r.label}</TableCell>
            <TableCell className="figure-sm">{r.headcount}</TableCell>
            <TableCell className="figure-sm">{formatAmount(r.totalAnnualBase, { whole: true })} {r.totalAnnualBase.currency}</TableCell>
            <TableCell className="figure-sm">{formatAmount(r.averageAnnualBase)} {r.averageAnnualBase.currency}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function HistogramTable({ buckets }: { buckets: { bucket: string; count: number }[] }) {
  if (buckets.length === 0) {
    return <EmptyState title="No banded employees yet" detail="The histogram fills in once employees have a band and current comp." />;
  }
  return (
    <Table>
      <TableHeader>
        <TableRow className="h-10">
          <TableHead className="type-label text-muted-foreground">Compa-ratio</TableHead>
          <TableHead className="type-label text-muted-foreground">Employees</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {buckets.map((b) => (
          <TableRow key={b.bucket} className="h-10">
            <TableCell className="figure-sm">{b.bucket}</TableCell>
            <TableCell className="figure-sm">{b.count}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function ApprovalQueuePreview() {
  const pending = useChanges("PENDING");

  if (pending.isLoading) {
    return <Skeleton className="h-40 w-full" />;
  }
  if (pending.isError) {
    return <ErrorState title="Couldn't load the approval queue" detail="Check your connection and try again." />;
  }
  const rows = (pending.data ?? []).slice(0, 5);
  if (rows.length === 0) {
    return <GoodNewsState title="Nothing awaiting approval" population="0 pending changes" />;
  }

  return (
    <div className="border-border overflow-hidden rounded-lg border">
      <Table>
        <TableHeader className="bg-muted/40">
          <TableRow className="h-10">
            <TableHead className="type-label text-muted-foreground">Employee</TableHead>
            <TableHead className="type-label text-muted-foreground">Current → proposed</TableHead>
            <TableHead className="type-label text-muted-foreground">Reason</TableHead>
            <TableHead className="type-label text-muted-foreground">Effective</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((change) => (
            <TableRow key={change.id} className="h-10">
              <TableCell className="type-body-sm">
                {change.employeeFirstName} {change.employeeLastName}
              </TableCell>
              <TableCell className="figure-sm">
                <Money value={change.currentBase} size="figure-sm" showCurrency={false} /> {"→ "}
                <Money value={change.newBase} size="figure-sm" />
              </TableCell>
              <TableCell className="type-body-sm">{CHANGE_REASON_LABEL[change.changeReason] ?? change.changeReason}</TableCell>
              <TableCell className="figure-sm">{change.effectiveDate}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
