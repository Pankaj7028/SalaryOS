"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Search } from "lucide-react";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { NAV_GROUPS } from "@/lib/nav";
import { useEmployeeSearch } from "@/lib/api/employees-queries";
import { useDepartments, useJobLevels, useLocations } from "@/lib/api/reference-queries";

const SEARCH_DEBOUNCE_MS = 300;

/**
 * ⌘K palette (§6.1). Looking up one person is the most common task in this product, so it must
 * never require the list page.
 *
 * <p><b>Live, against the real employee search — the stub this replaced never was.</b> It rendered
 * three hardcoded names under a heading that said so ("sample data — live search lands at P4") and
 * every result's `onSelect` just closed the dialog. P4 shipped the employees module and the search
 * API this needed long ago; the palette was never revisited to use it. Found in QA: clicking a
 * result did nothing, because there was nothing behind it to click.
 *
 * <p><b>`shouldFilter={false}` on the dialog, and both groups filter themselves.</b> People come
 * from the server, already matched by `q` and ranked by it — cmdk's own fuzzy filter re-scoring an
 * already-filtered result set would be redundant at best and could hide a real match at worst.
 * Workspace items are filtered here by a plain substring test instead, so both groups are governed
 * by logic this file owns rather than by a scorer neither group was written against.
 */
export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const router = useRouter();

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => () => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
  }, []);

  function handleQueryChange(value: string) {
    setQuery(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => setDebouncedQuery(value), SEARCH_DEBOUNCE_MS);
  }

  // Cleared on close so reopening never flashes the previous search's results for one frame.
  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (!next) {
      setQuery("");
      setDebouncedQuery("");
    }
  }

  function go(href: string) {
    handleOpenChange(false);
    router.push(href);
  }

  const results = useEmployeeSearch(debouncedQuery);
  const departments = useDepartments();
  const locations = useLocations();
  const jobLevels = useJobLevels();

  const departmentNames = useMemo(
    () => new Map((departments.data ?? []).map((d) => [d.id, d.name])),
    [departments.data],
  );
  const locationNames = useMemo(
    () => new Map((locations.data ?? []).map((l) => [l.id, l.name])),
    [locations.data],
  );
  const jobLevelTitles = useMemo(
    () => new Map((jobLevels.data ?? []).map((l) => [l.id, l.title])),
    [jobLevels.data],
  );

  const trimmed = query.trim();
  const isSearching = trimmed.length > 0;
  const loadingFirstPage = isSearching && results.isFetching && results.data === undefined;
  const people = results.data?.items ?? [];

  const navItems = isSearching
    ? NAV_GROUPS.flatMap((group) =>
        group.items.filter((item) => item.label.toLowerCase().includes(trimmed.toLowerCase())),
      )
    : null;

  const nothingFound = isSearching && !loadingFirstPage && people.length === 0 && navItems?.length === 0;

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="border-border text-muted-foreground hover:bg-accent focus-visible:ring-ring type-body-sm hidden h-8 w-[320px] items-center justify-between rounded-md border px-3 outline-none focus-visible:ring-2 lg:flex"
      >
        <span className="flex items-center gap-2">
          <Search aria-hidden className="size-3.5" />
          Search employees
        </span>
        <kbd className="figure-sm text-muted-foreground">⌘K</kbd>
      </button>

      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="Search employees"
        className="hover:bg-accent focus-visible:ring-ring grid size-8 place-items-center rounded-md outline-none focus-visible:ring-2 lg:hidden"
      >
        <Search aria-hidden className="size-4" />
      </button>

      <CommandDialog
        open={open}
        onOpenChange={handleOpenChange}
        title="Search"
        description="Search employees, or jump to a section."
        shouldFilter={false}
      >
        <CommandInput
          value={query}
          onValueChange={handleQueryChange}
          placeholder="Search employees by name, number or email…"
        />
        <CommandList>
          {nothingFound ? <CommandEmpty>No employees or sections match “{trimmed}”.</CommandEmpty> : null}

          {loadingFirstPage ? (
            <CommandGroup heading="People">
              <div className="type-caption text-muted-foreground px-2 py-3">Searching…</div>
            </CommandGroup>
          ) : people.length > 0 ? (
            <CommandGroup heading="People">
              {people.map((employee) => {
                const roleAndPlace = [
                  employee.jobLevelId ? jobLevelTitles.get(employee.jobLevelId) : null,
                  employee.locationId ? locationNames.get(employee.locationId) : null,
                ]
                  .filter(Boolean)
                  .join(" · ");
                return (
                  <CommandItem
                    key={employee.id}
                    value={employee.id}
                    onSelect={() => go(`/employees/${employee.id}`)}
                  >
                    <div className="flex w-full items-center justify-between gap-3">
                      <span className="type-body-sm">
                        {employee.firstName} {employee.lastName}
                      </span>
                      <span className="figure-sm text-muted-foreground">{employee.employeeNumber}</span>
                    </div>
                    <span className="type-caption text-muted-foreground">
                      {roleAndPlace ||
                        (employee.departmentId ? departmentNames.get(employee.departmentId) : null) ||
                        employee.workEmail}
                    </span>
                  </CommandItem>
                );
              })}
            </CommandGroup>
          ) : null}

          {navItems ? (
            navItems.length > 0 ? (
              <CommandGroup heading="Workspace">
                {navItems.map((item) => (
                  <CommandItem key={item.href} value={item.href} onSelect={() => go(item.href)}>
                    <item.icon aria-hidden className="size-4" />
                    <span className="type-body-sm">{item.label}</span>
                  </CommandItem>
                ))}
              </CommandGroup>
            ) : null
          ) : (
            NAV_GROUPS.map((group) => (
              <CommandGroup key={group.caption} heading={group.caption}>
                {group.items.map((item) => (
                  <CommandItem key={item.href} value={item.href} onSelect={() => go(item.href)}>
                    <item.icon aria-hidden className="size-4" />
                    <span className="type-body-sm">{item.label}</span>
                  </CommandItem>
                ))}
              </CommandGroup>
            ))
          )}
        </CommandList>
      </CommandDialog>
    </>
  );
}
