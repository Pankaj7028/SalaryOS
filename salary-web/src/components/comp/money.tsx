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
  size?: "figure-xl" | "figure-lg" | "figure" | "figure-sm";
  whole?: boolean;
  showCurrency?: boolean;
  className?: string;
}) {
  return (
    <span className={cn("inline-flex items-baseline gap-1.5", className)}>
      <span className={size}>{formatAmount(value, { whole })}</span>
      {showCurrency ? (
        <span className="figure-sm text-muted-foreground">{value.currency}</span>
      ) : null}
    </span>
  );
}
