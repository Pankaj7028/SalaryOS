import type { ReactNode } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/** ui doc §8.1: six figures across the top — one `figure-xl`, a `label`, and a `figure-sm`
 * comparison giving the figure its context. `value` takes a node (usually `<Money size="figure-xl">`
 * or a plain `figure-xl`-classed span) so every figure in it stays mono/tabular per §3.2. */
export function StatCard({
  label,
  value,
  comparison,
  href,
}: {
  label: string;
  value: ReactNode;
  comparison?: string;
  href?: string;
}) {
  const Wrapper = href ? "a" : "div";
  return (
    <Wrapper
      href={href}
      className={cn(
        "border-border bg-card flex flex-col gap-1 rounded-lg border p-5",
        href && "hover:bg-muted/40 focus-visible:ring-ring/50 focus-visible:ring-3 focus-visible:outline-none",
      )}
    >
      <span className="type-label text-muted-foreground">{label}</span>
      <span className="figure-xl">{value}</span>
      {comparison ? <span className="figure-sm text-muted-foreground">{comparison}</span> : null}
    </Wrapper>
  );
}

export function StatCardSkeleton() {
  return (
    <div className="border-border bg-card flex flex-col gap-2 rounded-lg border p-5">
      <Skeleton className="h-3 w-2/3" />
      <Skeleton className="h-7 w-1/2" />
      <Skeleton className="h-3 w-1/3" />
    </div>
  );
}
