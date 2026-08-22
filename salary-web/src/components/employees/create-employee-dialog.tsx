"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Controller, useForm } from "react-hook-form";
import { useRouter } from "next/navigation";
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useCreateEmployee } from "@/lib/api/employees-queries";
import { useDepartments, useJobFamilies, useJobLevels, useLocations } from "@/lib/api/reference-queries";
import { employeeFormSchema, type EmployeeFormValues } from "@/components/employees/employee-form-schema";

const EMPLOYMENT_TYPE_LABEL: Record<string, string> = {
  FULL_TIME: "Full-time",
  PART_TIME: "Part-time",
  CONTRACT: "Contract",
};

/**
 * `/employees`'s "New employee" action. Identity and org placement only — pay is never part of
 * this call (CLAUDE.md §6.3: compensation is its own insert-only ledger, never a field on the
 * employee record). On success this navigates straight to the new person's detail page, where
 * `CurrentPayPanel`'s empty state is the one place "set a starting salary" lives — the same
 * prompt a CSV-imported employee with no pay yet already gets, so there's exactly one mechanism
 * for "give this person their first paycheck," not two.
 */
export function CreateEmployeeDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const router = useRouter();
  const createEmployee = useCreateEmployee();
  const departments = useDepartments();
  const locations = useLocations();
  const jobFamilies = useJobFamilies();
  const jobLevels = useJobLevels();

  const {
    register,
    control,
    handleSubmit,
    watch,
    resetField,
    reset,
    formState: { errors },
  } = useForm<EmployeeFormValues>({
    resolver: zodResolver(employeeFormSchema),
    defaultValues: {
      employeeNumber: "",
      firstName: "",
      lastName: "",
      workEmail: "",
      departmentId: "",
      locationId: "",
      jobFamilyId: "",
      jobLevelId: "",
      hireDate: new Date().toISOString().slice(0, 10),
      employmentType: "FULL_TIME",
      fte: "1.00",
    },
  });

  const selectedJobFamilyId = watch("jobFamilyId");
  const levelsInFamily = (jobLevels.data ?? []).filter((l) => l.jobFamilyId === selectedJobFamilyId);

  async function onSubmit(values: EmployeeFormValues) {
    const created = await createEmployee.mutateAsync(values);
    reset();
    onOpenChange(false);
    router.push(`/employees/${created.id}`);
  }

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) reset(); onOpenChange(next); }}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>New employee</DialogTitle>
          </DialogHeader>
          <div className="mt-4 flex max-h-[60vh] flex-col gap-3 overflow-y-auto pr-1">
            <div className="grid grid-cols-2 gap-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor="ce-employeeNumber">Employee number</Label>
                <Input id="ce-employeeNumber" {...register("employeeNumber")} />
                {errors.employeeNumber ? <p role="alert" className="type-body-sm text-critical">{errors.employeeNumber.message}</p> : null}
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="ce-workEmail">Work email</Label>
                <Input id="ce-workEmail" type="email" {...register("workEmail")} />
                {errors.workEmail ? <p role="alert" className="type-body-sm text-critical">{errors.workEmail.message}</p> : null}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor="ce-firstName">First name</Label>
                <Input id="ce-firstName" {...register("firstName")} />
                {errors.firstName ? <p role="alert" className="type-body-sm text-critical">{errors.firstName.message}</p> : null}
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="ce-lastName">Last name</Label>
                <Input id="ce-lastName" {...register("lastName")} />
                {errors.lastName ? <p role="alert" className="type-body-sm text-critical">{errors.lastName.message}</p> : null}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor="ce-departmentId">Department</Label>
                <Controller
                  control={control}
                  name="departmentId"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="ce-departmentId" size="sm"><SelectValue placeholder="Select" /></SelectTrigger>
                      <SelectContent>
                        {(departments.data ?? []).map((d) => (
                          <SelectItem key={d.id} value={d.id}>{d.name}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
                {errors.departmentId ? <p role="alert" className="type-body-sm text-critical">{errors.departmentId.message}</p> : null}
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="ce-locationId">Location</Label>
                <Controller
                  control={control}
                  name="locationId"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="ce-locationId" size="sm"><SelectValue placeholder="Select" /></SelectTrigger>
                      <SelectContent>
                        {(locations.data ?? []).map((l) => (
                          <SelectItem key={l.id} value={l.id}>{l.name}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
                {errors.locationId ? <p role="alert" className="type-body-sm text-critical">{errors.locationId.message}</p> : null}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor="ce-jobFamilyId">Job family</Label>
                <Controller
                  control={control}
                  name="jobFamilyId"
                  render={({ field }) => (
                    <Select
                      value={field.value}
                      onValueChange={(value) => {
                        field.onChange(value);
                        resetField("jobLevelId", { defaultValue: "" });
                      }}
                    >
                      <SelectTrigger id="ce-jobFamilyId" size="sm"><SelectValue placeholder="Select" /></SelectTrigger>
                      <SelectContent>
                        {(jobFamilies.data ?? []).map((f) => (
                          <SelectItem key={f.id} value={f.id}>{f.name}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
                {errors.jobFamilyId ? <p role="alert" className="type-body-sm text-critical">{errors.jobFamilyId.message}</p> : null}
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="ce-jobLevelId">Job level</Label>
                <Controller
                  control={control}
                  name="jobLevelId"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange} disabled={!selectedJobFamilyId}>
                      <SelectTrigger id="ce-jobLevelId" size="sm">
                        <SelectValue placeholder={selectedJobFamilyId ? "Select" : "Pick a family first"} />
                      </SelectTrigger>
                      <SelectContent>
                        {levelsInFamily.map((l) => (
                          <SelectItem key={l.id} value={l.id}>{l.title}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
                {errors.jobLevelId ? <p role="alert" className="type-body-sm text-critical">{errors.jobLevelId.message}</p> : null}
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor="ce-hireDate">Hire date</Label>
                <Input id="ce-hireDate" type="date" {...register("hireDate")} />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="ce-employmentType">Type</Label>
                <Controller
                  control={control}
                  name="employmentType"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="ce-employmentType" size="sm"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        {Object.entries(EMPLOYMENT_TYPE_LABEL).map(([value, label]) => (
                          <SelectItem key={value} value={value}>{label}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="ce-fte">FTE</Label>
                <Input id="ce-fte" inputMode="decimal" className="figure" {...register("fte")} />
                {errors.fte ? <p role="alert" className="type-body-sm text-critical">{errors.fte.message}</p> : null}
              </div>
            </div>
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" size="sm" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={createEmployee.isPending}>
              {createEmployee.isPending ? "Creating…" : "Create employee"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
