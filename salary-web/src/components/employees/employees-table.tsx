"use client";

import Link from "next/link";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
} from "@tanstack/react-table";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
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

export function EmployeesTable({
  data,
  isLoading,
  departmentNames,
  locationNames,
  jobLevelTitles,
}: {
  data: EmployeeSummary[];
  isLoading: boolean;
  departmentNames: NameLookup;
  locationNames: NameLookup;
  jobLevelTitles: NameLookup;
}) {
  const columns: ColumnDef<EmployeeSummary>[] = [
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
          {row.original.compaRatio != null ? formatCompaRatio(row.original.compaRatio) : "—"}
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
              percentThroughRange: employee.rangePenetration ?? 0,
              compaRatio: employee.compaRatio ?? 0,
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
    return <TableSkeleton columns={SKELETON_COLUMNS} />;
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
    <div className="border-border overflow-hidden rounded-lg border">
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
                <TableCell key={cell.id}>
                  {index === 0 ? (
                    <Link
                      href={`/employees/${row.original.id}`}
                      className="absolute inset-0 z-10"
                      aria-label={`Open ${row.original.firstName} ${row.original.lastName}`}
                    />
                  ) : null}
                  <span className="relative z-0">
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </span>
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
