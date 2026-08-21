import { Button } from "@/components/ui/button";

/**
 * Overview placeholder. Becomes the real Overview screen at P7.6.
 *
 * Follows the page-header pattern from §6.4: title, a one-line caption stating
 * the basis of what is on screen, and at most one primary action on the right.
 */
export default function OverviewPage() {
  return (
    <div className="space-y-6">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="type-title">Overview</h1>
          <p className="type-caption text-muted-foreground mt-1">
            Shell scaffold — no data is connected yet. Insight cards arrive at P7.6.
          </p>
        </div>
        <Button size="sm" disabled>
          Propose change
        </Button>
      </header>

      <div className="bg-card text-card-foreground rounded-lg border p-6">
        <h2 className="type-section">Build step P3.3</h2>
        <p className="type-body text-muted-foreground mt-2">
          The application shell is in place: sticky topbar, collapsible sidebar whose state survives
          a reload, and a Sheet under 768px. The topbar&rsquo;s search, currency toggle, theme menu
          and avatar are built next, at P3.4.
        </p>
      </div>
    </div>
  );
}
