import { BandBar } from "@/components/comp/band-bar";
import { BandStatusBadge } from "@/components/comp/band-status-badge";
import { Delta } from "@/components/comp/delta";
import { Money } from "@/components/comp/money";
import type { Band, BandPosition, BandStatus, Money as MoneyValue } from "@/lib/money";

/**
 * Component gallery — the Verify for build step P3.7.
 *
 * Every state of every compensation primitive, in both themes, including the two
 * that are easy to get wrong and invisible when you do: an out-of-band marker
 * sitting OUTSIDE the track rather than clamped to its end, and the no-band
 * state rendering as a dashed outline rather than a centred marker.
 *
 * Development-only surface.
 */

const GBP = (amount: string): MoneyValue => ({ amount, currency: "GBP" });
const band: Band = { min: GBP("95000.00"), mid: GBP("120000.00"), max: GBP("150000.00") };

const CASES: {
  label: string;
  salary: MoneyValue;
  position: BandPosition;
  band: Band | null;
  scope?: string;
}[] = [
  {
    label: "In band — 62% through range",
    salary: GBP("129000.00"),
    band,
    position: { status: "IN_BAND", percentThroughRange: 61.8, compaRatio: 1.08 },
  },
  {
    label: "At the minimum — 0%",
    salary: GBP("95000.00"),
    band,
    position: { status: "IN_BAND", percentThroughRange: 0, compaRatio: 0.79 },
  },
  {
    label: "Below minimum — marker sits OUTSIDE the track",
    salary: GBP("88000.00"),
    band,
    position: { status: "BELOW_MIN", percentThroughRange: -12.7, compaRatio: 0.73 },
  },
  {
    label: "Above maximum — marker sits OUTSIDE the track",
    salary: GBP("164000.00"),
    band,
    position: { status: "ABOVE_MAX", percentThroughRange: 125.5, compaRatio: 1.37 },
  },
  {
    label: "No band — dashed, never a centred marker",
    salary: GBP("112000.00"),
    band: null,
    scope: "L4 · Ireland",
    position: { status: "NO_BAND", percentThroughRange: 0, compaRatio: 0 },
  },
];

const STATUSES: BandStatus[] = ["IN_BAND", "BELOW_MIN", "ABOVE_MAX", "NO_BAND"];

function Panel({ theme, label }: { theme: "app-light" | "app-dark"; label: string }) {
  return (
    <section className={`${theme} bg-background text-foreground space-y-8 rounded-lg border p-6`}>
      <div>
        <h2 className="type-section">{label}</h2>
        <p className="type-caption text-muted-foreground">
          <code className="font-mono">.{theme}</code>
        </p>
      </div>

      <div className="space-y-6">
        <h3 className="type-subsection">BandBar — three widths, every state</h3>
        {CASES.map((c) => (
          <div key={c.label} className="space-y-2">
            <p className="type-caption text-muted-foreground">{c.label}</p>
            <div className="flex flex-wrap items-center gap-8">
              <div className="space-y-1">
                <p className="type-label text-muted-foreground">inline</p>
                <BandBar {...c} variant="inline" />
              </div>
              <div className="space-y-1">
                <p className="type-label text-muted-foreground">default</p>
                <BandBar {...c} variant="default" />
              </div>
            </div>
            <div className="max-w-md space-y-1">
              <p className="type-label text-muted-foreground">detail</p>
              <BandBar {...c} variant="detail" />
            </div>
          </div>
        ))}
      </div>

      <div className="space-y-3">
        <h3 className="type-subsection">BandStatusBadge — text always present</h3>
        <div className="flex flex-wrap gap-2">
          {STATUSES.map((s) => (
            <BandStatusBadge key={s} status={s} />
          ))}
        </div>
      </div>

      <div className="space-y-3">
        <h3 className="type-subsection">Money — never without its currency</h3>
        <div className="space-y-1">
          <div>
            <Money value={GBP("1284500.00")} size="figure-xl" />
          </div>
          <div>
            <Money value={GBP("142500.00")} size="figure-lg" />
          </div>
          <div>
            <Money value={{ amount: "87300.25", currency: "USD" }} />
          </div>
          <div>
            <Money value={{ amount: "1000000.00", currency: "JPY" }} />
          </div>
          <div>
            <Money value={{ amount: "4820000.00", currency: "USD" }} whole />{" "}
            <span className="type-caption text-muted-foreground">
              (aggregate ≥ 1,000,000 — whole units)
            </span>
          </div>
        </div>
      </div>

      <div className="space-y-3">
        <h3 className="type-subsection">Delta — sign, colour, both forms; zero is an em dash</h3>
        <div className="space-y-1">
          <div>
            <Delta amount={GBP("6000.00")} percent={4.4} />
          </div>
          <div>
            <Delta amount={GBP("-3200.00")} percent={-2.1} />
          </div>
          <div>
            <Delta percent={0} />
          </div>
          <div>
            <Delta percent={12.5} />
          </div>
          <div>
            <Delta amount={GBP("1500.00")} />
          </div>
        </div>
      </div>
    </section>
  );
}

export default function ComponentGalleryPage() {
  return (
    <main className="bg-content min-h-full p-8">
      <div className="mx-auto max-w-7xl space-y-6">
        <div>
          <h1 className="type-title">Compensation components</h1>
          <p className="type-caption text-muted-foreground mt-1">
            Money · Delta · BandStatusBadge · BandBar, every state in both themes. Build step P3.7.
          </p>
        </div>
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
          <Panel theme="app-light" label="Light" />
          <Panel theme="app-dark" label="Dark" />
        </div>
      </div>
    </main>
  );
}
