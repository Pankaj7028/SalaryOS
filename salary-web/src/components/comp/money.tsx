import { cn } from "@/lib/utils";
import { formatAmount, type Money as MoneyValue } from "@/lib/money";

/**
 * The only way a money value is rendered (§7.2).
 *
 * Mono and tabular, with the currency code as a separate muted span so digits
 * align across currencies. It never does arithmetic — if a displayed value needs
 * computing, the API returns it (CLAUDE.md §6.1).
 */
export function Money({
  value,
  size = "figure",
  whole = false,
  showCurrency = true,
  className,
}: {
  value: MoneyValue;
  /** `figure-fluid-xl` sizes against the nearest `@container` — for headline figures whose
   * length varies wildly (a headcount and a payroll total share one card size). */
  size?: "figure-xl" | "figure-fluid-xl" | "figure-lg" | "figure" | "figure-sm";
  whole?: boolean;
  showCurrency?: boolean;
  className?: string;
}) {
  return (
    // `flex-wrap` + `min-w-0`: if the amount and its currency code together exceed the space,
    // the code drops to the next line rather than shouldering the number out of its container.
    // The figure itself is never the thing that gives way — it is the value being reported.
    <span className={cn("inline-flex min-w-0 flex-wrap items-baseline gap-x-1.5", className)}>
      <span className={size}>{formatAmount(value, { whole })}</span>
      {showCurrency ? (
        <span className="figure-sm text-muted-foreground">{value.currency}</span>
      ) : null}
    </span>
  );
}
