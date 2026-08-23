"use client";

import type { BandHealthResponse, BandHealthRow } from "@/lib/api/analytics";

/**
 * P11.4 — the health judgement laid over the existing `/bands` level × country matrix (F9).
 *
 * <p>The matrix was already the right shape; what it could not say was whether the structure it
 * displays is any good. A band store tells you what the ranges are. Band health tells you the
 * ranges have a promotion cliff in them, or that fifteen of them contain nobody at all — which is
 * the difference between a screen you look things up in and a screen that tells you something.
 *
 * <p><b>Every tone here is a defined token</b> (CLAUDE.md §5.1): `--critical` for a defect that
 * makes someone's pay wrong, `--attention` for one that makes the structure untrustworthy. No raw
 * hex, and no third colour invented for a third severity — there are exactly two, which is what
 * the palette defines.
 */

export type BandFlag = {
  key: string;
  label: string;
  detail: string;
  severity: "critical" | "attention";
};

/**
 * What is wrong with one band, worst first.
 *
 * <p>A *gap* is critical and an overlap is not a defect at all. Adjacent bands overlapping is
 * normal and healthy; a gap — this band's minimum above the previous level's maximum — is a
 * promotion cliff, where someone promoted out of the top of one band lands at the bottom of the
 * next and goes backwards relative to where they were standing. That is a person's pay being
 * wrong, so it is `--critical`. An empty or stale band is a structure nobody can trust, which is
 * `--attention`: nothing is wrong with anyone's pay today.
 */
export function flagsFor(row: BandHealthRow, staleAfterMonths: number): BandFlag[] {
  const flags: BandFlag[] = [];

  if (row.gapToPreviousLevel) {
    flags.push({
      key: "gap",
      label: "Promotion cliff",
      detail:
        "This band starts above where the level below ends. Someone promoted out of the top of that band lands at the bottom of this one.",
      severity: "critical",
    });
  }
  if (row.incumbents === 0) {
    flags.push({
      key: "empty",
      label: "No incumbents",
      detail: "Nobody is paid against this band, so nothing has ever tested whether it is right.",
      severity: "attention",
    });
  }
  if (row.monthsSinceVersioned !== null && row.monthsSinceVersioned >= staleAfterMonths) {
    flags.push({
      key: "stale",
      label: `Not versioned in ${row.monthsSinceVersioned} months`,
      detail: `Pay moves faster than ${staleAfterMonths} months. A band this old is describing a market that has changed.`,
      severity: "attention",
    });
  }

  return flags;
}

const TONE: Record<BandFlag["severity"], string> = {
  critical: "text-critical",
  attention: "text-attention",
};

/** A dot, not a badge: the cell already carries three money figures and a headcount. */
export function BandFlagDots({ flags }: { flags: BandFlag[] }) {
  if (flags.length === 0) return null;
  return (
    <span className="flex items-center gap-1" aria-hidden>
      {flags.map((flag) => (
        <span key={flag.key} className={`${TONE[flag.severity]} type-caption leading-none`}>
          ●
        </span>
      ))}
    </span>
  );
}

/** The same flags in words — for the detail dialog, and for screen readers on the cell. */
export function BandFlagList({ flags }: { flags: BandFlag[] }) {
  if (flags.length === 0) return null;
  return (
    <ul className="flex flex-col gap-2">
      {flags.map((flag) => (
        <li key={flag.key}>
          <span className={`type-label ${TONE[flag.severity]}`}>{flag.label}</span>
          <p className="type-caption text-muted-foreground">{flag.detail}</p>
        </li>
      ))}
    </ul>
  );
}

/**
 * The four numbers that say whether the band structure as a whole is in good order.
 *
 * <p>Zeroes are shown, not hidden — "no promotion cliffs" is the answer someone came for, and a
 * strip that only renders problems is indistinguishable from one that failed to load.
 */
export function BandHealthSummary({ health }: { health: BandHealthResponse }) {
  const stats: { label: string; value: number; tone?: string; hint: string }[] = [
    { label: "In-force bands", value: health.inForceBands, hint: "Live today across every level and country." },
    {
      label: "Promotion cliffs",
      value: health.bandsWithGapToPreviousLevel,
      tone: health.bandsWithGapToPreviousLevel > 0 ? TONE.critical : undefined,
      hint: "A band starting above where the level below ends.",
    },
    {
      label: "No incumbents",
      value: health.bandsWithNoIncumbents,
      tone: health.bandsWithNoIncumbents > 0 ? TONE.attention : undefined,
      hint: "Nobody is paid against them.",
    },
    {
      label: `Stale (${health.staleAfterMonths}m+)`,
      value: health.staleBands,
      tone: health.staleBands > 0 ? TONE.attention : undefined,
      hint: "Not versioned since pay last moved.",
    },
  ];

  return (
    <dl className="grid grid-cols-2 gap-3 md:grid-cols-4">
      {stats.map((stat) => (
        <div key={stat.label} className="border-border bg-card rounded-lg border px-4 py-3">
          <dt className="type-label text-muted-foreground">{stat.label}</dt>
          <dd className={`figure-lg ${stat.tone ?? ""}`}>{stat.value.toLocaleString()}</dd>
          <p className="type-caption text-muted-foreground mt-0.5">{stat.hint}</p>
        </div>
      ))}
    </dl>
  );
}

/** Range spread and midpoint progression as percentages — the two structural shape figures. */
export function BandShapeFigures({ row }: { row: BandHealthRow }) {
  return (
    <dl className="grid grid-cols-2 gap-x-4 gap-y-2">
      <div>
        <dt className="type-label text-muted-foreground">Range spread</dt>
        <dd className="figure-sm">{formatFraction(row.rangeSpread)}</dd>
      </div>
      <div>
        <dt className="type-label text-muted-foreground">Progression from below</dt>
        <dd className="figure-sm">
          {row.midpointProgression === null ? (
            <span className="text-muted-foreground">Lowest level</span>
          ) : (
            formatFraction(row.midpointProgression)
          )}
        </dd>
      </div>
      <div>
        <dt className="type-label text-muted-foreground">Incumbents</dt>
        <dd className="figure-sm">{row.incumbents}</dd>
      </div>
      <div>
        <dt className="type-label text-muted-foreground">Median compa-ratio</dt>
        <dd className="figure-sm">
          {row.medianCompaRatio === null ? <span className="text-muted-foreground">—</span> : row.medianCompaRatio}
        </dd>
      </div>
    </dl>
  );
}

/**
 * The server sends a decimal string and the browser only formats it (CLAUDE.md §6.1). `Number` is
 * safe here and nowhere near money: this is a ratio the server already computed, being turned into
 * a percentage for display, not an arithmetic step whose result anyone is paid.
 */
function formatFraction(value: string): string {
  return `${(Number(value) * 100).toFixed(1)}%`;
}
