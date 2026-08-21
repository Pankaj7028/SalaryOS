"use client";

import { useEffect, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Money } from "@/components/comp/money";
import { useBandVersionImpact, useUpdateBand } from "@/lib/api/bands-queries";
import { bandFormSchema, type BandFormValues } from "@/components/bands/band-form-schema";
import type { Band } from "@/lib/api/bands";

const PREVIEW_DEBOUNCE_MS = 400;

export function BandDetailDialog({
  open,
  onOpenChange,
  versions,
  levelTitle,
  countryName,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** All versions for this (level, country), newest first. */
  versions: Band[];
  levelTitle: string;
  countryName: string;
}) {
  const current = versions[0];
  const [versioning, setVersioning] = useState(false);
  const updateBand = useUpdateBand(current.id);

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<BandFormValues>({
    resolver: zodResolver(bandFormSchema),
    defaultValues: {
      currency: current.min.currency,
      minAmount: current.min.amount,
      midAmount: current.mid.amount,
      maxAmount: current.max.amount,
      effectiveFrom: new Date().toISOString().slice(0, 10),
      note: "",
    },
  });

  const watched = watch(["minAmount", "midAmount", "maxAmount"]);
  const [debounced, setDebounced] = useState<{ minAmount: string; midAmount: string; maxAmount: string } | null>(null);
  useEffect(() => {
    const [minAmount, midAmount, maxAmount] = watched;
    const allNumeric = [minAmount, midAmount, maxAmount].every((v) => v && !Number.isNaN(Number(v)));
    if (!versioning || !allNumeric) {
      setDebounced(null);
      return;
    }
    const timer = setTimeout(() => setDebounced({ minAmount, midAmount, maxAmount }), PREVIEW_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [watched, versioning]);

  const impact = useBandVersionImpact(current.id, debounced);

  async function onSubmit(values: BandFormValues) {
    await updateBand.mutateAsync(values);
    reset();
    setVersioning(false);
    onOpenChange(false);
  }

  function handleOpenChange(next: boolean) {
    if (!next) {
      setVersioning(false);
      reset();
    }
    onOpenChange(next);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{levelTitle}</DialogTitle>
          <p className="type-caption text-muted-foreground">{countryName}</p>
        </DialogHeader>

        {!versioning ? (
          <div className="mt-4 flex flex-col gap-4">
            <ol className="border-border flex flex-col gap-4 border-l pl-4">
              {versions.map((version) => (
                <li key={version.id} className="relative">
                  <span aria-hidden className="bg-primary absolute top-1 -left-[19px] size-2 rounded-full" />
                  <p className="figure-sm text-muted-foreground">
                    {version.effectiveFrom}
                    {version.effectiveTo ? ` – ${version.effectiveTo}` : " – present"}
                  </p>
                  <p className="figure">
                    <Money value={version.min} size="figure-sm" /> – <Money value={version.mid} size="figure-sm" /> –{" "}
                    <Money value={version.max} size="figure-sm" />
                  </p>
                  {version.effectiveTo === null ? (
                    <p className="type-caption text-muted-foreground">
                      {version.headcount} {version.headcount === 1 ? "employee" : "employees"}
                    </p>
                  ) : null}
                  {version.note ? <p className="type-caption text-muted-foreground">{version.note}</p> : null}
                </li>
              ))}
            </ol>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
                Close
              </Button>
              <Button type="button" onClick={() => setVersioning(true)}>
                New version
              </Button>
            </DialogFooter>
          </div>
        ) : (
          <form onSubmit={handleSubmit(onSubmit)} noValidate className="mt-4 flex flex-col gap-3">
            <div className="grid grid-cols-3 gap-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor="v-minAmount">Min</Label>
                <Input id="v-minAmount" inputMode="decimal" {...register("minAmount")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="v-midAmount">Mid</Label>
                <Input id="v-midAmount" inputMode="decimal" {...register("midAmount")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="v-maxAmount">Max</Label>
                <Input id="v-maxAmount" inputMode="decimal" {...register("maxAmount")} />
              </div>
            </div>
            {errors.midAmount ? (
              <p role="alert" className="type-body-sm text-critical">{errors.midAmount.message}</p>
            ) : errors.maxAmount ? (
              <p role="alert" className="type-body-sm text-critical">{errors.maxAmount.message}</p>
            ) : null}
            <div className="grid grid-cols-2 gap-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor="v-currency">Currency</Label>
                <Input id="v-currency" maxLength={3} className="uppercase" {...register("currency")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="v-effectiveFrom">Effective from</Label>
                <Input id="v-effectiveFrom" type="date" {...register("effectiveFrom")} />
              </div>
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="v-note">Note</Label>
              <Textarea id="v-note" rows={2} {...register("note")} />
            </div>

            {/* ui doc §8.6: "the most useful number on the screen." */}
            <div className="bg-muted/40 rounded-lg border px-3 py-2">
              {impact.isLoading ? (
                <p className="type-caption text-muted-foreground">Checking impact…</p>
              ) : impact.data ? (
                <p className="type-body-sm">
                  <span className="figure">{impact.data.changingStatus}</span> of{" "}
                  <span className="figure">{impact.data.cohortSize}</span> employees currently in this band would
                  change status.
                </p>
              ) : (
                <p className="type-caption text-muted-foreground">Enter min/mid/max to preview impact.</p>
              )}
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setVersioning(false)}>
                Back
              </Button>
              <Button type="submit" disabled={updateBand.isPending}>
                {updateBand.isPending ? "Saving…" : "Save new version"}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
