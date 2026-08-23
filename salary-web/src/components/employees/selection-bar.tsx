"use client";

import { Button } from "@/components/ui/button";

/**
 * P10.5 — what you can do with a selection, shown only once there is one.
 *
 * <p>It replaces the filter readout in place rather than appearing above it, so the row of controls
 * does not jump down the moment the first checkbox is ticked and move the next row you meant to
 * tick out from under the pointer.
 */
export function SelectionBar({
  selectedCount,
  pageCount,
  canPropose,
  onClear,
  onPropose,
}: {
  selectedCount: number;
  /** How many rows are on this page — selection does not survive paging, and says so. */
  pageCount: number;
  canPropose: boolean;
  onClear: () => void;
  onPropose: () => void;
}) {
  return (
    <div className="border-primary/30 bg-primary/5 flex flex-wrap items-center justify-between gap-x-4 gap-y-2 rounded-md border px-3 py-2">
      <p className="type-body-sm">
        <span className="figure-sm">{selectedCount}</span> of{" "}
        <span className="figure-sm">{pageCount}</span> on this page selected
      </p>
      <div className="flex items-center gap-2">
        {canPropose ? (
          <Button size="sm" onClick={onPropose}>
            Propose a change
          </Button>
        ) : null}
        <Button size="sm" variant="ghost" onClick={onClear}>
          Clear selection
        </Button>
      </div>
    </div>
  );
}
