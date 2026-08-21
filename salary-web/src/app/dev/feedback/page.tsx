"use client";

import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, GoodNewsState, TableSkeleton } from "@/components/feedback/states";
import { bulk, failure, info, success, warning } from "@/lib/notify";

/**
 * Feedback audit — the Verify for build step P3.6.
 *
 * Every toast below goes through @/lib/notify, never sonner directly; that is
 * asserted by src/lib/notify.test.ts. Development-only surface.
 */
export default function FeedbackAuditPage() {
  return (
    <main className="bg-content min-h-full p-8">
      <div className="mx-auto max-w-3xl space-y-8">
        <div>
          <h1 className="type-title">Feedback</h1>
          <p className="type-caption text-muted-foreground mt-1">
            Toasts and the loading / empty / error primitives. Build step P3.6.
          </p>
        </div>

        <section className="bg-card space-y-3 rounded-lg border p-6">
          <h2 className="type-section">Toasts</h2>
          <p className="type-caption text-muted-foreground">
            Summary is a past-tense outcome in 2–4 words with no full stop. Dwell: 3s / 4s / 5s /
            6s.
          </p>
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              onClick={() => success("Change approved", "Ada Okonkwo · effective 1 Sep 2026")}
            >
              Success
            </Button>
            <Button
              size="sm"
              variant="secondary"
              onClick={() => info("Export queued", "1,284 rows · USD")}
            >
              Info
            </Button>
            <Button
              size="sm"
              variant="secondary"
              onClick={() => warning("Below band minimum", "A note is required outside the band")}
            >
              Warning
            </Button>
            <Button
              size="sm"
              variant="destructive"
              onClick={() =>
                failure({ detail: "Effective date precedes the last change" }, "Couldn’t save")
              }
            >
              Failure
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => bulk("Merit upload processed", { done: 412, rejected: 18 })}
            >
              Bulk (one toast)
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => failure(new Error(""), "Couldn’t load")}
            >
              Failure, no reason given
            </Button>
          </div>
        </section>

        <section className="space-y-3">
          <h2 className="type-section">Loading — geometry matches the real table</h2>
          <TableSkeleton columns={["18%", "22%", "14%", "12%", "20%"]} rows={5} />
        </section>

        <section className="space-y-3">
          <h2 className="type-section">Empty — an invitation, not an apology</h2>
          <EmptyState
            title="No employees match these filters"
            detail="Clear the country filter to widen the search, or reset every filter to see all 10,000."
            action={
              <Button size="sm" variant="secondary">
                Clear country filter
              </Button>
            }
          />
        </section>

        <section className="space-y-3">
          <h2 className="type-section">Zero that is a real answer</h2>
          <GoodNewsState
            title="Everyone is inside their band"
            population="Checked 9,842 employees with a band assigned · as at 21 Aug 2026"
          />
        </section>

        <section className="space-y-3">
          <h2 className="type-section">Error — names the failure and the fix</h2>
          <ErrorState
            title="Couldn’t load pay analysis"
            detail="The report timed out. Narrow the date range and try again."
            action={
              <Button size="sm" variant="secondary">
                Retry with last 12 months
              </Button>
            }
          />
        </section>
      </div>
    </main>
  );
}
