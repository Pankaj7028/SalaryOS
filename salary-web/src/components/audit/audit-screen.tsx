"use client";

import { useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";
import { auditExportUrl } from "@/lib/api/audit";
import { useAuditEvents } from "@/lib/api/audit-queries";
import { EmptyState, ErrorState, TableSkeleton } from "@/components/feedback/states";

const COLUMNS = ["150px", "180px", "90px", "140px", "140px", "260px"];

/**
 * `/admin/audit` (ui doc §8.9, FR-7.4). Filter, and only filter, lives in `searchParams`
 * (CLAUDE.md §9) — a search this specific is exactly what an auditor sends a colleague. Clicking
 * an actor's email narrows to that actor, the same "click to filter" affordance the filter chips
 * elsewhere in the app use; there's no separate actor picker because `GET /api/admin/users` is
 * HR_ADMIN-only (P8.1) and this screen must also work for AUDITOR.
 */
export function AuditScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const actorUserId = searchParams.get("actorUserId") ?? "";
  const actorLabel = searchParams.get("actorLabel") ?? "";
  const entityType = searchParams.get("entityType") ?? "";
  const action = searchParams.get("action") ?? "";
  const fromDate = searchParams.get("fromDate") ?? "";
  const toDate = searchParams.get("toDate") ?? "";

  const [entityTypeDraft, setEntityTypeDraft] = useState(entityType);
  const [actionDraft, setActionDraft] = useState(action);

  function updateParams(next: Record<string, string | undefined>) {
    const params = new URLSearchParams(searchParams.toString());
    for (const [key, value] of Object.entries(next)) {
      if (value) params.set(key, value);
      else params.delete(key);
    }
    router.push(`${pathname}?${params.toString()}`, { scroll: false });
  }

  const from = fromDate ? `${fromDate}T00:00:00Z` : undefined;
  const to = toDate ? `${toDate}T23:59:59Z` : undefined;
  const filters = {
    actorUserId: actorUserId || undefined,
    entityType: entityType || undefined,
    action: action || undefined,
    from,
    to,
  };

  const events = useAuditEvents(filters);
  const exportUrl = auditExportUrl(filters);

  return (
    <div className="space-y-4">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="type-title">Audit log</h1>
          <p className="type-caption text-muted-foreground mt-1">
            {events.data ? `${events.data.length} events, this filter` : "Loading…"}
          </p>
        </div>
        <Button size="sm" variant="outline" asChild>
          <a href={exportUrl}>Export CSV</a>
        </Button>
      </header>

      <div className="flex flex-wrap items-end gap-2">
        {actorUserId ? (
          <div className="flex flex-col gap-1">
            <span className="type-label text-muted-foreground">Actor</span>
            <button
              type="button"
              onClick={() => updateParams({ actorUserId: undefined, actorLabel: undefined })}
              className="border-border bg-muted/40 hover:bg-muted inline-flex h-7 items-center gap-1.5 rounded-md border px-2.5 type-body-sm"
            >
              {actorLabel || actorUserId}
              <span aria-hidden>×</span>
            </button>
          </div>
        ) : null}
        <div className="flex flex-col gap-1">
          <label className="type-label text-muted-foreground" htmlFor="entityType">Entity type</label>
          <Input
            id="entityType"
            className="w-40"
            placeholder="e.g. EMPLOYEE"
            value={entityTypeDraft}
            onChange={(e) => setEntityTypeDraft(e.target.value)}
            onBlur={() => updateParams({ entityType: entityTypeDraft || undefined })}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label className="type-label text-muted-foreground" htmlFor="action">Action</label>
          <Input
            id="action"
            className="w-48"
            placeholder="e.g. UPDATE_EMPLOYEE"
            value={actionDraft}
            onChange={(e) => setActionDraft(e.target.value)}
            onBlur={() => updateParams({ action: actionDraft || undefined })}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label className="type-label text-muted-foreground" htmlFor="fromDate">From</label>
          <Input id="fromDate" type="date" value={fromDate} onChange={(e) => updateParams({ fromDate: e.target.value || undefined })} />
        </div>
        <div className="flex flex-col gap-1">
          <label className="type-label text-muted-foreground" htmlFor="toDate">To</label>
          <Input id="toDate" type="date" value={toDate} onChange={(e) => updateParams({ toDate: e.target.value || undefined })} />
        </div>
        {actorUserId || entityType || action || fromDate || toDate ? (
          <Button
            size="sm"
            variant="ghost"
            onClick={() => {
              setEntityTypeDraft("");
              setActionDraft("");
              router.push(pathname, { scroll: false });
            }}
          >
            Clear filters
          </Button>
        ) : null}
      </div>

      {events.isError ? (
        <ErrorState
          title="Couldn't load the audit log"
          detail={
            events.error instanceof ApiError && events.error.problem?.detail
              ? events.error.problem.detail
              : "Check your connection and try again."
          }
          action={<Button size="sm" variant="outline" onClick={() => events.refetch()}>Retry</Button>}
        />
      ) : events.isLoading ? (
        <TableSkeleton columns={COLUMNS} rows={10} />
      ) : (events.data ?? []).length === 0 ? (
        <EmptyState title="No matching events" detail="Widen the date range or clear a filter." />
      ) : (
        <div className="border-border overflow-x-auto rounded-lg border">
          <Table>
            <TableHeader className="bg-muted/40">
              <TableRow className="h-10">
                <TableHead className="type-label text-muted-foreground">Occurred at</TableHead>
                <TableHead className="type-label text-muted-foreground">Actor</TableHead>
                <TableHead className="type-label text-muted-foreground">Role</TableHead>
                <TableHead className="type-label text-muted-foreground">Action</TableHead>
                <TableHead className="type-label text-muted-foreground">Entity</TableHead>
                <TableHead className="type-label text-muted-foreground">Detail</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(events.data ?? []).map((event) => (
                <TableRow key={event.id} className="h-10">
                  <TableCell className="figure-sm whitespace-nowrap">{event.occurredAt}</TableCell>
                  <TableCell>
                    {event.actorUserId ? (
                      <button
                        type="button"
                        onClick={() =>
                          updateParams({
                            actorUserId: event.actorUserId ?? undefined,
                            actorLabel: event.actorEmail ?? event.actorUserId ?? undefined,
                          })
                        }
                        className="type-body-sm hover:underline"
                      >
                        {event.actorEmail ?? event.actorUserId}
                      </button>
                    ) : (
                      <span className="type-body-sm text-muted-foreground">—</span>
                    )}
                  </TableCell>
                  <TableCell className="type-caption text-muted-foreground">{event.actorRole}</TableCell>
                  <TableCell className="type-body-sm whitespace-nowrap">{event.action}</TableCell>
                  <TableCell className="type-body-sm whitespace-nowrap">{event.entityType}</TableCell>
                  <TableCell className="figure-sm text-muted-foreground max-w-xs truncate" title={event.entityId ?? undefined}>
                    {event.entityId ?? ""}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
