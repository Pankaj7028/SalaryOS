"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Money } from "@/components/comp/money";
import { Delta } from "@/components/comp/delta";
import { EmptyState, TableSkeleton } from "@/components/feedback/states";
import { CHANGE_REASON_LABEL } from "@/lib/change-reasons";
import type { Change } from "@/lib/api/changes";

const SKELETON_COLUMNS = ["170px", "160px", "90px", "100px", "120px", "110px", "140px"];

/**
 * `/changes` (ui doc §8.5) row rendering — desktop table (≥768px) and a `md:hidden` card list
 * below it, same "table degrades to cards at 375px" discipline as the Employees table and Bands
 * grid (§12.10). `showActions` gates both the Actions column/card-footer AND whether they render
 * at all — "the tab you cannot act on for your role is visible but its actions are absent," never
 * disabled-with-a-tooltip, so a role without them sees one column/row fewer, not a greyed-out one.
 */
export function ChangesTable({
  changes,
  isLoading,
  isError,
  emptyTitle,
  onRetry,
  actionsLabel,
  showActions,
  renderActions,
}: {
  changes: Change[];
  isLoading: boolean;
  isError: boolean;
  emptyTitle: string;
  onRetry: () => void;
  actionsLabel?: string;
  showActions: boolean;
  renderActions?: (change: Change) => ReactNode;
}) {
  if (isLoading) {
    return <TableSkeleton columns={SKELETON_COLUMNS} />;
  }

  if (isError) {
    return (
      <div className="border-critical/30 bg-critical-subtle flex flex-col items-center gap-3 rounded-lg border px-6 py-10 text-center">
        <p className="type-subsection text-critical">Couldn&apos;t load changes</p>
        <p className="type-body-sm text-muted-foreground">Check your connection and try again.</p>
        <Button size="sm" variant="outline" onClick={onRetry}>Retry</Button>
      </div>
    );
  }

  if (changes.length === 0) {
    return (
      <EmptyState
        title={emptyTitle}
        detail="Propose a change from an employee's profile to see it here."
      />
    );
  }

  return (
    <>
      <div className="border-border hidden overflow-hidden rounded-lg border md:block">
        <Table>
          <TableHeader className="bg-muted/40">
            <TableRow className="h-10">
              <TableHead className="type-label text-muted-foreground">Employee</TableHead>
              <TableHead className="type-label text-muted-foreground">Current → proposed</TableHead>
              <TableHead className="type-label text-muted-foreground">Delta</TableHead>
              <TableHead className="type-label text-muted-foreground">Effective</TableHead>
              <TableHead className="type-label text-muted-foreground">Reason</TableHead>
              <TableHead className="type-label text-muted-foreground">Proposer</TableHead>
              {showActions ? (
                <TableHead className="type-label text-muted-foreground">{actionsLabel ?? "Actions"}</TableHead>
              ) : null}
            </TableRow>
          </TableHeader>
          <TableBody>
            {changes.map((change) => (
              <TableRow key={change.id} className="h-10">
                <TableCell>
                  <IdentityCell change={change} />
                </TableCell>
                <TableCell>
                  <CurrentProposedCell change={change} />
                </TableCell>
                <TableCell>
                  <Delta amount={change.deltaAmount} percent={change.deltaPercent * 100} size="figure-sm" />
                </TableCell>
                <TableCell>
                  <span className="figure-sm">{change.effectiveDate}</span>
                </TableCell>
                <TableCell>
                  <span className="type-body-sm">{CHANGE_REASON_LABEL[change.changeReason] ?? change.changeReason}</span>
                </TableCell>
                <TableCell>
                  <span className="type-body-sm">{change.proposedByName ?? "—"}</span>
                </TableCell>
                {showActions ? (
                  <TableCell>
                    <div className="flex gap-2">{renderActions?.(change)}</div>
                  </TableCell>
                ) : null}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <ul className="flex flex-col gap-3 md:hidden" aria-label="Changes">
        {changes.map((change) => (
          <li key={change.id} className="border-border bg-card flex flex-col gap-3 rounded-lg border p-5">
            <div className="flex items-start justify-between gap-2">
              <IdentityCell change={change} />
              <span className="type-body-sm text-muted-foreground">{change.effectiveDate}</span>
            </div>
            <CurrentProposedCell change={change} />
            <div className="flex items-center justify-between gap-3">
              <Delta amount={change.deltaAmount} percent={change.deltaPercent * 100} size="figure-sm" />
              <span className="type-caption text-muted-foreground">
                {CHANGE_REASON_LABEL[change.changeReason] ?? change.changeReason} · {change.proposedByName ?? "—"}
              </span>
            </div>
            {showActions ? <div className="flex gap-2">{renderActions?.(change)}</div> : null}
          </li>
        ))}
      </ul>
    </>
  );
}

function IdentityCell({ change }: { change: Change }) {
  return (
    <Link href={`/employees/${change.employeeId}`} className="hover:underline">
      <span className="type-body-sm text-foreground block">
        {change.employeeFirstName} {change.employeeLastName}
      </span>
      <span className="figure-sm text-muted-foreground">{change.employeeNumber}</span>
    </Link>
  );
}

function CurrentProposedCell({ change }: { change: Change }) {
  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-baseline gap-1.5">
        <Money value={change.currentBase} size="figure-sm" showCurrency={false} />
        <span className="text-muted-foreground">→</span>
        <Money value={change.newBase} size="figure-sm" />
      </div>
      {change.outOfBand ? (
        <Badge variant="outline" className="border-attention/40 bg-attention-subtle text-attention type-label w-fit">
          Out of band
        </Badge>
      ) : null}
    </div>
  );
}
