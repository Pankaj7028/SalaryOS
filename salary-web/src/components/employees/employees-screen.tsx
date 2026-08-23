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
import { CreateEmployeeDialog } from "@/components/employees/create-employee-dialog";
import { ErrorState } from "@/components/feedback/states";
import { useSession } from "@/lib/auth/auth-queries";
import { canManageEmployees, canProposeChanges } from "@/lib/auth/roles";
import { ListPagination } from "@/components/employees/list-pagination";
import { SelectionBar } from "@/components/employees/selection-bar";
import { BulkProposeDialog } from "@/components/employees/bulk-propose-dialog";
import { SavedViewBar } from "@/components/saved-views/saved-view-bar";
import { ActiveFilters, type ActiveFilter } from "@/components/saved-views/active-filters";

/**
 * `/employees` (ui doc §8.2). Filter, sort, and page state all live in
 * `searchParams` (CLAUDE.md §9) — nothing here is component state that a
 * reload would lose. That is also what makes a view savable at all: P10.4's
 * picker stores this screen's query string verbatim and replays it, so the
 * URL *is* the saved question.
 *
 * The saved-view select the spec asked for is now built (P10.4) over the
 * P10.3 library. Bulk select → "Propose changes for selected" is P6.4 and
 * doesn't exist yet; a button with no action is worse than no button. "Page 4"
 * navigation isn't possible either — `KeysetPage` carries no total count.
 * Next uses the returned cursor; Previous uses browser history, which only
 * works for in-app back navigation, not a pasted mid-list link.
 *
 * Band-status filter and compa-ratio sort (both server-side, real keyset
 * pagination over the full 10k, not a client-side filter of one page) were
 * added post-P9.6 — `GET /api/employees` now takes `bandStatus` and
 * `sortBy=compaRatio`; see `EmployeeService`'s javadoc for why the latter is
 * a hand-rolled native query rather than the default keyset `Sort` path.
 */

const PAGE_SIZE_OPTIONS = [25, 50, 100, 200];
const ALL = "__all__";
const SEARCH_DEBOUNCE_MS = 300;
const ROUTE = "/employees";

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: "Active",
  ON_LEAVE: "On leave",
  TERMINATED: "Terminated",
};

const BAND_STATUS_LABELS: Record<string, string> = {
  IN_BAND: "In band",
  BELOW_MIN: "Below minimum",
  ABOVE_MAX: "Above maximum",
  NO_BAND: "No band",
};

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
  const bandStatus = searchParams.get("bandStatus") ?? "";
  const sortBy = searchParams.get("sortBy") ?? "";
  const cursor = searchParams.get("cursor") ?? "";
  const offset = Number(searchParams.get("offset") ?? "0");
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
    if (opts?.resetCursor !== false) {
      params.delete("cursor");
      // A row offset is a position in the *old* result set. Kept across a filter change it opens
      // row 4,000 of a list that now has 300 rows, which reads as an empty screen, not a filter.
      params.delete("offset");
    }
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
  const employees = useEmployees({ q, departmentId, locationId, countryCode, jobLevelId, status, bandStatus, sortBy, cursor, offset, limit });

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

  const exportUrl = employeesExportUrl({ q, departmentId, locationId, countryCode, jobLevelId, status, bandStatus });
  const session = useSession();
  const canManage = session.data ? canManageEmployees(session.data.role) : false;
  const [creating, setCreating] = useState(false);

  const countryNames = useMemo(
    () => new Map((countries.data ?? []).map((c) => [c.code, c.name])),
    [countries.data],
  );

  // The question in words. Ids are resolved to names here rather than in the chip component —
  // a chip reading "Department is 7f3a…" is not a question anyone can check before saving it.
  const activeFilters: ActiveFilter[] = [
    q ? { param: "q", label: "Matching", value: q } : null,
    departmentId
      ? { param: "departmentId", label: "Department", value: departmentNames.get(departmentId) ?? departmentId }
      : null,
    locationId
      ? { param: "locationId", label: "Location", value: locationNames.get(locationId) ?? locationId }
      : null,
    countryCode
      ? { param: "countryCode", label: "Country", value: countryNames.get(countryCode) ?? countryCode }
      : null,
    jobLevelId
      ? { param: "jobLevelId", label: "Level", value: jobLevelTitles.get(jobLevelId) ?? jobLevelId }
      : null,
    status ? { param: "status", label: "Status", value: STATUS_LABELS[status] ?? status } : null,
    bandStatus
      ? { param: "bandStatus", label: "Band status", value: BAND_STATUS_LABELS[bandStatus] ?? bandStatus }
      : null,
  ].filter((filter): filter is ActiveFilter => filter !== null);

  // Selection is deliberately NOT in the URL, unlike every filter on this screen (CLAUDE.md §9).
  // A filter is the question and belongs in a link you can send someone; a selection is a gesture
  // mid-task, and 200 UUIDs in a query string is not a link anyone can send. It is cleared on any
  // navigation for the same reason — a checkbox ticked on page 3 that survives to page 7 is a
  // proposal you did not know you were making.
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [bulkProposing, setBulkProposing] = useState(false);

  const pageIds = employees.data?.items.map((employee) => employee.id) ?? [];
  const [syncedPageKey, setSyncedPageKey] = useState("");
  const pageKey = `${cursor}|${offset}|${searchParams.toString()}`;
  if (pageKey !== syncedPageKey) {
    setSyncedPageKey(pageKey);
    if (selectedIds.size > 0) setSelectedIds(new Set());
  }

  function toggleOne(id: string) {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleAllOnPage() {
    setSelectedIds((current) => {
      const allSelected = pageIds.length > 0 && pageIds.every((id) => current.has(id));
      return allSelected ? new Set() : new Set(pageIds);
    });
  }

  const canPropose = session.data ? canProposeChanges(session.data.role) : false;

  function clearAllFilters() {
    updateParams({
      q: undefined,
      departmentId: undefined,
      locationId: undefined,
      countryCode: undefined,
      jobLevelId: undefined,
      status: undefined,
      bandStatus: undefined,
    });
  }

  return (
    <div className="space-y-4">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="type-title">Employees</h1>
          <p className="type-caption text-muted-foreground mt-1">
            {employees.data ? `${employees.data.items.length} shown, this filter` : "Loading…"}
          </p>
        </div>
        <div className="flex gap-2">
          <Button size="sm" variant="outline" asChild>
            <a href={exportUrl}>Export CSV</a>
          </Button>
          {canManage ? (
            <Button size="sm" onClick={() => setCreating(true)}>New employee</Button>
          ) : null}
        </div>
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
        <Select
          value={bandStatus || ALL}
          onValueChange={(value) => updateParams({ bandStatus: value === ALL ? undefined : value })}
        >
          <SelectTrigger size="sm"><SelectValue placeholder="Band status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Any band status</SelectItem>
            <SelectItem value="IN_BAND">In band</SelectItem>
            <SelectItem value="BELOW_MIN">Below minimum</SelectItem>
            <SelectItem value="ABOVE_MAX">Above maximum</SelectItem>
            <SelectItem value="NO_BAND">No band</SelectItem>
          </SelectContent>
        </Select>
        <Select
          value={sortBy || "lastName"}
          onValueChange={(value) => updateParams({ sortBy: value === "lastName" ? undefined : value })}
        >
          <SelectTrigger size="sm"><SelectValue placeholder="Sort" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="lastName">Sort: last name</SelectItem>
            <SelectItem value="compaRatio">Sort: compa-ratio (highest first)</SelectItem>
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

      {selectedIds.size > 0 ? (
        <SelectionBar
          selectedCount={selectedIds.size}
          pageCount={pageIds.length}
          canPropose={canPropose}
          onClear={() => setSelectedIds(new Set())}
          onPropose={() => setBulkProposing(true)}
        />
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
          <ActiveFilters
            filters={activeFilters}
            onClear={(param) => updateParams({ [param]: undefined })}
            onClearAll={clearAllFilters}
          />
          <SavedViewBar route={ROUTE} currentQueryString={searchParams.toString()} />
        </div>
      )}

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
          selection={
            canPropose
              ? { selectedIds, onToggle: toggleOne, onToggleAll: toggleAllOnPage }
              : undefined
          }
        />
      )}

      <ListPagination
        // Where this page starts is only knowable when it was reached by offset. A cursor walk
        // does not carry a row index, so the count reads "1-50 of 9,847" on a cursor page and
        // names the real position on a jumped-to one -- honest either way, and never a guess.
        pageStart={offset}
        pageSize={limit}
        itemCount={employees.data?.items.length ?? 0}
        totalCount={employees.data?.totalCount ?? 0}
        hasNext={Boolean(employees.data?.nextCursor)}
        hasPrevious={Boolean(cursor) || offset > 0}
        onPrevious={() => router.back()}
        onNext={() =>
          updateParams(
            { cursor: employees.data?.nextCursor ?? undefined, offset: undefined },
            { resetCursor: false },
          )
        }
        onJump={(nextOffset) =>
          updateParams({ offset: String(nextOffset), cursor: undefined }, { resetCursor: false })
        }
      />

      {canManage ? <CreateEmployeeDialog open={creating} onOpenChange={setCreating} /> : null}
      {canPropose ? (
        <BulkProposeDialog
          open={bulkProposing}
          onOpenChange={setBulkProposing}
          employeeIds={Array.from(selectedIds)}
          onProposed={() => setSelectedIds(new Set())}
        />
      ) : null}
    </div>
  );
}
