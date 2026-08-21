import type { ReactNode } from "react";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Loading, empty and error primitives (ui doc §7.8).
 *
 * The rules these encode: skeletons mirror the final geometry so nothing shifts;
 * empty is an invitation rather than an apology; a zero that is a real answer
 * says so; an error names what failed and what to do about it.
 */

/**
 * Table skeleton. Takes the REAL column widths and the real row height so the
 * layout does not jump when data arrives — a skeleton of the wrong shape is
 * worse than none, because it moves the page twice.
 */
export function TableSkeleton({
  columns,
  rows = 8,
  rowHeight = 40,
}: {
  columns: string[];
  rows?: number;
  rowHeight?: number;
}) {
  return (
    <div
      role="status"
      aria-label="Loading"
      className="border-border overflow-hidden rounded-lg border"
    >
      <div className="bg-muted/40 border-border flex items-center gap-4 border-b px-4 py-2">
        {columns.map((w, i) => (
          <Skeleton key={i} className="h-3" style={{ width: w }} />
        ))}
      </div>
      {Array.from({ length: rows }).map((_, r) => (
        <div
          key={r}
          className="border-border flex items-center gap-4 border-b px-4 last:border-0"
          style={{ height: rowHeight }}
        >
          {columns.map((w, i) => (
            <Skeleton key={i} className="h-3.5" style={{ width: w }} />
          ))}
        </div>
      ))}
    </div>
  );
}

/**
 * Empty state. `action` is required in spirit: an empty state that does not offer
 * a way forward is an apology, which §7.8 rules out. Never "No data available".
 */
export function EmptyState({
  title,
  detail,
  action,
}: {
  title: string;
  detail: string;
  action?: ReactNode;
}) {
  return (
    <div className="border-border bg-card flex flex-col items-center gap-3 rounded-lg border border-dashed px-6 py-12 text-center">
      <p className="type-subsection">{title}</p>
      <p className="type-body-sm text-muted-foreground max-w-md">{detail}</p>
      {action ? <div className="mt-1">{action}</div> : null}
    </div>
  );
}

/**
 * A zero that is a genuinely good answer — "Everyone is inside their band" — with
 * the population it checked, so it cannot be mistaken for a broken query.
 */
export function GoodNewsState({ title, population }: { title: string; population: string }) {
  return (
    <div className="border-positive/30 bg-positive-subtle flex flex-col items-center gap-2 rounded-lg border px-6 py-10 text-center">
      <p className="type-subsection text-positive">{title}</p>
      <p className="type-caption text-muted-foreground">{population}</p>
    </div>
  );
}

/** Names what failed and what to do. No apologies, no "something went wrong". */
export function ErrorState({
  title,
  detail,
  action,
}: {
  title: string;
  detail: string;
  action?: ReactNode;
}) {
  return (
    <div
      role="alert"
      className="border-critical/30 bg-critical-subtle flex flex-col items-center gap-3 rounded-lg border px-6 py-10 text-center"
    >
      <p className="type-subsection text-critical">{title}</p>
      <p className="type-body-sm text-muted-foreground max-w-md">{detail}</p>
      {action ? <div className="mt-1">{action}</div> : null}
    </div>
  );
}
