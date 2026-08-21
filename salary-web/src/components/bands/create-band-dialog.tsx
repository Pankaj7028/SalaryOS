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
import { Textarea } from "@/components/ui/textarea";
import { useCreateBand } from "@/lib/api/bands-queries";
import { bandFormSchema, type BandFormValues } from "@/components/bands/band-form-schema";

export function CreateBandDialog({
  open,
  onOpenChange,
  jobLevelId,
  countryCode,
  levelTitle,
  countryName,
  defaultCurrency,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  jobLevelId: string;
  countryCode: string;
  levelTitle: string;
  countryName: string;
  defaultCurrency: string;
}) {
  const createBand = useCreateBand();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<BandFormValues>({
    resolver: zodResolver(bandFormSchema),
    defaultValues: {
      currency: defaultCurrency,
      minAmount: "",
      midAmount: "",
      maxAmount: "",
      effectiveFrom: new Date().toISOString().slice(0, 10),
      note: "",
    },
  });

  async function onSubmit(values: BandFormValues) {
    await createBand.mutateAsync({ jobLevelId, countryCode, ...values });
    reset();
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>New band</DialogTitle>
            <p className="type-caption text-muted-foreground">
              {levelTitle} · {countryName}
            </p>
          </DialogHeader>
          <div className="mt-4 flex flex-col gap-3">
            <div className="grid grid-cols-3 gap-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor="minAmount">Min</Label>
                <Input id="minAmount" inputMode="decimal" {...register("minAmount")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="midAmount">Mid</Label>
                <Input id="midAmount" inputMode="decimal" {...register("midAmount")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="maxAmount">Max</Label>
                <Input id="maxAmount" inputMode="decimal" {...register("maxAmount")} />
              </div>
            </div>
            {errors.midAmount ? (
              <p role="alert" className="type-body-sm text-critical">{errors.midAmount.message}</p>
            ) : errors.maxAmount ? (
              <p role="alert" className="type-body-sm text-critical">{errors.maxAmount.message}</p>
            ) : null}
            <div className="grid grid-cols-2 gap-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor="currency">Currency</Label>
                <Input id="currency" maxLength={3} className="uppercase" {...register("currency")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="effectiveFrom">Effective from</Label>
                <Input id="effectiveFrom" type="date" {...register("effectiveFrom")} />
              </div>
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="note">Note (optional)</Label>
              <Textarea id="note" rows={2} {...register("note")} />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={createBand.isPending}>
              {createBand.isPending ? "Creating…" : "Create band"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
