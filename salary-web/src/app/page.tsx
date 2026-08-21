import { Button } from "@/components/ui/button";

/**
 * P0.4 placeholder. The real application shell — topbar, sidebar, content — arrives at P3.3,
 * and this route becomes the Overview screen at P7.6.
 *
 * Every colour here comes from a design token, never a literal (CLAUDE.md §5).
 */
export default function Home() {
  return (
    <main className="bg-background text-foreground flex min-h-full flex-col items-center justify-center gap-6 p-8">
      <div className="space-y-2 text-center">
        <h1 className="text-2xl font-semibold tracking-tight">Salary OS</h1>
        <p className="text-muted-foreground text-sm">
          Compensation management for ACME. Scaffold only — the shell lands at P3.3.
        </p>
      </div>
      <Button size="sm" disabled>
        Sign in (P2.5)
      </Button>
    </main>
  );
}
