"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useDeleteSavedView, useSavedViews } from "@/lib/api/saved-views-queries";
import { SaveViewDialog } from "@/components/saved-views/save-view-dialog";
import type { SavedView } from "@/lib/api/saved-views";

/**
 * P10.4 — the saved-question picker, over the P10.3 library.
 *
 * <p>The `/employees` screen has carried a note since P9.6 that its spec'd saved-view select was
 * "deliberately not built: nothing backs it". This is the thing that backs it.
 *
 * <p>Route-scoped on purpose: a view saved on `/employees` is meaningless replayed against
 * `/insights`, and a picker that offers it is a picker that produces an empty screen and no
 * explanation. The list endpoint returns every view the user can see; the filtering to *this*
 * question's route happens here.
 */
export function SavedViewBar({
  route,
  currentQueryString,
}: {
  route: string;
  /** The live `searchParams` for this screen, no leading `?`. */
  currentQueryString: string;
}) {
  const router = useRouter();
  const savedViews = useSavedViews();
  const deleteView = useDeleteSavedView();
  const [saving, setSaving] = useState(false);

  const forThisRoute = (savedViews.data ?? []).filter((view) => view.route === route);
  const mine = forThisRoute.filter((view) => view.ownedByMe);
  const shared = forThisRoute.filter((view) => !view.ownedByMe);

  function apply(view: SavedView) {
    router.push(view.queryString ? `${view.route}?${view.queryString}` : view.route, { scroll: false });
  }

  return (
    <>
      <div className="flex items-center gap-2">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button size="sm" variant="outline">
              Saved views
              {forThisRoute.length > 0 ? (
                <span className="figure-sm text-muted-foreground ml-1.5">{forThisRoute.length}</span>
              ) : null}
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start" className="w-72">
            {forThisRoute.length === 0 ? (
              <p className="type-body-sm text-muted-foreground px-2 py-3">
                No saved views for this screen yet. Set your filters, then save the question.
              </p>
            ) : null}

            {mine.length > 0 ? (
              <>
                <DropdownMenuLabel className="type-label text-muted-foreground">Yours</DropdownMenuLabel>
                {mine.map((view) => (
                  <SavedViewRow
                    key={view.id}
                    view={view}
                    onApply={apply}
                    onDelete={(id) => deleteView.mutate(id)}
                  />
                ))}
              </>
            ) : null}

            {shared.length > 0 ? (
              <>
                {mine.length > 0 ? <DropdownMenuSeparator /> : null}
                <DropdownMenuLabel className="type-label text-muted-foreground">
                  Shared with you
                </DropdownMenuLabel>
                {shared.map((view) => (
                  <SavedViewRow key={view.id} view={view} onApply={apply} />
                ))}
              </>
            ) : null}
          </DropdownMenuContent>
        </DropdownMenu>

        <Button size="sm" variant="outline" onClick={() => setSaving(true)}>
          Save this view
        </Button>
      </div>

      <SaveViewDialog
        open={saving}
        onOpenChange={setSaving}
        route={route}
        queryString={currentQueryString}
        existingNames={mine.map((view) => view.name)}
      />
    </>
  );
}

function SavedViewRow({
  view,
  onApply,
  onDelete,
}: {
  view: SavedView;
  onApply: (view: SavedView) => void;
  onDelete?: (id: string) => void;
}) {
  return (
    <DropdownMenuItem
      onSelect={() => onApply(view)}
      className="flex items-start justify-between gap-2"
    >
      <span className="min-w-0">
        <span className="type-body-sm block truncate">{view.name}</span>
        <span className="type-caption text-muted-foreground block">
          {view.ownedByMe
            ? view.shared
              ? "Shared with everyone"
              : "Only you"
            : `Shared by ${view.ownerName}`}
        </span>
      </span>
      {onDelete ? (
        <button
          type="button"
          aria-label={`Delete saved view ${view.name}`}
          className="type-caption text-muted-foreground hover:text-critical focus-visible:ring-ring shrink-0 rounded px-1 focus-visible:ring-2 focus-visible:outline-none"
          onClick={(event) => {
            // Without this the row's onSelect also fires and navigates away mid-delete.
            event.preventDefault();
            event.stopPropagation();
            onDelete(view.id);
          }}
        >
          Delete
        </button>
      ) : null}
    </DropdownMenuItem>
  );
}
