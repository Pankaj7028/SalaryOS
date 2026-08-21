/**
 * Type-scale audit — the Verify for build step P3.2.
 *
 * Proves the two things §3 is actually about: a column of mixed-width figures aligns on the
 * decimal, and the zero is slashed so it cannot be read as a letter O in an employee number.
 *
 * Development-only surface, not linked from the app shell.
 */

const SCALE = [
  ["figure-xl", "figure-xl", "30 / 36 · mono 500", "1,284,500.00"],
  ["figure-lg", "figure-lg", "20 / 28 · mono 500", "142,500.00"],
  ["figure", "figure", "13 / 18 · mono 400", "142,500.00"],
  ["figure-sm", "figure-sm", "12 / 16 · mono 400", "+4.2%"],
  ["type-title", "title", "20 / 28 · sans 600", "Employee detail"],
  ["type-section", "section", "16 / 24 · sans 600", "Current compensation"],
  ["type-subsection", "subsection", "14 / 20 · sans 600", "Band position"],
  ["type-body", "body", "14 / 20 · sans 400", "The default interface text."],
  ["type-body-sm", "body-sm", "13 / 18 · sans 400", "Table text and helper text."],
  ["type-label", "label", "12 / 16 · sans 500 · 0.06em", "Base salary"],
  ["type-caption", "caption", "12 / 16 · sans 400", "As at 21 Aug 2026 · USD"],
] as const;

/** Deliberately mixed widths — 2 to 10 digits — so misalignment would be obvious. */
const FIGURES = [
  ["9.50", "USD"],
  ["142,500.00", "USD"],
  ["1,284,500.00", "USD"],
  ["87,300.25", "GBP"],
  ["600.00", "EUR"],
  ["1,000,000.00", "JPY"],
] as const;

export default function TypeAuditPage() {
  return (
    <main className="bg-content min-h-full p-8">
      <div className="mx-auto max-w-4xl space-y-10">
        <div>
          <h1 className="type-title">Type scale</h1>
          <p className="type-caption text-muted-foreground">
            IBM Plex Sans / IBM Plex Mono. Build step P3.2.
          </p>
        </div>

        <section className="bg-card rounded-lg border p-6">
          <h2 className="type-section mb-4">Scale</h2>
          <table className="w-full">
            <tbody>
              {SCALE.map(([cls, name, spec, sample]) => (
                <tr key={cls} className="border-border border-b last:border-0">
                  <td className="w-32 py-3 align-middle">
                    <code className="type-caption text-muted-foreground font-mono">{name}</code>
                  </td>
                  <td className="w-56 py-3 align-middle">
                    <span className="type-caption text-muted-foreground">{spec}</span>
                  </td>
                  <td className="py-3 align-middle">
                    <span className={cls}>{sample}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="bg-card rounded-lg border p-6">
          <h2 className="type-section">Mixed-width figures align</h2>
          <p className="type-caption text-muted-foreground mt-1 mb-4">
            Right-aligned, tabular. The currency code is a separate muted span so the digits align
            across currencies.
          </p>
          <table className="w-full max-w-sm">
            <thead>
              <tr>
                <th className="type-label text-muted-foreground numeric pb-2">Base</th>
                <th className="type-label text-muted-foreground w-16 pb-2 pl-3 text-left">Ccy</th>
              </tr>
            </thead>
            <tbody>
              {FIGURES.map(([amount, ccy]) => (
                <tr key={amount + ccy}>
                  <td className="figure numeric py-1">{amount}</td>
                  <td className="figure-sm text-muted-foreground py-1 pl-3">{ccy}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="bg-card rounded-lg border p-6">
          <h2 className="type-section">Slashed zero</h2>
          <p className="type-caption text-muted-foreground mt-1 mb-4">
            Zero must be distinguishable from a capital O — employee numbers contain both.
          </p>
          <div className="space-y-2">
            <p className="figure-lg">0O 0O 0O</p>
            <p className="figure">EMP-0042-O0 · 00000 · OOOOO</p>
            <p className="type-body-sm text-muted-foreground">
              Same string in the sans face for comparison: EMP-0042-O0 · 00000 · OOOOO
            </p>
          </div>
        </section>

        <section className="bg-card rounded-lg border p-6">
          <h2 className="type-section mb-4">Delta conventions (§3.2)</h2>
          <div className="space-y-1">
            <p className="figure text-positive">+4.2% · an increase carries its sign</p>
            <p className="figure text-critical">−2.0% · a decrease</p>
            <p className="figure text-neutral-figure">— · zero reads as an em dash, never 0.0%</p>
            <p className="figure text-muted-foreground">0.94 · compa-ratio, always two decimals</p>
          </div>
        </section>
      </div>
    </main>
  );
}
