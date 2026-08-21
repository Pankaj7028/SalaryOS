"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Money } from "@/components/comp/money";
import { EmptyState } from "@/components/feedback/states";
import { usePeers } from "@/lib/api/employees-queries";

/**
 * FR-6.6 (ui doc §7.8/§8.3): p25 / median / p75 and this person's percentile
 * within their (job level × country) cohort. Suppressed under 5 — the API
 * returns null figures in that case, never a computed value the panel could
 * accidentally show.
 */
export function PeersPanel({ employeeId }: { employeeId: string }) {
  const peers = usePeers(employeeId);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Peers</CardTitle>
      </CardHeader>
      <CardContent>
        {peers.isLoading ? (
          <div className="flex flex-col gap-2">
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-4 w-1/2" />
          </div>
        ) : peers.isError ? (
          <EmptyState title="Couldn't load peers" detail="Check your connection and try again." />
        ) : peers.data?.suppressed ? (
          <EmptyState
            title="Cohort too small to show"
            detail={`Only ${peers.data.cohortSize} ${peers.data.cohortSize === 1 ? "person shares" : "people share"} this level and country — fewer than 5 would risk identifying someone individually.`}
          />
        ) : peers.data ? (
          <div className="flex flex-col gap-4">
            <div className="flex gap-6">
              <div className="flex flex-col gap-0.5">
                <span className="type-label text-muted-foreground">p25</span>
                {peers.data.p25 ? <Money value={peers.data.p25} size="figure-sm" /> : null}
              </div>
              <div className="flex flex-col gap-0.5">
                <span className="type-label text-muted-foreground">Median</span>
                {peers.data.median ? <Money value={peers.data.median} size="figure-sm" /> : null}
              </div>
              <div className="flex flex-col gap-0.5">
                <span className="type-label text-muted-foreground">p75</span>
                {peers.data.p75 ? <Money value={peers.data.p75} size="figure-sm" /> : null}
              </div>
            </div>
            <p className="type-caption text-muted-foreground">
              This person sits at the <span className="figure-sm">{peers.data.percentile}th</span> percentile of{" "}
              {peers.data.cohortSize} peers at the same level and country.
            </p>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
