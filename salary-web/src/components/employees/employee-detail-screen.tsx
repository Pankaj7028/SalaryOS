"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ErrorState, TableSkeleton } from "@/components/feedback/states";
import { CurrentPayPanel } from "@/components/employees/current-pay-panel";
import { PayHistoryPanel } from "@/components/employees/pay-history-panel";
import { PeersPanel } from "@/components/employees/peers-panel";
import { SetInitialCompensationDialog } from "@/components/employees/set-initial-compensation-dialog";
import { ProposeChangeDialog } from "@/components/changes/propose-change-dialog";
import { ApiError } from "@/lib/api/client";
import { useEmployee } from "@/lib/api/employees-queries";
import { useCountries, useDepartments, useJobLevels, useLocations } from "@/lib/api/reference-queries";
import { useSession } from "@/lib/auth/auth-queries";
import { canManageEmployees } from "@/lib/auth/roles";

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "Active",
  ON_LEAVE: "On leave",
  TERMINATED: "Terminated",
};

/** `/employees/[id]` (ui doc §8.3). Header + Current pay + Pay history + Peers + propose-change dialog (P6.4). */
export function EmployeeDetailScreen({ id }: { id: string }) {
  const employee = useEmployee(id);
  const manager = useEmployee(employee.data?.managerId ?? "", { enabled: !!employee.data?.managerId });
  const departments = useDepartments();
  const locations = useLocations();
  const jobLevels = useJobLevels();
  const countries = useCountries();
  const session = useSession();
  const [proposing, setProposing] = useState(false);
  const [settingInitialComp, setSettingInitialComp] = useState(false);

  if (employee.isLoading) {
    return <TableSkeleton columns={["300px"]} rows={6} rowHeight={32} />;
  }

  if (employee.isError || !employee.data) {
    return (
      <ErrorState
        title="Couldn't load this employee"
        detail={
          employee.error instanceof ApiError && employee.error.problem?.detail
            ? employee.error.problem.detail
            : "Check your connection and try again."
        }
        action={<Button size="sm" variant="outline" onClick={() => employee.refetch()}>Retry</Button>}
      />
    );
  }

  const person = employee.data;
  const departmentName = departments.data?.find((d) => d.id === person.departmentId)?.name ?? "—";
  const employeeLocation = locations.data?.find((l) => l.id === person.locationId);
  const locationName = employeeLocation?.name ?? "—";
  const jobLevelTitle = jobLevels.data?.find((l) => l.id === person.jobLevelId)?.title ?? "—";
  const managerName = manager.data ? `${manager.data.firstName} ${manager.data.lastName}` : null;
  const defaultCurrency = countries.data?.find((c) => c.code === employeeLocation?.countryCode)?.defaultCurrency ?? "USD";
  const canManage = session.data ? canManageEmployees(session.data.role) : false;

  return (
    <div className="space-y-6">
      <header className="flex items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <h1 className="type-title">
              {person.firstName} {person.lastName}
            </h1>
            <span className="figure-sm text-muted-foreground">{person.employeeNumber}</span>
            <Badge variant="outline" className={person.status === "TERMINATED" ? "text-muted-foreground" : undefined}>
              {STATUS_LABEL[person.status] ?? person.status}
            </Badge>
            {person.bandMismatched ? (
              <Badge variant="outline" className="border-attention/40 bg-attention-subtle text-attention">
                Band mismatch
              </Badge>
            ) : null}
          </div>
          <p className="type-caption text-muted-foreground">
            {jobLevelTitle} · {departmentName} · {locationName}
          </p>
          {managerName ? (
            <p className="type-caption text-muted-foreground">Reports to {managerName}</p>
          ) : null}
        </div>
        <Button size="sm" disabled={!person.currentBasePay} onClick={() => setProposing(true)}>
          Propose change
        </Button>
      </header>

      <div className="grid gap-4 lg:grid-cols-2">
        <CurrentPayPanel
          employee={person}
          onSetInitialCompensation={canManage ? () => setSettingInitialComp(true) : undefined}
        />
        <PeersPanel employeeId={person.id} />
        <div className="lg:col-span-2">
          <PayHistoryPanel employeeId={person.id} />
        </div>
      </div>

      {proposing ? (
        <ProposeChangeDialog open={proposing} onOpenChange={setProposing} employee={person} />
      ) : null}
      {settingInitialComp ? (
        <SetInitialCompensationDialog
          open
          onOpenChange={setSettingInitialComp}
          employeeId={person.id}
          defaultCurrency={defaultCurrency}
        />
      ) : null}
    </div>
  );
}
