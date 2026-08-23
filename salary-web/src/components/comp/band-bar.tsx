import Link from "next/link";
import { cn } from "@/lib/utils";
import {
  BAND_STATUS_LABEL,
  formatAmount,
  formatCompaRatio,
  type Band,
  type BandPosition,
  type Money as MoneyValue,
} from "@/lib/money";

/**
 * `<BandBar>` — the signature component (§7.1, CLAUDE.md §5.6).
 *
 * A salary figure shown without its band is an incomplete answer. That is the
 * product's whole thesis, which is why this is a component and not a one-off
 * chart: wherever a salary appears next to a band, this appears with it.
 *
 * Two rules here are load-bearing:
 *
 *  1. **Out-of-band values sit OUTSIDE the track**, overshooting by 6px with the
 *     track end capped. Clamping the marker to the end would draw someone paid
 *     below minimum as sitting healthily *at* the minimum — the exact error this
 *     product exists to surface.
 *
 *  2. **No band is its own state**, a dashed outline naming what is missing. It
 *     is never a full track with a centred marker, which would invent a band
 *     that does not exist.
 *
 * Every figure is computed server-side (§6.1). `percentThroughRange` and
 * `compaRatio` arrive from the API; this component only draws them.
 */

const WIDTH = {
  inline: "w-16", // 64px — table cells
  default: "w-[200px]", // cards, dialogs
  detail: "w-full", // employee page, with labelled endpoints
} as const;

const MARKER_TONE = {
  IN_BAND: "bg-positive",
  BELOW_MIN: "bg-attention",
  ABOVE_MAX: "bg-critical",
  NO_BAND: "bg-muted-foreground",
} as const;

export type BandBarProps = {
  salary: MoneyValue;
  band: Band | null;
  position: BandPosition;
  variant?: keyof typeof WIDTH;
  /** Shown in the no-band state, e.g. "L4 · Ireland". */
  scope?: string;
  className?: string;
};

/** "142,500 GBP · compa-ratio 0.94 · in band, 62% through range" */
function accessibleSentence(props: BandBarProps): string {
  const { salary, position, scope } = props;
  const money = `${formatAmount(salary)} ${salary.currency}`;
  if (position.status === "NO_BAND") {
    return `${money} · no band${scope ? ` for ${scope}` : ""}`;
  }
  const state = BAND_STATUS_LABEL[position.status].toLowerCase();
  const base = `${money} · compa-ratio ${formatCompaRatio(position.compaRatio)} · ${state}, ${Math.round(
    position.percentThroughRange,
  )}% through range`;
  // §7.1: the bar is decorative to a screen reader and this sentence is the content, so a tick
  // that is visible must also be audible — otherwise the market benchmark exists only for people
  // who can see it.
  const marketP50 = props.band?.marketP50;
  return marketP50
    ? `${base} · market median ${formatAmount(marketP50)} ${marketP50.currency}`
    : base;
}

export function BandBar(props: BandBarProps) {
  const { band, position, variant = "default", scope, className } = props;
  const sentence = accessibleSentence(props);

  // ---- no band ------------------------------------------------------------
  if (!band || position.status === "NO_BAND") {
    return (
      <div className={cn("flex flex-col gap-1", WIDTH[variant], className)}>
        <div
          aria-hidden
          className="border-border h-1 w-full rounded-full border border-dashed bg-transparent"
        />
        {variant !== "inline" ? (
          <Link
            href="/bands"
            className="type-caption text-muted-foreground hover:text-foreground underline underline-offset-2"
          >
            No band{scope ? ` for ${scope}` : ""}
          </Link>
        ) : null}
        <span className="sr-only">{sentence}</span>
      </div>
    );
  }

  const pct = position.percentThroughRange;
  const below = pct < 0;
  const above = pct > 100;

  /**
   * Marker offset. In band it is a percentage along the track; outside it, a 6px
   * overshoot past the capped end — never clamped to 0% or 100%.
   */
  const markerStyle = below
    ? { left: "-6px" }
    : above
      ? { left: "calc(100% + 6px)" }
      : { left: `${pct}%` };

  /**
   * Mid tick position. This is layout geometry — where to put a 1px rule — not a
   * displayed figure, so it is derived here rather than round-tripped. Every
   * number the reader actually SEES still comes from the API.
   */
  const minN = Number(band.min.amount);
  const maxN = Number(band.max.amount);
  const midN = Number(band.mid.amount);
  const midPct = maxN > minN ? ((midN - minN) / (maxN - minN)) * 100 : 50;

  /**
   * Market-median tick position, same layout-geometry reasoning as the mid tick — where to put a
   * 1px rule, not a figure anyone reads off the pixels.
   *
   * Null when there is no market data (the ordinary case) **and when the median falls outside the
   * band**. A tick pinned to the track end would say "the market is exactly at your maximum", which
   * is precisely wrong in the situation that matters most: a band that has fallen behind the market
   * entirely. The figure is still in the accessible sentence, where it cannot mislead by position.
   */
  const marketN = band.marketP50 ? Number(band.marketP50.amount) : null;
  const marketPct =
    marketN !== null && maxN > minN && marketN >= minN && marketN <= maxN
      ? ((marketN - minN) / (maxN - minN)) * 100
      : null;

  return (
    <div className={cn("flex flex-col gap-1.5", WIDTH[variant], className)}>
      <div aria-hidden className="relative h-3.5">
        {/* track: 4px, band min → max */}
        <div className="bg-muted absolute top-1/2 h-1 w-full -translate-y-1/2 rounded-full" />
        {/* capped ends, so an overshooting marker reads as beyond the band */}
        <div className="bg-border absolute top-1/2 left-0 h-2 w-px -translate-y-1/2" />
        <div className="bg-border absolute top-1/2 right-0 h-2 w-px -translate-y-1/2" />
        {/* mid tick */}
        <div
          className="bg-border absolute top-1/2 h-2 w-px -translate-y-1/2"
          style={{ left: `${midPct}%` }}
        />
        {/* P11.6 — market median. Drawn ABOVE the track rather than on it, and only when there is
            data: the track is the band, and a market figure is a different kind of claim about the
            same scale. It is deliberately not a second marker shape competing with the salary's,
            which is the one thing on this bar the reader is looking for. A band with no market data
            renders exactly as it did before — no placeholder, no empty tick. */}
        {marketPct !== null ? (
          <div
            className="bg-muted-foreground absolute top-0 h-1.5 w-px"
            style={{ left: `${marketPct}%` }}
          />
        ) : null}
        {/* marker: 3 × 14px */}
        <div
          className={cn(
            "absolute top-1/2 h-3.5 w-[3px] -translate-x-1/2 -translate-y-1/2 rounded-full",
            MARKER_TONE[position.status],
          )}
          style={markerStyle}
        />
      </div>

      {variant === "detail" ? (
        /* Each label sits under its own tick. `justify-between` would centre the
           mid label at 50%, but mid is rarely the midpoint of min and max — this
           band's mid tick is at 45.5% — and a label pointing at the wrong place
           on a pay scale is worse than no label. */
        <div aria-hidden className="text-muted-foreground relative h-4">
          <span className="figure-sm absolute left-0">{formatAmount(band.min)}</span>
          <span className="figure-sm absolute -translate-x-1/2" style={{ left: `${midPct}%` }}>
            {formatAmount(band.mid)}
          </span>
          <span className="figure-sm absolute right-0">{formatAmount(band.max)}</span>
        </div>
      ) : null}

      {/* The detail variant labels its ticks, so it labels this one too — as a sentence rather
          than a floating number, because "market median" is not self-evident from a position. */}
      {variant === "detail" && band.marketP50 ? (
        <p aria-hidden className="type-caption text-muted-foreground">
          Market median {formatAmount(band.marketP50)} {band.marketP50.currency}
          {marketPct === null ? " — outside this band" : ""}
        </p>
      ) : null}

      {/* The bar is decorative; this sentence is the content (§7.1). */}
      <span className="sr-only">{sentence}</span>
    </div>
  );
}
