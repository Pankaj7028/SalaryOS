"use client";

import { Money } from "@/components/comp/money";
import { EmptyState, ErrorState, TableSkeleton } from "@/components/feedback/states";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { usePayGap } from "@/lib/api/analytics-queries";
import { formatAmount, formatPercent } from "@/lib/money";
import { downloadCsv } from "@/lib/csv";
import type { PayGapGroupMedian } from "@/lib/api/analytics";

/**
 * `/insights/equity` (ui doc §8.8). Unadjusted and level-adjusted figures render as two
 * separately-labelled sections, never merged into one number — conflating an org-wide mix effect
 * with a like-for-like within-level comparison is exactly how a pay-gap figure stops being
 * defensible (§8.8's own words). The suppression notice is stated at the top unconditionally, even
 * at zero, so "nothing was suppressed" is itself a visible, checked fact rather than an absence.
 */
export function EquityReviewScreen() {
  const payGap = usePayGap();

  return (
    <div className="space-y-6">
      <header>
        <h1 className="type-title">Equity review</h1>
        <p className="type-caption text-muted-foreground mt-1">
          {payGap.data ? `As at ${payGap.data.asAtDate} · normalised to ${payGap.data.baseCurrency}` : "Loading…"}
        </p>
      </header>

      {payGap.isLoading ? (
        <TableSkeleton columns={["100%"]} rows={6} />
      ) : payGap.isError || !payGap.data ? (
        <ErrorState title="Couldn't load the equity review" detail="Check your connection and try again." />
      ) : (
        <>
          <SuppressionNotice count={payGap.data.suppressedCohorts} />

          <section className="space-y-3">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="type-section">Unadjusted</h2>
                <p className="type-caption text-muted-foreground">
                  Org-wide median pay by group, ignoring job level entirely — a mix effect (who
                  holds senior roles) can dominate this number.
                </p>
              </div>
              {payGap.data.unadjustedGroups.length >= 2 ? (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() =>
                    downloadCsv(
                      `pay-gap-unadjusted-${payGap.data.asAtDate}.csv`,
                      ["Group", "Count", "Median amount", "Currency"],
                      payGap.data.unadjustedGroups.map((g) => [
                        g.group,
                        String(g.count),
                        g.median.amount,
                        g.median.currency,
                      ]),
                    )
                  }
                >
                  Export CSV
                </Button>
              ) : null}
            </div>
            <UnadjustedPanel
              groups={payGap.data.unadjustedGroups}
              gapAmount={payGap.data.unadjustedGapAmount}
              gapPercent={payGap.data.unadjustedGapPercent}
            />
          </section>

          <section className="space-y-3">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="type-section">Level-adjusted</h2>
                <p className="type-caption text-muted-foreground">
                  Median pay by group within each job level × country cohort — a like-for-like
                  comparison, controlling for level by construction.
                </p>
              </div>
              {payGap.data.levelAdjustedCohorts.length > 0 ? (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() =>
                    downloadCsv(
                      `pay-gap-level-adjusted-${payGap.data.asAtDate}.csv`,
                      ["Level", "Country", "Group", "Median amount", "Currency", "Count", "Spread amount", "Spread %"],
                      payGap.data.levelAdjustedCohorts.flatMap((c) =>
                        c.groups.map((g) => [
                          c.jobLevelLabel,
                          c.countryLabel,
                          g.group,
                          g.median.amount,
                          g.median.currency,
                          String(g.count),
                          c.gapAmount.amount,
                          c.gapPercent,
                        ]),
                      ),
                    )
                  }
                >
                  Export CSV
                </Button>
              ) : null}
            </div>
            <CohortTable cohorts={payGap.data.levelAdjustedCohorts} />
          </section>
        </>
      )}
    </div>
  );
}

function SuppressionNotice({ count }: { count: number }) {
  if (count === 0) {
    return (
      <p className="type-body-sm text-muted-foreground border-border rounded-lg border border-dashed px-4 py-3">
        No cohorts were suppressed — every level × country pairing with demographic coverage had at
        least two groups of five or more.
      </p>
    );
  }
  return (
    <p className="border-attention/40 bg-attention-subtle text-attention type-body-sm rounded-lg border px-4 py-3">
      {count} {count === 1 ? "cohort is" : "cohorts are"} not shown here — a group in that
      level × country pairing had fewer than five people, or only one group was represented at
      all, so no gap could be shown without risking identifying someone.
    </p>
  );
}

function UnadjustedPanel({
  groups,
  gapAmount,
  gapPercent,
}: {
  groups: PayGapGroupMedian[];
  gapAmount: { amount: string; currency: string };
  gapPercent: string;
}) {
  if (groups.length < 2) {
    return (
      <EmptyState
        title="Not enough groups to compare"
        detail="Fewer than two demographic groups have five or more people org-wide."
      />
    );
  }
  return (
    <div className="border-border bg-card flex flex-wrap items-center gap-6 rounded-lg border p-5">
      {groups.map((g) => (
        <div key={g.group} className="flex flex-col gap-1">
          <span className="type-label text-muted-foreground">{g.group}</span>
          <Money value={g.median} size="figure-lg" />
          <span className="type-caption text-muted-foreground">{g.count} people</span>
        </div>
      ))}
      <div className="flex flex-col gap-1 border-l pl-6">
        <span className="type-label text-muted-foreground">Spread (highest − lowest)</span>
        <Money value={gapAmount} size="figure-lg" />
        <span className="type-caption text-muted-foreground">{formatPercent(Number(gapPercent) * 100)}</span>
      </div>
    </div>
  );
}

function CohortTable({
  cohorts,
}: {
  cohorts: {
    jobLevelId: string;
    jobLevelLabel: string;
    countryLabel: string;
    groups: PayGapGroupMedian[];
    gapAmount: { amount: string; currency: string };
    gapPercent: string;
  }[];
}) {
  if (cohorts.length === 0) {
    return (
      <EmptyState
        title="No level × country cohort has a comparable gap"
        detail="Every cohort with demographic coverage was suppressed — see the notice above."
      />
    );
  }
  return (
    <div className="border-border overflow-hidden rounded-lg border">
      <Table>
        <TableHeader className="bg-muted/40">
          <TableRow className="h-10">
            <TableHead className="type-label text-muted-foreground">Level</TableHead>
            <TableHead className="type-label text-muted-foreground">Country</TableHead>
            <TableHead className="type-label text-muted-foreground">Groups (median · n)</TableHead>
            <TableHead className="type-label text-muted-foreground">Spread</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {cohorts.map((c) => (
            <TableRow key={`${c.jobLevelId}-${c.countryLabel}`} className="h-10">
              <TableCell className="type-body-sm">{c.jobLevelLabel}</TableCell>
              <TableCell className="type-body-sm">{c.countryLabel}</TableCell>
              <TableCell>
                <div className="flex flex-wrap gap-2">
                  {c.groups.map((g) => (
                    <Badge key={g.group} variant="outline" className="type-label">
                      {g.group}: {formatAmount(g.median, { whole: true })} ({g.count})
                    </Badge>
                  ))}
                </div>
              </TableCell>
              <TableCell className="figure-sm">
                {formatAmount(c.gapAmount, { whole: true })} {c.gapAmount.currency} ({formatPercent(Number(c.gapPercent) * 100)})
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
