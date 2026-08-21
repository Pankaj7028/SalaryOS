"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Money } from "@/components/comp/money";
import { EmptyState, TableSkeleton } from "@/components/feedback/states";
import { useCompensationHistory } from "@/lib/api/employees-queries";

const REASON_LABEL: Record<string, string> = {
  INITIAL: "Initial hire",
  MERIT: "Merit increase",
  PROMOTION: "Promotion",
  MARKET_ADJUSTMENT: "Market adjustment",
  ROLE_CHANGE: "Role change",
  LOCATION_CHANGE: "Location change",
  CORRECTION: "Correction",
  DEMOTION: "Demotion",
};

/**
 * Pay history (ui doc §8.3): the ledger, one dated entry per period. A `<Delta>` per change is
 * deliberately not shown — computing it would mean subtracting two money amounts in the browser,
 * which CLAUDE.md §6.1 rules out ("the browser formats; it does not calculate"), and the backend
 * doesn't return a precomputed delta between consecutive ledger entries yet. Note, proposer, and
 * approver are also not shown — those live on the `compensation_changes` row this record's
 * `changeId` points at, and that domain doesn't exist until P6.1.
 */
export function PayHistoryPanel({ employeeId }: { employeeId: string }) {
  const history = useCompensationHistory(employeeId);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Pay history</CardTitle>
      </CardHeader>
      <CardContent>
        {history.isLoading ? (
          <TableSkeleton columns={["80px", "120px", "100px"]} rows={3} rowHeight={44} />
        ) : history.isError ? (
          <EmptyState title="Couldn't load pay history" detail="Check your connection and try again." />
        ) : !history.data || history.data.length === 0 ? (
          <EmptyState title="No pay history yet" detail="This employee has no compensation record on file." />
        ) : (
          <ol className="border-border relative flex flex-col gap-5 border-l pl-5">
            {history.data.map((record) => (
              <li key={record.id} className="relative">
                <span
                  aria-hidden
                  className="bg-primary absolute top-1 -left-[23px] size-2.5 rounded-full"
                />
                <div className="flex flex-wrap items-baseline gap-2">
                  <span className="figure-sm text-muted-foreground">
                    {record.effectiveFrom}
                    {record.effectiveTo ? ` – ${record.effectiveTo}` : " – present"}
                  </span>
                  <Badge variant="outline" className="type-label">
                    {REASON_LABEL[record.changeReason] ?? record.changeReason}
                  </Badge>
                </div>
                <div className="mt-1">
                  <Money value={record.base} size="figure" />
                </div>
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  );
}
