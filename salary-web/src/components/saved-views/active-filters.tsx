"use client";

import { Button } from "@/components/ui/button";

export type ActiveFilter = {
  /** The `searchParams` key this chip clears. */
  param: string;
  /** What the filter is: "Department", "Band status". */
  label: string;
  /** The chosen value, already resolved to a human name — never a raw id. */
  value: string;
};

/**
 * P10.4 — the structured question readout that sits under the filter bar.
 *
 * <p>The filter controls are typed selects over ids the endpoints already accept (never free text,
 * so every cohort-suppression and RBAC guardrail stays in SQL), but a row of eight collapsed
 * `<Select>` triggers does not read as a *question*. This states the question in words — "Department
 * is Engineering, Band status is Below minimum" — which is what a person is actually about to save,
 * and what someone opening a shared view needs to see to trust the numbers under it.
 *
 * <p>Presentational only: it holds no state and resolves no ids. The screen owns the URL.
 */
export function ActiveFilters({
  filters,
  onClear,
  onClearAll,
}: {
  filters: ActiveFilter[];
  onClear: (param: string) => void;
  onClearAll: () => void;
}) {
  if (filters.length === 0) {
    return (
      <p className="type-caption text-muted-foreground">
        No filters — every employee you can see.
      </p>
    );
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="type-caption text-muted-foreground">Showing</span>
      {filters.map((filter) => (
        <span
          key={filter.param}
          className="border-border bg-muted/40 inline-flex items-center gap-1.5 rounded-full border py-1 pr-1 pl-3"
        >
          <span className="type-caption text-muted-foreground">{filter.label}</span>
          <span className="type-body-sm">{filter.value}</span>
          <button
            type="button"
            aria-label={`Clear ${filter.label} filter`}
            onClick={() => onClear(filter.param)}
            className="text-muted-foreground hover:bg-border hover:text-foreground focus-visible:ring-ring flex size-5 items-center justify-center rounded-full focus-visible:ring-2 focus-visible:outline-none"
          >
            <span aria-hidden>×</span>
          </button>
        </span>
      ))}
      <Button size="sm" variant="ghost" onClick={onClearAll}>
        Clear all
      </Button>
    </div>
  );
}
