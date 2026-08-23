"use client";

import Link from "next/link";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type Row,
} from "@tanstack/react-table";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { Checkbox } from "@/components/ui/checkbox";
import { Money } from "@/components/comp/money";
import { BandBar } from "@/components/comp/band-bar";
import { EmptyState, TableSkeleton } from "@/components/feedback/states";
import { formatCompaRatio } from "@/lib/money";
import type { EmployeeSummary } from "@/lib/api/employees";

/** Employment status is not a band status — no defined tone (CLAUDE.md §5.1), so it stays
 * plain text, dimmed only for a terminated employee (an existing, defined token). */
const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "Active",
  ON_LEAVE: "On leave",
  TERMINATED: "Terminated",
};

const SKELETON_COLUMNS = ["180px", "140px", "120px", "100px", "110px", "60px", "70px", "70px"];

type NameLookup = Map<string, string>;

/** Renders one named column's cell for a row — lets the 375px card layout (§12.10) reuse the
 * exact same formatting as the desktop table instead of duplicating it per field. */
function CellFor({ row, id }: { row: Row<EmployeeSummary>; id: string }) {
  const cell = row.getAllCells().find((c) => c.column.id === id);
  if (!cell) return null;
  return <>{flexRender(cell.column.columnDef.cell, cell.getContext())}</>;
}

export function EmployeesTable({
  data,
  isLoading,
  departmentNames,
  locationNames,
  jobLevelTitles,
  selection,
}: {
  data: EmployeeSummary[];
  isLoading: boolean;
  departmentNames: NameLookup;
  locationNames: NameLookup;
  jobLevelTitles: NameLookup;
  /** Omitted entirely for a role that cannot act on a selection — no dead checkboxes. */
  selection?: {
    selectedIds: Set<string>;
    onToggle: (id: string) => void;
    onToggleAll: () => void;
  };
}) {
  const allOnPageSelected =
    selection !== undefined && data.length > 0 && data.every((row) => selection.selectedIds.has(row.id));
  const someOnPageSelected =
    selection !== undefined && data.some((row) => selection.selectedIds.has(row.id));

  const columns: ColumnDef<EmployeeSummary>[] = [
    ...(selection
      ? [
          {
            id: "select",
            header: () => (
              <Checkbox
                checked={allOnPageSelected ? true : someOnPageSelected ? "indeterminate" : false}
                onCheckedChange={selection.onToggleAll}
                aria-label={allOnPageSelected ? "Clear selection on this page" : "Select every row on this page"}
              />
            ),
            cell: ({ row }: { row: Row<EmployeeSummary> }) => (
              <Checkbox
                checked={selection.selectedIds.has(row.original.id)}
                onCheckedChange={() => selection.onToggle(row.original.id)}
                aria-label={`Select ${row.original.firstName} ${row.original.lastName}`}
              />
            ),
          } satisfies ColumnDef<EmployeeSummary>,
        ]
      : []),
    {
      id: "name",
      header: "Name",
      cell: ({ row }) => {
        const employee = row.original;
        return (
          <div className="flex flex-col">
            <span className="type-body-sm text-foreground">
              {employee.firstName} {employee.lastName}
            </span>
            <span className="figure-sm text-muted-foreground">{employee.employeeNumber}</span>
          </div>
        );
      },
    },
    {
      id: "department",
      header: "Department",
      cell: ({ row }) => (
        <span className="type-body-sm">
          {row.original.departmentId ? (departmentNames.get(row.original.departmentId) ?? "—") : "—"}
        </span>
      ),
    },
    {
      id: "location",
      header: "Location",
      cell: ({ row }) => (
        <span className="type-body-sm">
          {row.original.locationId ? (locationNames.get(row.original.locationId) ?? "—") : "—"}
        </span>
      ),
    },
    {
      id: "level",
      header: "Level",
      cell: ({ row }) => (
        <span className="type-body-sm">
          {row.original.jobLevelId ? (jobLevelTitles.get(row.original.jobLevelId) ?? "—") : "—"}
        </span>
      ),
    },
    {
      id: "basePay",
      header: "Base pay",
      cell: ({ row }) =>
        row.original.currentBasePay ? (
          <Money value={row.original.currentBasePay} size="figure-sm" />
        ) : (
          <span className="figure-sm text-muted-foreground">—</span>
        ),
    },
    {
      id: "compaRatio",
      header: "Compa-ratio",
      cell: ({ row }) => (
        <span className="figure-sm">
          {row.original.compaRatio != null ? formatCompaRatio(Number(row.original.compaRatio)) : "—"}
        </span>
      ),
    },
    {
      id: "band",
      header: "Band",
      cell: ({ row }) => {
        const employee = row.original;
        if (!employee.currentBasePay) {
          return <span className="figure-sm text-muted-foreground">—</span>;
        }
        return (
          <BandBar
            variant="inline"
            salary={employee.currentBasePay}
            band={employee.band}
            position={{
              status: employee.bandStatus ?? "NO_BAND",
              percentThroughRange: Number(employee.rangePenetration ?? 0),
              compaRatio: Number(employee.compaRatio ?? 0),
            }}
          />
        );
      },
    },
    {
      id: "status",
      header: "Status",
      cell: ({ row }) => (
        <span
          className={
            row.original.status === "TERMINATED" ? "type-body-sm text-muted-foreground" : "type-body-sm"
          }
        >
          {STATUS_LABEL[row.original.status] ?? row.original.status}
        </span>
      ),
    },
  ];

  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getRowId: (row) => row.id,
  });

  if (isLoading && data.length === 0) {
    return (
      <>
        <div className="hidden md:block">
          <TableSkeleton columns={selection ? ["40px", ...SKELETON_COLUMNS] : SKELETON_COLUMNS} />
        </div>
        <div className="flex flex-col gap-3 md:hidden">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="border-border rounded-lg border p-5">
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="mt-2 h-3 w-1/3" />
              <Skeleton className="mt-4 h-3 w-full" />
            </div>
          ))}
        </div>
      </>
    );
  }

  if (data.length === 0) {
    return (
      <EmptyState
        title="No employees match this filter"
        detail="Try widening the department, location, or status filter, or clear the search."
      />
    );
  }

  return (
    <>
      <div className="border-border hidden overflow-hidden rounded-lg border md:block">
        <Table>
          <TableHeader className="bg-muted/40 sticky top-0 z-20">
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id} className="h-10">
                {headerGroup.headers.map((header) => (
                  <TableHead key={header.id} className="type-label text-muted-foreground">
                    {flexRender(header.column.columnDef.header, header.getContext())}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.map((row) => (
              <TableRow key={row.id} className="relative h-10">
                {row.getVisibleCells().map((cell, index) => (
                  <TableCell key={cell.id} className={cell.column.id === "select" ? "w-10" : undefined}>
                    {index === 0 ? (
                      <Link
                        href={`/employees/${row.original.id}`}
                        className="absolute inset-0 z-10"
                        aria-label={`Open ${row.original.firstName} ${row.original.lastName}`}
                      />
                    ) : null}
                    {/* The row-wide Link overlay sits at z-10 across every cell. The checkbox has
                        to sit above it, or ticking a row navigates to that employee instead. */}
                    <span className={cell.column.id === "select" ? "relative z-20 flex" : "relative z-0"}>
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </span>
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <ul className="flex flex-col gap-3 md:hidden" aria-label="Employees">
        {table.getRowModel().rows.map((row) => (
          <li key={row.id}>
            <Link
              href={`/employees/${row.original.id}`}
              className="border-border bg-card hover:bg-muted/40 focus-visible:ring-ring/50 flex flex-col gap-3 rounded-lg border p-5 focus-visible:ring-3 focus-visible:outline-none"
            >
              <div className="flex items-start justify-between gap-2">
                <CellFor row={row} id="name" />
                <CellFor row={row} id="status" />
              </div>
              <div className="type-body-sm text-muted-foreground flex flex-wrap gap-x-3 gap-y-1">
                <CellFor row={row} id="department" />
                <CellFor row={row} id="location" />
                <CellFor row={row} id="level" />
              </div>
              <div className="flex items-end justify-between gap-3">
                <div className="flex flex-col gap-1">
                  <CellFor row={row} id="basePay" />
                  <CellFor row={row} id="compaRatio" />
                </div>
                <CellFor row={row} id="band" />
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
