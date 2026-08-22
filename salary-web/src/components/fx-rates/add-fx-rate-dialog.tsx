"use client";

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
import { useAddFxRate } from "@/lib/api/fx-rates-queries";
import { fxRateFormSchema, type FxRateFormValues } from "@/components/fx-rates/fx-rate-form-schema";

/** ui doc §8.9 / P8.3 Verify: a missing rate month is addable. `prefill` carries the (currency,
 * month) the admin clicked from the missing-months list; the dialog also opens blank from the
 * screen's own "Add rate" button, so both paths share one form. */
export function AddFxRateDialog({
  open,
  onOpenChange,
  prefill,
  baseCurrencyOptions,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  prefill?: { baseCurrency: string; quoteCurrency: string; rateMonth: string };
  baseCurrencyOptions: string[];
}) {
  const addFxRate = useAddFxRate();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FxRateFormValues>({
    resolver: zodResolver(fxRateFormSchema),
    values: {
      baseCurrency: prefill?.baseCurrency ?? "",
      quoteCurrency: prefill?.quoteCurrency ?? "USD",
      rateMonth: prefill?.rateMonth ?? new Date().toISOString().slice(0, 7) + "-01",
      rate: "",
    },
  });

  async function onSubmit(values: FxRateFormValues) {
    await addFxRate.mutateAsync(values);
    reset();
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>Add FX rate</DialogTitle>
            <p className="type-caption text-muted-foreground">
              One pinned rate per currency and month — normalisation never recomputes it later.
            </p>
          </DialogHeader>
          <div className="mt-4 flex flex-col gap-3">
            <div className="grid grid-cols-2 gap-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor="baseCurrency">From currency</Label>
                <Input
                  id="baseCurrency"
                  maxLength={3}
                  className="uppercase"
                  list="fx-currency-options"
                  {...register("baseCurrency")}
                />
                <datalist id="fx-currency-options">
                  {baseCurrencyOptions.map((code) => (
                    <option key={code} value={code} />
                  ))}
                </datalist>
                {errors.baseCurrency ? (
                  <p role="alert" className="type-body-sm text-critical">{errors.baseCurrency.message}</p>
                ) : null}
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="quoteCurrency">To currency</Label>
                <Input id="quoteCurrency" maxLength={3} className="uppercase" {...register("quoteCurrency")} />
                {errors.quoteCurrency ? (
                  <p role="alert" className="type-body-sm text-critical">{errors.quoteCurrency.message}</p>
                ) : null}
              </div>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor="rateMonth">Month</Label>
                <Input id="rateMonth" type="date" {...register("rateMonth")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="rate">Rate</Label>
                <Input id="rate" inputMode="decimal" className="figure" {...register("rate")} />
                {errors.rate ? (
                  <p role="alert" className="type-body-sm text-critical">{errors.rate.message}</p>
                ) : null}
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button type="button" size="sm" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={addFxRate.isPending}>
              {addFxRate.isPending ? "Adding…" : "Add rate"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
