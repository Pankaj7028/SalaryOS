/**
 * Money in the browser (CLAUDE.md §6.1, §6.2).
 *
 * `amount` is a STRING, not a number. The server holds NUMERIC(15,2) / BigDecimal,
 * and an IEEE-754 double cannot represent every such value exactly — routing a
 * salary through `number` silently rounds it. Keeping the server's decimal text
 * verbatim means the figure displayed is the figure stored.
 *
 * And a money value NEVER travels without its currency (§6.2). There is no bare
 * `amount` type in this file, deliberately: the pair is the unit.
 *
 * Nothing here does arithmetic. The browser formats; the API computes. If a
 * figure needs working out, add it to the API response.
 */
export type Money = {
  amount: string;
  currency: string;
};

export type BandStatus = "IN_BAND" | "BELOW_MIN" | "ABOVE_MAX" | "NO_BAND";

/**
 * Salary band, with its own currency — bands and pay can differ in currency.
 *
 * `marketP50` is the most recently imported market median for this band's (job level, country),
 * in the band's own currency (P11.6/F10). It is **null far more often than not**, and that is the
 * ordinary case rather than a degraded one: Salary OS ships the seam for market data, not a
 * dataset. The server also sends null when the survey is denominated differently from the band —
 * a tick drawn on a GBP scale from a USD figure would be a silent lie on a scale the reader trusts
 * to be one currency.
 */
export type Band = {
  min: Money;
  mid: Money;
  max: Money;
  marketP50?: Money | null;
};

/**
 * Position of a salary within its band, **computed server-side**. The UI draws
 * these; it does not derive them.
 *
 * @param percentThroughRange 0 at the minimum, 100 at the maximum. May fall
 *        outside 0–100 when the salary is outside the band — which is the point.
 * @param compaRatio salary ÷ band mid, always shown to two decimals.
 */
export type BandPosition = {
  status: BandStatus;
  percentThroughRange: number;
  compaRatio: number;
};

/**
 * Formats with Intl.NumberFormat, passing the decimal STRING straight through so
 * no double is ever constructed.
 *
 * `whole` is for aggregates of a million or more, where §3.2 allows whole units —
 * the caller states the rounding in the card's caption.
 */
export function formatAmount(
  money: Money,
  opts: { whole?: boolean; locale?: string } = {},
): string {
  const digits = opts.whole ? 0 : 2;
  return new Intl.NumberFormat(opts.locale ?? "en-GB", {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
    useGrouping: true,
  }).format(money.amount as unknown as number);
}

/** Compa-ratio is always two decimals (§3.2). */
export function formatCompaRatio(ratio: number): string {
  return ratio.toFixed(2);
}

/** Percent is always one decimal and always carries its sign (§3.2). */
export function formatPercent(percent: number): string {
  const sign = percent > 0 ? "+" : percent < 0 ? "−" : "";
  return `${sign}${Math.abs(percent).toFixed(1)}%`;
}

export const BAND_STATUS_LABEL: Record<BandStatus, string> = {
  IN_BAND: "In band",
  BELOW_MIN: "Below min",
  ABOVE_MAX: "Above max",
  NO_BAND: "No band",
};
