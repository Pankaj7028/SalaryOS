"use client";

import { useEffect, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Controller, useForm } from "react-hook-form";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Money } from "@/components/comp/money";
import { Delta } from "@/components/comp/delta";
import { BandBar } from "@/components/comp/band-bar";
import { useChangeImpactPreview, useProposeChange } from "@/lib/api/changes-queries";
import { proposeChangeFormSchema, type ProposeChangeFormValues } from "@/components/changes/propose-change-form-schema";
import { CHANGE_REASON_LABEL, PROPOSABLE_CHANGE_REASONS } from "@/lib/change-reasons";
import { formatCompaRatio } from "@/lib/money";
import type { EmployeeDetail } from "@/lib/api/employees";

const PREVIEW_DEBOUNCE_MS = 400;

export function ProposeChangeDialog({
  open,
  onOpenChange,
  employee,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  employee: EmployeeDetail;
}) {
  const proposeChange = useProposeChange();
  const currency = employee.currentBasePay?.currency ?? "USD";

  const {
    register,
    control,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<ProposeChangeFormValues>({
    resolver: zodResolver(proposeChangeFormSchema),
    defaultValues: {
      effectiveDate: new Date().toISOString().slice(0, 10),
      newBaseAmount: "",
      changeReason: "",
      performanceRating: "",
      note: "",
    },
  });

  const watched = watch(["effectiveDate", "newBaseAmount"]);
  const [debounced, setDebounced] = useState<{ effectiveDate: string; newBaseAmount: string } | null>(null);
  useEffect(() => {
    const [effectiveDate, newBaseAmount] = watched;
    const valid = effectiveDate && newBaseAmount && Number(newBaseAmount) > 0;
    if (!valid) {
      setDebounced(null);
      return;
    }
    const timer = setTimeout(() => setDebounced({ effectiveDate, newBaseAmount }), PREVIEW_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [watched]);

  const preview = useChangeImpactPreview(
    debounced ? { employeeId: employee.id, currency, ...debounced } : null,
  );

  const note = watch("note");
  const noteMissing = !!preview.data?.noteRequired && !note?.trim();

  async function onSubmit(values: ProposeChangeFormValues) {
    if (noteMissing) {
      return;
    }
    await proposeChange.mutateAsync({
      employeeId: employee.id,
      currency,
      ...values,
      note: values.note || undefined,
      performanceRating: values.performanceRating || undefined,
    });
    reset();
    setDebounced(null);
    onOpenChange(false);
  }

  function handleOpenChange(next: boolean) {
    if (!next) {
      reset();
      setDebounced(null);
    }
    onOpenChange(next);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Propose change</DialogTitle>
          <p className="type-caption text-muted-foreground">
            {employee.firstName} {employee.lastName} · {employee.employeeNumber}
          </p>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} noValidate className="mt-2 grid gap-6 sm:grid-cols-2">
          <div className="flex flex-col gap-3">
            <div className="grid grid-cols-2 gap-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor="pc-effectiveDate">Effective date</Label>
                <Input id="pc-effectiveDate" type="date" {...register("effectiveDate")} />
                {errors.effectiveDate ? (
                  <p role="alert" className="type-body-sm text-critical">{errors.effectiveDate.message}</p>
                ) : null}
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="pc-newBaseAmount">New annual base ({currency})</Label>
                <Input id="pc-newBaseAmount" inputMode="decimal" {...register("newBaseAmount")} />
                {errors.newBaseAmount ? (
                  <p role="alert" className="type-body-sm text-critical">{errors.newBaseAmount.message}</p>
                ) : null}
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <Label htmlFor="pc-changeReason">Reason</Label>
              <Controller
                control={control}
                name="changeReason"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="pc-changeReason" size="sm">
                      <SelectValue placeholder="Select a reason" />
                    </SelectTrigger>
                    <SelectContent>
                      {PROPOSABLE_CHANGE_REASONS.map((reason) => (
                        <SelectItem key={reason} value={reason}>
                          {CHANGE_REASON_LABEL[reason]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
              {errors.changeReason ? (
                <p role="alert" className="type-body-sm text-critical">{errors.changeReason.message}</p>
              ) : null}
            </div>

            <div className="flex flex-col gap-1">
              <Label htmlFor="pc-performanceRating">Performance rating (optional)</Label>
              <Input id="pc-performanceRating" {...register("performanceRating")} />
            </div>

            <div className="flex flex-col gap-1">
              <Label htmlFor="pc-note">
                Note{preview.data?.noteRequired ? " (required)" : " (optional)"}
              </Label>
              <Textarea id="pc-note" rows={3} {...register("note")} />
              {preview.data?.noteRequired ? (
                <p className="type-body-sm text-attention">
                  This lands {formatBandStatus(preview.data.proposedBandStatus)} — a note explaining why is required.
                </p>
              ) : null}
            </div>
          </div>

          <div className="bg-muted/30 flex flex-col gap-4 rounded-lg border p-4">
            {!debounced ? (
              <p className="type-caption text-muted-foreground">Enter an effective date and amount to preview impact.</p>
            ) : preview.isLoading ? (
              <p className="type-caption text-muted-foreground">Checking impact…</p>
            ) : preview.isError ? (
              <p className="type-body-sm text-critical">Couldn&apos;t load the impact preview.</p>
            ) : preview.data ? (
              <>
                <div className="flex flex-col gap-1">
                  <span className="type-label text-muted-foreground">Current → proposed</span>
                  <div className="flex items-baseline gap-2">
                    <Money value={preview.data.currentBase} size="figure-sm" showCurrency={false} />
                    <span className="text-muted-foreground">→</span>
                    <Money value={preview.data.proposedBase} size="figure" />
                  </div>
                  <Delta amount={preview.data.deltaAmount} percent={preview.data.deltaPercent * 100} size="figure-sm" />
                </div>

                <div className="flex gap-6">
                  <div className="flex flex-col gap-0.5">
                    <span className="type-label text-muted-foreground">Compa-ratio</span>
                    <span className="figure-sm">
                      {preview.data.currentCompaRatio != null ? formatCompaRatio(preview.data.currentCompaRatio) : "—"}
                      {" → "}
                      {preview.data.proposedCompaRatio != null ? formatCompaRatio(preview.data.proposedCompaRatio) : "—"}
                    </span>
                  </div>
                  <div className="flex flex-col gap-0.5">
                    <span className="type-label text-muted-foreground">Annualised cost</span>
                    <Delta amount={preview.data.deltaAmount} size="figure-sm" />
                  </div>
                </div>

                <div className="flex flex-col gap-2">
                  <span className="type-label text-muted-foreground">Band position</span>
                  <div className="flex flex-col gap-1">
                    <span className="type-caption text-muted-foreground">Current</span>
                    <BandBar
                      salary={preview.data.currentBase}
                      band={preview.data.band}
                      position={{
                        status: preview.data.currentBandStatus,
                        percentThroughRange: preview.data.currentRangePenetration ?? 0,
                        compaRatio: preview.data.currentCompaRatio ?? 0,
                      }}
                    />
                  </div>
                  <div className="flex flex-col gap-1">
                    <span className="type-caption text-muted-foreground">Proposed</span>
                    <BandBar
                      salary={preview.data.proposedBase}
                      band={preview.data.band}
                      position={{
                        status: preview.data.proposedBandStatus,
                        percentThroughRange: preview.data.proposedRangePenetration ?? 0,
                        compaRatio: preview.data.proposedCompaRatio ?? 0,
                      }}
                    />
                  </div>
                </div>

                <div className="flex flex-col gap-0.5">
                  <span className="type-label text-muted-foreground">Peer percentile</span>
                  {preview.data.peerSuppressed ? (
                    <span className="type-caption text-muted-foreground">
                      Cohort of {preview.data.peerCohortSize} is too small to show.
                    </span>
                  ) : (
                    <span className="figure-sm">
                      {preview.data.peerPercentileBefore}th → {preview.data.peerPercentileAfter}th of{" "}
                      {preview.data.peerCohortSize} peers
                    </span>
                  )}
                </div>
              </>
            ) : null}
          </div>

          <DialogFooter className="sm:col-span-2">
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={proposeChange.isPending || noteMissing || !preview.data}>
              {proposeChange.isPending ? "Submitting…" : "Submit for approval"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function formatBandStatus(status: string): string {
  if (status === "BELOW_MIN") return "below the band minimum";
  if (status === "ABOVE_MAX") return "above the band maximum";
  return "outside the band";
}
