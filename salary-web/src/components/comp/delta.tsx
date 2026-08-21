import { cn } from "@/lib/utils";
import { formatAmount, formatPercent, type Money as MoneyValue } from "@/lib/money";

/**
 * Signed change (§7.3). Carries its sign, takes --positive / --critical, and
 * shows both forms where space allows: "+£6,000 (+4.4%)".
 *
 * Zero renders an em dash, never "0.0%" and never a bare arrow — an arrow
 * without a number is a mood, not a figure.
 *
 * `amount` and `percent` are both computed server-side.
 */
export function Delta({
  amount,
  percent,
  size = "figure",
  className,
}: {
  amount?: MoneyValue;
  percent?: number;
  size?: "figure-lg" | "figure" | "figure-sm";
  className?: string;
}) {
  const raw = percent ?? (amount ? Number(amount.amount) : 0);
  const isZero = raw === 0;

  if (isZero) {
    return (
      <span className={cn(size, "text-neutral-figure", className)} aria-label="No change">
        —
      </span>
    );
  }

  const tone = raw > 0 ? "text-positive" : "text-critical";
  const sign = raw > 0 ? "+" : "−";

  return (
    <span className={cn(size, tone, "inline-flex items-baseline gap-1.5", className)}>
      {amount ? (
        <span>
          {sign}
          {formatAmount({ ...amount, amount: amount.amount.replace(/^-/, "") })}
          <span className="figure-sm ml-1 opacity-80">{amount.currency}</span>
        </span>
      ) : null}
      {percent !== undefined ? (
        <span className={amount ? "figure-sm" : undefined}>
          {amount ? `(${formatPercent(percent)})` : formatPercent(percent)}
        </span>
      ) : null}
    </span>
  );
}
