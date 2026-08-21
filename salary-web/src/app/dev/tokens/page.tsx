import { TOKEN_GROUPS } from "@/lib/design/tokens";

/**
 * Token audit — the Verify for build step P3.1.
 *
 * Renders every token from `theme.css` in BOTH themes on one page, so a value that is wrong,
 * missing, or illegible in dark is visible without toggling anything. `scripts/check-tokens.mjs`
 * guarantees the list below is complete.
 *
 * Development-only surface. It is not linked from the app shell.
 *
 * The `var(--token)` in the swatch style is a token *reference*, not a literal — it is the only
 * way to render a token whose name is data. No colour literal appears in this file.
 */

function Swatch({ token }: { token: string }) {
  return (
    <div className="flex items-center gap-3">
      <div
        aria-hidden
        className="border-border size-10 shrink-0 rounded-md border"
        style={{ background: `var(--${token})` }}
      />
      <code className="text-muted-foreground font-mono text-xs">--{token}</code>
    </div>
  );
}

function ThemePanel({ theme, label }: { theme: "app-light" | "app-dark"; label: string }) {
  return (
    <section className={`${theme} bg-background text-foreground rounded-lg border p-6`}>
      <header className="mb-6">
        <h2 className="text-lg font-semibold tracking-tight">{label}</h2>
        <p className="text-muted-foreground text-sm">
          <code className="font-mono">.{theme}</code>
        </p>
      </header>

      <div className="space-y-8">
        {TOKEN_GROUPS.map((group) => (
          <div key={group.title}>
            <h3 className="text-sm font-medium">{group.title}</h3>
            {group.note ? (
              <p className="text-muted-foreground mt-1 mb-3 text-xs">{group.note}</p>
            ) : (
              <div className="mb-3" />
            )}
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {group.tokens.map((token) => (
                <Swatch key={token} token={token} />
              ))}
            </div>
          </div>
        ))}

        <div>
          <h3 className="mb-3 text-sm font-medium">Radius</h3>
          <div className="flex items-center gap-3">
            <div
              className="border-border bg-card size-10 shrink-0 border"
              style={{ borderRadius: "var(--radius)" }}
            />
            <code className="text-muted-foreground font-mono text-xs">--radius</code>
          </div>
        </div>

        <div>
          <h3 className="mb-3 text-sm font-medium">Legibility spot-check</h3>
          <div className="space-y-1 text-sm">
            <p className="text-primary">--primary as text (links, active nav, deltas)</p>
            <p className="text-positive">--positive · an increase</p>
            <p className="text-attention">--attention · below band minimum</p>
            <p className="text-critical">--critical · above band maximum</p>
            <p className="text-neutral-figure font-mono tabular-nums">
              --neutral-figure · 128,400.00
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}

export default function TokenAuditPage() {
  return (
    <main className="bg-content min-h-full p-8">
      <div className="mx-auto max-w-6xl space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Design tokens</h1>
          <p className="text-muted-foreground text-sm">
            Every token in <code className="font-mono">src/app/theme.css</code>, both themes. Build
            step P3.1.
          </p>
        </div>
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <ThemePanel theme="app-light" label="Light" />
          <ThemePanel theme="app-dark" label="Dark" />
        </div>
      </div>
    </main>
  );
}
