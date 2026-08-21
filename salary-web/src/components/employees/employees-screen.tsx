"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ApiError } from "@/lib/api/client";
import { employeesExportUrl } from "@/lib/api/employees";
import { useEmployees } from "@/lib/api/employees-queries";
import { useCountries, useDepartments, useJobLevels, useLocations } from "@/lib/api/reference-queries";
import { EmployeesTable } from "@/components/employees/employees-table";
import { ErrorState } from "@/components/feedback/states";

/**
 * `/employees` (ui doc §8.2). Filter, sort, and page state all live in
 * `searchParams` (CLAUDE.md §9) — nothing here is component state that a
 * reload would lose.
 *
 * Two spec items are deliberately not built: a "saved-view select" (nothing
 * backs it — no view-persistence model exists anywhere in the API) and bulk
 * select → "Propose changes for selected" (that flow is P6.4 and doesn't
 * exist yet; a button with no action is worse than no button). A "band
 * status" filter is also omitted — `GET /api/employees` has no such
 * parameter, and filtering only the current page client-side would silently
 * misreport the true result set. Column sort is likewise not wired: the
 * service sorts a fixed `lastName, id` and keyset cursors are tied to that
 * order, so arbitrary column sort needs backend work this pass didn't do.
 * "Page 4" navigation isn't possible either — `KeysetPage` carries no total
 * count. Next uses the returned cursor; Previous uses browser history, which
 * only works for in-app back navigation, not a pasted mid-list link.
 */

const PAGE_SIZE_OPTIONS = [25, 50, 100, 200];
const ALL = "__all__";
const SEARCH_DEBOUNCE_MS = 300;

export function EmployeesScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const q = searchParams.get("q") ?? "";
  const departmentId = searchParams.get("departmentId") ?? "";
  const locationId = searchParams.get("locationId") ?? "";
  const countryCode = searchParams.get("countryCode") ?? "";
  const jobLevelId = searchParams.get("jobLevelId") ?? "";
  const status = searchParams.get("status") ?? "";
  const cursor = searchParams.get("cursor") ?? "";
  const limit = Number(searchParams.get("limit") ?? "50");

  const [searchDraft, setSearchDraft] = useState(q);
  const [syncedQ, setSyncedQ] = useState(q);
  if (q !== syncedQ) {
    setSyncedQ(q);
    setSearchDraft(q);
  }

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => () => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
  }, []);

  function updateParams(next: Record<string, string | undefined>, opts?: { resetCursor?: boolean }) {
    const params = new URLSearchParams(searchParams.toString());
    for (const [key, value] of Object.entries(next)) {
      if (value) params.set(key, value);
      else params.delete(key);
    }
    if (opts?.resetCursor !== false) params.delete("cursor");
    router.push(`${pathname}?${params.toString()}`, { scroll: false });
  }

  function handleSearchChange(value: string) {
    setSearchDraft(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => updateParams({ q: value || undefined }), SEARCH_DEBOUNCE_MS);
  }

  const departments = useDepartments();
  const locations = useLocations();
  const jobLevels = useJobLevels();
  const countries = useCountries();
  const employees = useEmployees({ q, departmentId, locationId, countryCode, jobLevelId, status, cursor, limit });

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

  const exportUrl = employeesExportUrl({ q, departmentId, locationId, countryCode, jobLevelId, status });

  return (
    <div className="space-y-4">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="type-title">Employees</h1>
          <p className="type-caption text-muted-foreground mt-1">
            {employees.data ? `${employees.data.items.length} shown, this filter` : "Loading…"}
          </p>
        </div>
        <Button size="sm" variant="outline" asChild>
          <a href={exportUrl}>Export CSV</a>
        </Button>
      </header>

      <div className="flex flex-wrap items-center gap-2">
        <Input
          placeholder="Search name, employee #, email"
          value={searchDraft}
          onChange={(event) => handleSearchChange(event.target.value)}
          className="w-64"
        />
        <Select
          value={departmentId || ALL}
          onValueChange={(value) => updateParams({ departmentId: value === ALL ? undefined : value })}
        >
          <SelectTrigger size="sm"><SelectValue placeholder="Department" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All departments</SelectItem>
            {(departments.data ?? []).map((d) => (
              <SelectItem key={d.id} value={d.id}>{d.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={locationId || ALL}
          onValueChange={(value) => updateParams({ locationId: value === ALL ? undefined : value })}
        >
          <SelectTrigger size="sm"><SelectValue placeholder="Location" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All locations</SelectItem>
            {(locations.data ?? []).map((l) => (
              <SelectItem key={l.id} value={l.id}>{l.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={countryCode || ALL}
          onValueChange={(value) => updateParams({ countryCode: value === ALL ? undefined : value })}
        >
          <SelectTrigger size="sm"><SelectValue placeholder="Country" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All countries</SelectItem>
            {(countries.data ?? []).map((c) => (
              <SelectItem key={c.code} value={c.code}>{c.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={jobLevelId || ALL}
          onValueChange={(value) => updateParams({ jobLevelId: value === ALL ? undefined : value })}
        >
          <SelectTrigger size="sm"><SelectValue placeholder="Level" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All levels</SelectItem>
            {(jobLevels.data ?? []).map((l) => (
              <SelectItem key={l.id} value={l.id}>{l.title}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={status || ALL} onValueChange={(value) => updateParams({ status: value === ALL ? undefined : value })}>
          <SelectTrigger size="sm"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            <SelectItem value="ACTIVE">Active</SelectItem>
            <SelectItem value="ON_LEAVE">On leave</SelectItem>
            <SelectItem value="TERMINATED">Terminated</SelectItem>
          </SelectContent>
        </Select>
        <Select value={String(limit)} onValueChange={(value) => updateParams({ limit: value })}>
          <SelectTrigger size="sm"><SelectValue /></SelectTrigger>
          <SelectContent>
            {PAGE_SIZE_OPTIONS.map((n) => (
              <SelectItem key={n} value={String(n)}>{n} / page</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {employees.isError ? (
        <ErrorState
          title="Couldn't load employees"
          detail={
            employees.error instanceof ApiError && employees.error.problem?.detail
              ? employees.error.problem.detail
              : "Check your connection and try again."
          }
          action={<Button size="sm" variant="outline" onClick={() => employees.refetch()}>Retry</Button>}
        />
      ) : (
        <EmployeesTable
          data={employees.data?.items ?? []}
          isLoading={employees.isLoading}
          departmentNames={departmentNames}
          locationNames={locationNames}
          jobLevelTitles={jobLevelTitles}
        />
      )}

      <div className="flex items-center justify-between">
        <Button size="sm" variant="outline" disabled={!cursor} onClick={() => router.back()}>
          Previous
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={!employees.data?.nextCursor}
          onClick={() =>
            updateParams({ cursor: employees.data?.nextCursor ?? undefined }, { resetCursor: false })
          }
        >
          Next
        </Button>
      </div>
    </div>
  );
}
