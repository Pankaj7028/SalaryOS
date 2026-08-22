"use client";

import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EmptyState } from "@/components/feedback/states";
import { useImportEmployeesCsv } from "@/lib/api/employee-import-queries";
import type { EmployeeImportResult } from "@/lib/api/employee-import";

const CSV_COLUMNS =
  "employeeNumber,firstName,lastName,workEmail,departmentId,locationId,jobFamilyId,jobLevelId,managerId,hireDate,employmentType,fte";

function ActionBadge({ action }: { action: "CREATE" | "UPDATE" | "ERROR" }) {
  if (action === "ERROR") {
    return <Badge variant="outline" className="border-critical/30 text-critical">Error</Badge>;
  }
  if (action === "UPDATE") {
    return <Badge variant="outline" className="border-primary/30 text-primary">Update</Badge>;
  }
  return <Badge variant="outline" className="border-positive/30 text-positive">Create</Badge>;
}

/**
 * `/admin/import` (ui doc §8.9, P8.4). "Import / bulk upload" is HR_ADMIN only (CLAUDE.md §7) —
 * the backend enforces that; this screen doesn't duplicate the check, it just has one visitor.
 * A file is always dry-run first: the diff renders, nothing has been written, and only then does
 * "Apply import" send the identical file with `dryRun=false` — matching the CSV bands import's own
 * contract (`BandImportResult`, P5.3) and this step's own Verify clause.
 */
export function EmployeeImportScreen() {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<EmployeeImportResult | null>(null);
  const [applied, setApplied] = useState<EmployeeImportResult | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const importCsv = useImportEmployeesCsv();

  function handleFileChange(selected: File | null) {
    setFile(selected);
    setPreview(null);
    setApplied(null);
  }

  async function handlePreview() {
    if (!file) return;
    const result = await importCsv.mutateAsync({ file, dryRun: true });
    setPreview(result);
    setApplied(null);
  }

  async function handleApply() {
    if (!file) return;
    const result = await importCsv.mutateAsync({ file, dryRun: false });
    setApplied(result);
  }

  function handleReset() {
    setFile(null);
    setPreview(null);
    setApplied(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  }

  const result = applied ?? preview;

  return (
    <div className="space-y-6">
      <header>
        <h1 className="type-title">Employee import</h1>
        <p className="type-caption text-muted-foreground mt-1">
          A CSV keyed by employee number — a new number creates, an existing one updates that
          employee&rsquo;s profile (never their pay). Every file previews as a dry run before
          anything is written.
        </p>
      </header>

      <section className="border-border bg-card space-y-3 rounded-lg border p-4">
        <div className="flex min-w-0 flex-col gap-1">
          <span className="type-label text-muted-foreground">Columns, in order</span>
          <code className="figure-sm bg-muted/40 block rounded px-2 py-1.5 break-words whitespace-normal">{CSV_COLUMNS}</code>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv,text/csv"
            onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
            className="type-body-sm file:type-body-sm file:border-border file:bg-muted/40 file:mr-3 file:rounded-md file:border file:px-2.5 file:py-1"
          />
          <Button size="sm" disabled={!file || importCsv.isPending} onClick={handlePreview}>
            {importCsv.isPending && !applied ? "Previewing…" : "Preview (dry run)"}
          </Button>
          {preview && !applied ? (
            <Button size="sm" variant="outline" disabled={importCsv.isPending || preview.errors === preview.totalRows} onClick={handleApply}>
              {importCsv.isPending ? "Applying…" : `Apply import (${preview.rowsApplied || preview.created + preview.updated} rows)`}
            </Button>
          ) : null}
          {file ? (
            <Button size="sm" variant="ghost" onClick={handleReset}>
              Start over
            </Button>
          ) : null}
        </div>
      </section>

      {result ? (
        <section className="space-y-3">
          <div className="flex flex-wrap items-center gap-4">
            <h2 className="type-section">{result.dryRun ? "Preview" : "Applied"}</h2>
            <span className="type-body-sm text-muted-foreground">
              {result.totalRows} rows · {result.created} to create · {result.updated} to update ·{" "}
              {result.errors} error{result.errors === 1 ? "" : "s"}
              {result.dryRun ? " · nothing written yet" : ` · ${result.rowsApplied} written`}
            </span>
          </div>

          {result.rows.length === 0 ? (
            <EmptyState title="No rows" detail="The file had no data rows after the header." />
          ) : (
            <div className="border-border overflow-x-auto rounded-lg border">
              <Table>
                <TableHeader className="bg-muted/40">
                  <TableRow className="h-10">
                    <TableHead className="type-label text-muted-foreground">Row</TableHead>
                    <TableHead className="type-label text-muted-foreground">Action</TableHead>
                    <TableHead className="type-label text-muted-foreground">Employee #</TableHead>
                    <TableHead className="type-label text-muted-foreground">Name</TableHead>
                    <TableHead className="type-label text-muted-foreground">Error</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.rows.map((row) => (
                    <TableRow key={row.rowNumber} className="h-10">
                      <TableCell className="figure-sm">{row.rowNumber}</TableCell>
                      <TableCell><ActionBadge action={row.action} /></TableCell>
                      <TableCell className="figure-sm">{row.employeeNumber ?? "—"}</TableCell>
                      <TableCell className="type-body-sm">
                        {row.firstName || row.lastName ? `${row.firstName ?? ""} ${row.lastName ?? ""}`.trim() : "—"}
                      </TableCell>
                      <TableCell className="type-body-sm text-critical">{row.error ?? ""}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </section>
      ) : null}
    </div>
  );
}
