"use client";

import { useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { ChangesTable } from "@/components/changes/changes-table";
import { RejectChangeDialog } from "@/components/changes/reject-change-dialog";
import {
  useApproveChange,
  useChanges,
  useDiscardDraft,
  useSubmitDraft,
} from "@/lib/api/changes-queries";
import { useSession } from "@/lib/auth/auth-queries";
import { canApproveChanges } from "@/lib/auth/roles";
import type { Change, ChangeStatus } from "@/lib/api/changes";

const TABS: { tab: string; label: string; status: ChangeStatus }[] = [
  { tab: "pending", label: "Awaiting approval", status: "PENDING" },
  { tab: "approved", label: "Approved", status: "APPROVED" },
  { tab: "applied", label: "Applied", status: "APPLIED" },
  { tab: "rejected", label: "Rejected", status: "REJECTED" },
  { tab: "draft", label: "Drafts", status: "DRAFT" },
];

/**
 * `/changes` (ui doc §8.5). Tab state lives in `?tab=` (CLAUDE.md §9) — reloading or sharing the
 * link lands on the same status filter. Approve/reject render inline on the Awaiting-approval tab,
 * gated by `canApproveChanges` (roles.ts, CLAUDE.md §7) — a role that cannot decide sees the tab
 * and every row, just without the action column, never a disabled button with a tooltip. Submit/
 * discard render on the Drafts tab for anyone who can reach this screen at all (the same roles who
 * may propose): `ProposeChangeDialog` (P6.4) only ever creates a DRAFT, so this is how one actually
 * reaches the approval queue.
 */
export function ChangesScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const tab = searchParams.get("tab") ?? "pending";
  const active = TABS.find((t) => t.tab === tab) ?? TABS[0];

  const session = useSession();
  const canApprove = session.data ? canApproveChanges(session.data.role) : false;

  const changes = useChanges(active.status);
  const approveChange = useApproveChange();
  const submitDraft = useSubmitDraft();
  const discardDraft = useDiscardDraft();
  const [rejecting, setRejecting] = useState<Change | null>(null);

  function setTab(next: string) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("tab", next);
    router.push(`${pathname}?${params.toString()}`, { scroll: false });
  }

  const showPendingActions = active.status === "PENDING" && canApprove;
  const showDraftActions = active.status === "DRAFT";

  return (
    <div className="space-y-4">
      <header>
        <h1 className="type-title">Changes</h1>
        <p className="type-caption text-muted-foreground mt-1">
          Proposed compensation changes, by status.
        </p>
      </header>

      <Tabs value={tab} onValueChange={setTab}>
        <TabsList>
          {TABS.map((t) => (
            <TabsTrigger key={t.tab} value={t.tab}>
              {t.label}
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value={tab} className="mt-4">
          <ChangesTable
            changes={changes.data ?? []}
            isLoading={changes.isLoading}
            isError={changes.isError}
            emptyTitle={`No changes ${active.label.toLowerCase()}`}
            onRetry={() => changes.refetch()}
            showActions={showPendingActions || showDraftActions}
            actionsLabel={showDraftActions ? "Actions" : "Decision"}
            renderActions={(change) =>
              showPendingActions ? (
                <>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => approveChange.mutate({ id: change.id })}
                    disabled={approveChange.isPending}
                  >
                    Approve
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => setRejecting(change)}>
                    Reject
                  </Button>
                </>
              ) : showDraftActions ? (
                <>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => submitDraft.mutate(change.id)}
                    disabled={submitDraft.isPending}
                  >
                    Submit
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => discardDraft.mutate(change.id)}
                    disabled={discardDraft.isPending}
                  >
                    Discard
                  </Button>
                </>
              ) : null
            }
          />
        </TabsContent>
      </Tabs>

      {rejecting ? (
        <RejectChangeDialog open onOpenChange={(open) => !open && setRejecting(null)} change={rejecting} />
      ) : null}
    </div>
  );
}
