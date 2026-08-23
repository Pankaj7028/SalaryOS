"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useBulkProposeChanges } from "@/lib/api/changes-queries";
import type { BulkProposeResult } from "@/lib/api/changes";

/** Mirrors `BulkProposeRequest`'s bean validation, so a valid form is a valid request. */
const bulkProposeSchema = z.object({
  percentIncrease: z
    .string()
    .trim()
    .min(1, "Enter a percentage.")
    .refine((v) => /^-?\d+(\.\d{1,2})?$/.test(v), "Use a number like 3 or 3.5.")
    .refine((v) => Number(v) >= -50 && Number(v) <= 100, "Between -50% and 100%."),
  effectiveDate: z.string().min(1, "Pick an effective date."),
  changeReason: z.string().min(1, "Pick a reason."),
  note: z.string().max(1000).optional(),
});

type BulkProposeValues = z.infer<typeof bulkProposeSchema>;

const CHANGE_REASONS = [
  { value: "MERIT", label: "Merit increase" },
  { value: "PROMOTION", label: "Promotion" },
  { value: "MARKET_ADJUSTMENT", label: "Market adjustment" },
  { value: "CORRECTION", label: "Correction" },
];

/**
 * P10.5 — propose one uplift for everyone selected on `/employees`.
 *
 * <p><b>A percentage, never an amount.</b> The people in a selection are on different salaries, so
 * a shared figure is meaningless and per-person figures would have to be computed somewhere. That
 * somewhere is the server (CLAUDE.md §6.1): this form sends one percentage and the service works
 * out each new base in `BigDecimal` from what the ledger already says that person is paid, in the
 * currency they are actually paid in.
 *
 * <p>Nothing here approves anything. Every row lands as a DRAFT, so a 40-person merit round is
 * still 40 reviewable proposals, and the one that is wrong can be discarded without touching the
 * other 39.
 */
export function BulkProposeDialog({
  open,
  onOpenChange,
  employeeIds,
  onProposed,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  employeeIds: string[];
  /** Called once the batch lands, so the screen can clear its selection. */
  onProposed: () => void;
}) {
  const bulkPropose = useBulkProposeChanges();
  const [result, setResult] = useState<BulkProposeResult | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<BulkProposeValues>({
    resolver: zodResolver(bulkProposeSchema),
    defaultValues: { percentIncrease: "", effectiveDate: "", changeReason: "MERIT", note: "" },
  });

  function close(next: boolean) {
    if (!next) {
      reset();
      setResult(null);
    }
    onOpenChange(next);
  }

  async function onSubmit(values: BulkProposeValues) {
    const outcome = await bulkPropose.mutateAsync({
      employeeIds,
      effectiveDate: values.effectiveDate,
      percentIncrease: values.percentIncrease,
      changeReason: values.changeReason,
      note: values.note?.trim() || undefined,
    });
    setResult(outcome);
    onProposed();
  }

  const skipped = result?.rows.filter((row) => row.action === "ERROR") ?? [];

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-lg">
        {result ? (
          <>
            <DialogHeader>
              <DialogTitle>
                {result.proposed} of {result.totalRows} proposed
              </DialogTitle>
              <p className="type-caption text-muted-foreground">
                Every one is a draft. Submit them for approval from the Changes screen.
              </p>
            </DialogHeader>

            {skipped.length > 0 ? (
              <div className="border-border mt-4 max-h-64 overflow-y-auto rounded-md border">
                <p className="type-label text-muted-foreground border-border bg-muted/30 border-b px-3 py-2">
                  Skipped ({skipped.length})
                </p>
                <ul className="divide-border divide-y">
                  {skipped.map((row) => (
                    <li key={row.rowNumber} className="px-3 py-2">
                      <span className="figure-sm">{row.employeeNumber ?? "—"}</span>
                      <p className="type-caption text-muted-foreground">{row.error}</p>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}

            <DialogFooter>
              <Button size="sm" onClick={() => close(false)}>
                Done
              </Button>
            </DialogFooter>
          </>
        ) : (
          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <DialogHeader>
              <DialogTitle>
                Propose a change for {employeeIds.length}{" "}
                {employeeIds.length === 1 ? "employee" : "employees"}
              </DialogTitle>
              <p className="type-caption text-muted-foreground">
                One percentage, applied to each person&rsquo;s own current base pay in their own
                currency. Each lands as a separate draft you can review one by one.
              </p>
            </DialogHeader>

            <div className="mt-4 flex flex-col gap-3">
              <div className="flex flex-col gap-1">
                <Label htmlFor="bulkPercent">Increase</Label>
                <div className="flex items-center gap-2">
                  <Input
                    id="bulkPercent"
                    inputMode="decimal"
                    placeholder="3.5"
                    className="figure-sm"
                    {...register("percentIncrease")}
                  />
                  <span className="type-body-sm text-muted-foreground">%</span>
                </div>
                {errors.percentIncrease ? (
                  <p role="alert" className="type-body-sm text-critical">
                    {errors.percentIncrease.message}
                  </p>
                ) : (
                  <p className="type-caption text-muted-foreground">
                    A negative percentage proposes a cut.
                  </p>
                )}
              </div>

              <div className="flex flex-col gap-1">
                <Label htmlFor="bulkEffectiveDate">Effective date</Label>
                <Input id="bulkEffectiveDate" type="date" className="figure-sm" {...register("effectiveDate")} />
                {errors.effectiveDate ? (
                  <p role="alert" className="type-body-sm text-critical">
                    {errors.effectiveDate.message}
                  </p>
                ) : null}
              </div>

              <div className="flex flex-col gap-1">
                <Label htmlFor="bulkReason">Reason</Label>
                <Controller
                  control={control}
                  name="changeReason"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="bulkReason" size="sm">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {CHANGE_REASONS.map((reason) => (
                          <SelectItem key={reason.value} value={reason.value}>
                            {reason.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>

              <div className="flex flex-col gap-1">
                <Label htmlFor="bulkNote">Note (optional)</Label>
                <Textarea id="bulkNote" rows={2} {...register("note")} />
                <p className="type-caption text-muted-foreground">
                  The same note is attached to every proposal in this batch.
                </p>
              </div>
            </div>

            <DialogFooter>
              <Button type="button" size="sm" variant="outline" onClick={() => close(false)}>
                Cancel
              </Button>
              <Button type="submit" size="sm" disabled={bulkPropose.isPending}>
                {bulkPropose.isPending ? "Proposing…" : `Propose ${employeeIds.length}`}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
