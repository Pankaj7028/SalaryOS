"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
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
import { useSetInitialCompensation } from "@/lib/api/employees-queries";

const schema = z.object({
  amount: z.string().min(1, "Required.").refine((v) => Number(v) > 0, "Must be greater than zero."),
  currency: z.string().length(3, "Use a 3-letter currency code, e.g. USD."),
});
type FormValues = z.infer<typeof schema>;

/** FR-2.5's missing half: a new hire (or a CSV-imported employee) has no pay until this runs
 * once — `EmployeeService.setInitialCompensation` refuses a second call, so from here on every
 * change goes through the normal Propose change → approve → apply lifecycle instead. */
export function SetInitialCompensationDialog({
  open,
  onOpenChange,
  employeeId,
  defaultCurrency,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  employeeId: string;
  defaultCurrency: string;
}) {
  const setInitialCompensation = useSetInitialCompensation();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    values: { amount: "", currency: defaultCurrency },
  });

  async function onSubmit(values: FormValues) {
    await setInitialCompensation.mutateAsync({ id: employeeId, input: values });
    reset();
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) reset(); onOpenChange(next); }}>
      <DialogContent className="sm:max-w-sm">
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>Set starting salary</DialogTitle>
            <p className="type-caption text-muted-foreground">
              This employee&rsquo;s first-ever pay period, effective their hire date. It can only be
              set once — every change after this goes through Propose change.
            </p>
          </DialogHeader>
          <div className="mt-4 grid grid-cols-2 gap-2">
            <div className="flex flex-col gap-1">
              <Label htmlFor="sic-amount">Annual base amount</Label>
              <Input id="sic-amount" inputMode="decimal" className="figure" {...register("amount")} />
              {errors.amount ? <p role="alert" className="type-body-sm text-critical">{errors.amount.message}</p> : null}
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="sic-currency">Currency</Label>
              <Input id="sic-currency" maxLength={3} className="uppercase" {...register("currency")} />
              {errors.currency ? <p role="alert" className="type-body-sm text-critical">{errors.currency.message}</p> : null}
            </div>
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" size="sm" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={setInitialCompensation.isPending}>
              {setInitialCompensation.isPending ? "Saving…" : "Set salary"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
