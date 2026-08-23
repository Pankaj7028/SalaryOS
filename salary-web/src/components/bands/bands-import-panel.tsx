"use client";

import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EmptyState } from "@/components/feedback/states";
import { useImportBandsCsv } from "@/lib/api/bands-queries";
import type { BandImportResult } from "@/lib/api/bands-import";

const CSV_COLUMNS = "jobLevelId,countryCode,currency,minAmount,midAmount,maxAmount,effectiveFrom,note";

function ActionBadge({ action }: { action: "CREATE" | "VERSION" | "ERROR" }) {
  if (action === "ERROR") {
    return <Badge variant="outline" className="border-critical/30 text-critical">Error</Badge>;
  }
  if (action === "VERSION") {
    return <Badge variant="outline" className="border-primary/30 text-primary">Version</Badge>;
  }
  return <Badge variant="outline" className="border-positive/30 text-positive">Create</Badge>;
}

/**
 * The "Salary bands" tab of `/admin/import` (post-P9 QA pass). Backend has existed since P5.3
 * (`BandService#importCsv`, dry-run diff) — P5.3's own done-note explicitly deferred the UI
 * ("a UI renders/downloads it whenever a bulk-upload screen exists"); nothing ever built it. Same
 * dry-run-first contract as the Employees tab: preview always runs before "Apply import" can.
 */
export function BandsImportPanel() {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<BandImportResult | null>(null);
  const [applied, setApplied] = useState<BandImportResult | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const importCsv = useImportBandsCsv();

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
      <p className="type-caption text-muted-foreground">
        A CSV of salary-band versions, one per (job level × country). A row always creates a new
        version — bands are never edited in place (CLAUDE.md §6.3&rsquo;s insert-only rule applies
        here too). Every file previews as a dry run before anything is written.
      </p>

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
              {importCsv.isPending ? "Applying…" : `Apply import (${preview.rowsApplied || preview.created + preview.versioned} rows)`}
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
              {result.totalRows} rows · {result.created} to create · {result.versioned} to version ·{" "}
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
                    <TableHead className="type-label text-muted-foreground">Level</TableHead>
                    <TableHead className="type-label text-muted-foreground">Country</TableHead>
                    <TableHead className="type-label text-muted-foreground">Min / Mid / Max</TableHead>
                    <TableHead className="type-label text-muted-foreground">Error</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.rows.map((row) => (
                    <TableRow key={row.rowNumber} className="h-10">
                      <TableCell className="figure-sm">{row.rowNumber}</TableCell>
                      <TableCell><ActionBadge action={row.action} /></TableCell>
                      <TableCell className="figure-sm">{row.jobLevelId ?? "—"}</TableCell>
                      <TableCell className="type-body-sm">{row.countryCode ?? "—"}</TableCell>
                      <TableCell className="figure-sm">
                        {row.minAmount && row.midAmount && row.maxAmount
                          ? `${row.minAmount} / ${row.midAmount} / ${row.maxAmount} ${row.currency ?? ""}`
                          : "—"}
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
