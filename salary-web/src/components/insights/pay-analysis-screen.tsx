"use client";

import { useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Money } from "@/components/comp/money";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EmptyState, ErrorState, GoodNewsState, TableSkeleton } from "@/components/feedback/states";
import { ChartCard } from "@/components/charts/chart-card";
import { ThemedBarChart, type BarDatum } from "@/components/charts/themed-bar-chart";
import { QuestionCard } from "@/components/insights/question-card";
import type { AnalyticsBasis } from "@/lib/api/analytics";
import {
  useCompaRatioDistribution,
  useIncreaseCycle,
  useOutOfBand,
  usePayrollCost,
} from "@/lib/api/analytics-queries";
import { useCountries, useDepartments, useJobLevels } from "@/lib/api/reference-queries";
import { CHANGE_REASON_LABEL } from "@/lib/change-reasons";
import { formatAmount, formatCompaRatio, formatPercent } from "@/lib/money";

const ALL = "__all__";

/**
 * `/insights/pay` (ui doc §8.7). The saved-question library: FR-6.1, 6.2, 6.3, 6.5 as expandable
 * cards, each with its own basis line and "View as table" toggle (§7.6). FR-6.4 (pay-gap) lives on
 * the separate Equity review screen (§8.8, P7.7) — a deliberately different surface, since its
 * demographic framing shouldn't sit beside the org-wide cost/distribution questions here.
 */
export function PayAnalysisScreen() {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="type-title">Pay analysis</h1>
        <p className="type-caption text-muted-foreground mt-1">
          The saved-question library. Expand a question for its full view and filters.
        </p>
      </header>

      <div className="space-y-4">
        <PayrollCostQuestion />
        <OutOfBandQuestion />
        <CompaRatioQuestion />
        <IncreaseCycleQuestion />
      </div>
    </div>
  );
}

// ---- FR-6.1 -------------------------------------------------------------------------------

const BASIS_LABEL: Record<AnalyticsBasis, string> = {
  BASE: "Base pay",
  TOTAL_TARGET_CASH: "Total target cash",
};

function PayrollCostQuestion() {
  // P10.7 / CLAUDE.md §9: both the basis and the breakdown live in the URL, not in component
  // state. "What do we spend?" has two legitimate answers and they differ by millions — a link to
  // this screen has to say which one the sender was looking at, or the recipient reads a different
  // number under the same question and neither of them knows.
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const basis: AnalyticsBasis =
    searchParams.get("basis") === "TOTAL_TARGET_CASH" ? "TOTAL_TARGET_CASH" : "BASE";
  const breakdown = (
    ["byCountry", "byDepartment", "byLevel"] as const
  ).find((b) => b === searchParams.get("breakdown")) ?? "byCountry";

  const payrollCost = usePayrollCost(basis);

  function setParam(key: string, value: string, isDefault: boolean) {
    const params = new URLSearchParams(searchParams.toString());
    if (isDefault) params.delete(key);
    else params.set(key, value);
    const query = params.toString();
    router.push(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }

  if (payrollCost.isLoading) {
    return (
      <QuestionCard question="What do we spend on base pay?" headline={<TableSkeleton columns={["120px"]} rows={1} />}>
        <TableSkeleton columns={["100%"]} rows={4} />
      </QuestionCard>
    );
  }
  if (payrollCost.isError || !payrollCost.data) {
    return (
      <QuestionCard question="What do we spend on base pay?" headline="—">
        <ErrorState title="Couldn't load payroll cost" detail="Check your connection and try again." />
      </QuestionCard>
    );
  }

  const data = payrollCost.data;
  const rows = data[breakdown];
  // The response states its own basis (FR-6.8) rather than the screen asserting it — if the two
  // ever disagree, the figure on screen is the one that is wrong, and this is where it shows.
  const basisLine = `As at ${data.asAtDate} · ${BASIS_LABEL[data.basis].toLowerCase()} · normalised to ${data.baseCurrency} · ${data.population.headcount} employees · ${data.population.excluded.terminated ?? 0} terminated excluded`;

  return (
    <QuestionCard
      question={
        data.basis === "TOTAL_TARGET_CASH"
          ? "What do we spend on total target cash?"
          : "What do we spend on base pay?"
      }
      headline={<Money value={data.overall.total} size="figure-lg" whole />}
    >
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <div className="flex items-center gap-2">
          <Label className="type-caption text-muted-foreground">Counting</Label>
          <Select
            value={basis}
            onValueChange={(v) => setParam("basis", v, v === "BASE")}
          >
            <SelectTrigger size="sm" className="w-44" aria-label="Payroll cost basis">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="BASE">{BASIS_LABEL.BASE}</SelectItem>
              <SelectItem value="TOTAL_TARGET_CASH">{BASIS_LABEL.TOTAL_TARGET_CASH}</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="flex items-center gap-2">
          <Label className="type-caption text-muted-foreground">Break down by</Label>
          <Select
            value={breakdown}
            onValueChange={(v) => setParam("breakdown", v, v === "byCountry")}
          >
            <SelectTrigger size="sm" className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="byCountry">Country</SelectItem>
              <SelectItem value="byDepartment">Department</SelectItem>
              <SelectItem value="byLevel">Level</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
      {data.basis === "TOTAL_TARGET_CASH" ? (
        <p className="type-caption text-muted-foreground">
          Base pay plus recurring components, each converted at the rate pinned to that employee&rsquo;s
          own record. One-off payments are not counted — they are not part of what someone is paid
          annually, and including them would make two runs of this report differ as they age out.
        </p>
      ) : null}
      <ChartCard
        title={data.basis === "TOTAL_TARGET_CASH" ? "Total target cash" : "Total annualised base"}
        basisLine={basisLine}
        chart={
          <ThemedBarChart
            data={rows.map((r) => ({
              key: r.key,
              label: r.label,
              value: Number(r.total.amount),
              displayValue: `${formatAmount(r.total, { whole: true })} ${r.total.currency}`,
            }))}
          />
        }
        table={
          rows.length === 0 ? (
            <EmptyState title="Nothing to show" detail="No employees have current comp yet." />
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="h-10">
                  <TableHead className="type-label text-muted-foreground">Group</TableHead>
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
                    <TableCell className="figure-sm">{formatAmount(r.total, { whole: true })} {r.total.currency}</TableCell>
                    <TableCell className="figure-sm">{formatAmount(r.average)} {r.average.currency}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )
        }
        csv={{
          filename: `payroll-cost-${breakdown}.csv`,
          headers: ["Group", "Headcount", "Total annual base", "Average annual base"],
          rows: rows.map((r) => [
            r.label,
            String(r.headcount),
            `${r.total.amount} ${r.total.currency}`,
            `${r.average.amount} ${r.average.currency}`,
          ]),
        }}
      />
    </QuestionCard>
  );
}

// ---- FR-6.2 -------------------------------------------------------------------------------

function OutOfBandQuestion() {
  const outOfBand = useOutOfBand();

  if (outOfBand.isLoading) {
    return (
      <QuestionCard question="Who is paid outside their band?" headline={<TableSkeleton columns={["80px"]} rows={1} />}>
        <TableSkeleton columns={["100%"]} rows={4} />
      </QuestionCard>
    );
  }
  if (outOfBand.isError || !outOfBand.data) {
    return (
      <QuestionCard question="Who is paid outside their band?" headline="—">
        <ErrorState title="Couldn't load out-of-band pay" detail="Check your connection and try again." />
      </QuestionCard>
    );
  }

  const data = outOfBand.data;
  const total = data.belowMinCount + data.aboveMaxCount;
  const basisLine = `As at ${data.asAtDate} · normalised to ${data.baseCurrency} · ${data.population.headcount} employees`;
  const chartData: BarDatum[] = [
    { key: "below", label: "Below min", value: data.belowMinCount, displayValue: `${data.belowMinCount} employees` },
    { key: "above", label: "Above max", value: data.aboveMaxCount, displayValue: `${data.aboveMaxCount} employees` },
  ];

  return (
    <QuestionCard question="Who is paid outside their band?" headline={<span>{total}</span>}>
      <ChartCard
        title="Below minimum vs. above maximum"
        basisLine={`${basisLine} · cost to bring everyone to minimum: ${formatAmount(data.totalCostToMinimum, { whole: true })} ${data.totalCostToMinimum.currency}`}
        chart={<ThemedBarChart data={chartData} height={160} />}
        table={
          data.rows.length === 0 ? (
            <GoodNewsState title="Everyone is inside their band" population={`${data.population.headcount} employees checked`} />
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="h-10">
                  <TableHead className="type-label text-muted-foreground">Employee</TableHead>
                  <TableHead className="type-label text-muted-foreground">Status</TableHead>
                  <TableHead className="type-label text-muted-foreground">Current</TableHead>
                  <TableHead className="type-label text-muted-foreground">Gap</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.rows.map((r) => (
                  <TableRow key={r.employeeId} className="h-10">
                    <TableCell className="type-body-sm">
                      {r.employeeFirstName} {r.employeeLastName}
                      <span className="text-muted-foreground"> · {r.employeeNumber}</span>
                    </TableCell>
                    <TableCell className="type-body-sm">{r.bandStatus === "BELOW_MIN" ? "Below min" : "Above max"}</TableCell>
                    <TableCell className="figure-sm">{formatAmount(r.currentBase)} {r.currentBase.currency}</TableCell>
                    <TableCell className="figure-sm">{formatAmount(r.gapAmount)} {r.gapAmount.currency}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )
        }
        csv={{
          filename: "out-of-band.csv",
          headers: ["Employee", "Employee number", "Status", "Current base", "Gap"],
          rows: data.rows.map((r) => [
            `${r.employeeFirstName} ${r.employeeLastName}`,
            r.employeeNumber,
            r.bandStatus === "BELOW_MIN" ? "Below min" : "Above max",
            `${r.currentBase.amount} ${r.currentBase.currency}`,
            `${r.gapAmount.amount} ${r.gapAmount.currency}`,
          ]),
        }}
      />
    </QuestionCard>
  );
}

// ---- FR-6.3 -------------------------------------------------------------------------------

function CompaRatioQuestion() {
  const [departmentId, setDepartmentId] = useState<string>(ALL);
  const [jobLevelId, setJobLevelId] = useState<string>(ALL);
  const [countryCode, setCountryCode] = useState<string>(ALL);
  const departments = useDepartments();
  const jobLevels = useJobLevels();
  const countries = useCountries();

  const compaRatio = useCompaRatioDistribution({
    departmentId: departmentId === ALL ? undefined : departmentId,
    jobLevelId: jobLevelId === ALL ? undefined : jobLevelId,
    countryCode: countryCode === ALL ? undefined : countryCode,
  });

  const headline = compaRatio.data ? formatCompaRatio(Number(compaRatio.data.median)) : "—";

  return (
    <QuestionCard question="What is the compa-ratio distribution?" headline={<span>{headline}</span>}>
      <div className="flex flex-wrap items-center gap-2">
        <Select value={departmentId} onValueChange={setDepartmentId}>
          <SelectTrigger size="sm" className="w-44">
            <SelectValue placeholder="Department" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All departments</SelectItem>
            {(departments.data ?? []).map((d) => (
              <SelectItem key={d.id} value={d.id}>{d.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={jobLevelId} onValueChange={setJobLevelId}>
          <SelectTrigger size="sm" className="w-40">
            <SelectValue placeholder="Level" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All levels</SelectItem>
            {(jobLevels.data ?? []).map((l) => (
              <SelectItem key={l.id} value={l.id}>{l.title}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={countryCode} onValueChange={setCountryCode}>
          <SelectTrigger size="sm" className="w-40">
            <SelectValue placeholder="Country" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All countries</SelectItem>
            {(countries.data ?? []).map((c) => (
              <SelectItem key={c.code} value={c.code}>{c.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {compaRatio.isLoading ? (
        <TableSkeleton columns={["100%"]} rows={4} />
      ) : compaRatio.isError || !compaRatio.data ? (
        <ErrorState title="Couldn't load the compa-ratio distribution" detail="Check your connection and try again." />
      ) : (
        <ChartCard
          title="Compa-ratio histogram"
          basisLine={`As at ${compaRatio.data.asAtDate} · ${compaRatio.data.population.headcount} employees with a band · p25 ${formatCompaRatio(Number(compaRatio.data.p25))} · median ${formatCompaRatio(Number(compaRatio.data.median))} · p75 ${formatCompaRatio(Number(compaRatio.data.p75))}`}
          chart={
            <ThemedBarChart
              data={compaRatio.data.histogram.map((b) => ({
                key: b.bucket,
                label: b.bucket,
                value: b.count,
                displayValue: `${b.count} employees`,
              }))}
            />
          }
          table={
            compaRatio.data.histogram.length === 0 ? (
              <EmptyState title="No banded employees match these filters" detail="Widen the department, level, or country filter." />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow className="h-10">
                    <TableHead className="type-label text-muted-foreground">Compa-ratio</TableHead>
                    <TableHead className="type-label text-muted-foreground">Employees</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {compaRatio.data.histogram.map((b) => (
                    <TableRow key={b.bucket} className="h-10">
                      <TableCell className="figure-sm">{b.bucket}</TableCell>
                      <TableCell className="figure-sm">{b.count}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )
          }
          csv={{
            filename: "compa-ratio-distribution.csv",
            headers: ["Compa-ratio bucket", "Employees"],
            rows: compaRatio.data.histogram.map((b) => [b.bucket, String(b.count)]),
          }}
        />
      )}
    </QuestionCard>
  );
}

// ---- FR-6.5 -------------------------------------------------------------------------------

function defaultCycleRange() {
  const now = new Date();
  return { fromDate: `${now.getFullYear()}-01-01`, toDate: now.toISOString().slice(0, 10) };
}

function IncreaseCycleQuestion() {
  const initial = defaultCycleRange();
  const [fromDate, setFromDate] = useState(initial.fromDate);
  const [toDate, setToDate] = useState(initial.toDate);
  const [budgetInput, setBudgetInput] = useState("");

  const increaseCycle = useIncreaseCycle({
    fromDate,
    toDate,
    budget: budgetInput.trim() && Number(budgetInput) > 0 ? budgetInput : undefined,
  });

  const headline = increaseCycle.data ? formatAmount(increaseCycle.data.totalIncrease, { whole: true }) : "—";

  return (
    <QuestionCard question="What did the last cycle cost?" headline={<span>{headline}</span>}>
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1">
          <Label htmlFor="cycle-from" className="type-caption text-muted-foreground">From</Label>
          <Input id="cycle-from" type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} className="w-40" />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="cycle-to" className="type-caption text-muted-foreground">To</Label>
          <Input id="cycle-to" type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} className="w-40" />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="cycle-budget" className="type-caption text-muted-foreground">Budget (optional)</Label>
          <Input
            id="cycle-budget"
            inputMode="decimal"
            placeholder="e.g. 500000"
            value={budgetInput}
            onChange={(e) => setBudgetInput(e.target.value)}
            className="w-40"
          />
        </div>
      </div>

      {increaseCycle.isLoading ? (
        <TableSkeleton columns={["100%"]} rows={4} />
      ) : increaseCycle.isError || !increaseCycle.data ? (
        <ErrorState title="Couldn't load the increase cycle" detail="Narrow the date range and try again." />
      ) : (
        <>
          {increaseCycle.data.budget && increaseCycle.data.budgetBurnPercent ? (
            <p className="type-body-sm text-muted-foreground">
              Budget burn: {formatPercent(Number(increaseCycle.data.budgetBurnPercent) * 100)} of{" "}
              {formatAmount(increaseCycle.data.budget, { whole: true })} {increaseCycle.data.budget.currency}
            </p>
          ) : null}
          <ChartCard
            title="Spend by reason"
            basisLine={`${increaseCycle.data.fromDate} to ${increaseCycle.data.toDate} · normalised to ${increaseCycle.data.baseCurrency} · avg increase ${formatPercent(Number(increaseCycle.data.avgIncreasePercent) * 100)} · median ${formatPercent(Number(increaseCycle.data.medianIncreasePercent) * 100)}`}
            chart={
              <ThemedBarChart
                data={increaseCycle.data.byReason.map((r) => ({
                  key: r.reasonCode,
                  label: CHANGE_REASON_LABEL[r.reasonCode] ?? r.reasonCode,
                  value: Number(r.totalIncrease.amount),
                  displayValue: `${formatAmount(r.totalIncrease, { whole: true })} ${r.totalIncrease.currency}`,
                }))}
              />
            }
            table={
              increaseCycle.data.byReason.length === 0 ? (
                <EmptyState title="No applied changes in this range" detail="Widen the date range to see spend." />
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow className="h-10">
                      <TableHead className="type-label text-muted-foreground">Reason</TableHead>
                      <TableHead className="type-label text-muted-foreground">Count</TableHead>
                      <TableHead className="type-label text-muted-foreground">Total</TableHead>
                      <TableHead className="type-label text-muted-foreground">Avg %</TableHead>
                      <TableHead className="type-label text-muted-foreground">Median %</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {increaseCycle.data.byReason.map((r) => (
                      <TableRow key={r.reasonCode} className="h-10">
                        <TableCell className="type-body-sm">{CHANGE_REASON_LABEL[r.reasonCode] ?? r.reasonCode}</TableCell>
                        <TableCell className="figure-sm">{r.count}</TableCell>
                        <TableCell className="figure-sm">{formatAmount(r.totalIncrease, { whole: true })} {r.totalIncrease.currency}</TableCell>
                        <TableCell className="figure-sm">{formatPercent(Number(r.avgIncreasePercent) * 100)}</TableCell>
                        <TableCell className="figure-sm">{formatPercent(Number(r.medianIncreasePercent) * 100)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )
            }
            csv={{
              filename: `increase-cycle-${increaseCycle.data.fromDate}-to-${increaseCycle.data.toDate}.csv`,
              headers: ["Reason", "Count", "Total increase", "Avg %", "Median %"],
              rows: increaseCycle.data.byReason.map((r) => [
                CHANGE_REASON_LABEL[r.reasonCode] ?? r.reasonCode,
                String(r.count),
                `${r.totalIncrease.amount} ${r.totalIncrease.currency}`,
                r.avgIncreasePercent,
                r.medianIncreasePercent,
              ]),
            }}
          />
        </>
      )}
    </QuestionCard>
  );
}
