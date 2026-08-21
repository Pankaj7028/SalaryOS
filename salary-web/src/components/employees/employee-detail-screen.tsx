"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ErrorState, TableSkeleton } from "@/components/feedback/states";
import { CurrentPayPanel } from "@/components/employees/current-pay-panel";
import { PeersPanel } from "@/components/employees/peers-panel";
import { ApiError } from "@/lib/api/client";
import { useEmployee } from "@/lib/api/employees-queries";
import { useDepartments, useJobLevels, useLocations } from "@/lib/api/reference-queries";

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "Active",
  ON_LEAVE: "On leave",
  TERMINATED: "Terminated",
};

/**
 * `/employees/[id]` (ui doc §8.3). Header + Current pay + Peers. **Pay
 * history is deliberately not built here** — it is its own step, P5.4/P5.5,
 * once the ledger endpoint (`GET /{id}/compensation`) exists; `BuildPlan.md`
 * scopes P4.4 to identity, current pay, band bar, and peers only. "Propose
 * change" is disabled for the same reason as the Overview page's button
 * (P3.3): the change-lifecycle endpoints are P6, not built yet.
 */
export function EmployeeDetailScreen({ id }: { id: string }) {
  const employee = useEmployee(id);
  const manager = useEmployee(employee.data?.managerId ?? "", { enabled: !!employee.data?.managerId });
  const departments = useDepartments();
  const locations = useLocations();
  const jobLevels = useJobLevels();

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
  const locationName = locations.data?.find((l) => l.id === person.locationId)?.name ?? "—";
  const jobLevelTitle = jobLevels.data?.find((l) => l.id === person.jobLevelId)?.title ?? "—";
  const managerName = manager.data ? `${manager.data.firstName} ${manager.data.lastName}` : null;

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
        <Button size="sm" disabled>
          Propose change
        </Button>
      </header>

      <div className="grid gap-4 lg:grid-cols-2">
        <CurrentPayPanel employee={person} />
        <PeersPanel employeeId={person.id} />
      </div>
    </div>
  );
}
