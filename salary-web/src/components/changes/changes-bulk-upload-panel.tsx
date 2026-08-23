"use client";

import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EmptyState } from "@/components/feedback/states";
import { useBulkUploadChangesCsv } from "@/lib/api/changes-queries";
import { CHANGE_REASON_LABEL } from "@/lib/change-reasons";
import type { ChangeBulkUploadResult } from "@/lib/api/changes-bulk-upload";

const CSV_COLUMNS = "employeeNumber,newAmount,changeReason,note (note is optional)";

function ActionBadge({ action }: { action: "PROPOSED" | "ERROR" }) {
  if (action === "ERROR") {
    return <Badge variant="outline" className="border-critical/30 text-critical">Error</Badge>;
  }
  return <Badge variant="outline" className="border-positive/30 text-positive">Proposed</Badge>;
}

function defaultEffectiveDate() {
  return new Date().toISOString().slice(0, 10);
}

/**
 * The "Merit changes" tab of `/admin/import` (post-P9 QA pass). Backend has existed since P6.3
 * (`ChangeService#bulkUpload`) with zero UI, same story as the Salary bands tab. No dry run by
 * design (FR-5.8) — a DRAFT is cheap to discard, so every upload creates real DRAFT proposals
 * directly; there is nothing to preview first. `effectiveDate` applies to every row in the file.
 */
export function ChangesBulkUploadPanel() {
  const [file, setFile] = useState<File | null>(null);
  const [effectiveDate, setEffectiveDate] = useState(defaultEffectiveDate());
  const [result, setResult] = useState<ChangeBulkUploadResult | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const bulkUpload = useBulkUploadChangesCsv();

  function handleFileChange(selected: File | null) {
    setFile(selected);
    setResult(null);
  }

  async function handleUpload() {
    if (!file || !effectiveDate) return;
    const uploaded = await bulkUpload.mutateAsync({ file, effectiveDate });
    setResult(uploaded);
  }

  function handleReset() {
    setFile(null);
    setResult(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  }

  return (
    <div className="space-y-6">
      <p className="type-caption text-muted-foreground">
        A CSV of merit raises — every valid row becomes a real DRAFT proposal immediately, ready to
        submit from the Drafts tab on <span className="type-body-sm">Changes</span>. No preview
        step: a DRAFT is cheap to discard, so there is nothing to dry-run first.
      </p>

      <section className="border-border bg-card space-y-3 rounded-lg border p-4">
        <div className="flex min-w-0 flex-col gap-1">
          <span className="type-label text-muted-foreground">Columns, in order</span>
          <code className="figure-sm bg-muted/40 block rounded px-2 py-1.5 break-words whitespace-normal">{CSV_COLUMNS}</code>
        </div>
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1">
            <Label htmlFor="bulk-effective-date" className="type-caption text-muted-foreground">Effective date</Label>
            <Input
              id="bulk-effective-date"
              type="date"
              value={effectiveDate}
              onChange={(e) => setEffectiveDate(e.target.value)}
              className="w-40"
            />
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv,text/csv"
            onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
            className="type-body-sm file:type-body-sm file:border-border file:bg-muted/40 file:mr-3 file:rounded-md file:border file:px-2.5 file:py-1"
          />
          <Button size="sm" disabled={!file || !effectiveDate || bulkUpload.isPending} onClick={handleUpload}>
            {bulkUpload.isPending ? "Uploading…" : "Upload"}
          </Button>
          {file || result ? (
            <Button size="sm" variant="ghost" onClick={handleReset}>
              Start over
            </Button>
          ) : null}
        </div>
      </section>

      {result ? (
        <section className="space-y-3">
          <div className="flex flex-wrap items-center gap-4">
            <h2 className="type-section">Result</h2>
            <span className="type-body-sm text-muted-foreground">
              {result.totalRows} rows · {result.proposed} proposed · {result.errors} error
              {result.errors === 1 ? "" : "s"}
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
                    <TableHead className="type-label text-muted-foreground">New amount</TableHead>
                    <TableHead className="type-label text-muted-foreground">Reason</TableHead>
                    <TableHead className="type-label text-muted-foreground">Error</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.rows.map((row) => (
                    <TableRow key={row.rowNumber} className="h-10">
                      <TableCell className="figure-sm">{row.rowNumber}</TableCell>
                      <TableCell><ActionBadge action={row.action} /></TableCell>
                      <TableCell className="figure-sm">{row.employeeNumber ?? "—"}</TableCell>
                      <TableCell className="figure-sm">{row.newAmount ?? "—"}</TableCell>
                      <TableCell className="type-body-sm">
                        {row.changeReason ? (CHANGE_REASON_LABEL[row.changeReason] ?? row.changeReason) : "—"}
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
