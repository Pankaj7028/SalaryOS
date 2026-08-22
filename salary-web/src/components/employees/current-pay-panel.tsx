import { Info } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { Button } from "@/components/ui/button";
import { Money } from "@/components/comp/money";
import { BandBar } from "@/components/comp/band-bar";
import { EmptyState } from "@/components/feedback/states";
import { formatCompaRatio, formatPercent } from "@/lib/money";
import type { EmployeeDetail } from "@/lib/api/employees";

const COMPONENT_LABEL: Record<string, string> = {
  BONUS_TARGET: "Bonus target",
  HOUSING: "Housing",
  TRANSPORT: "Transport",
  OTHER_ALLOWANCE: "Other allowance",
};

function Figure({
  label,
  value,
  formula,
}: {
  label: string;
  value: string;
  formula: string;
}) {
  return (
    <div className="flex flex-col gap-0.5">
      <div className="flex items-center gap-1">
        <span className="type-label text-muted-foreground">{label}</span>
        <Tooltip>
          <TooltipTrigger aria-label={`How ${label.toLowerCase()} is calculated`}>
            <Info className="text-muted-foreground size-3" />
          </TooltipTrigger>
          <TooltipContent>{formula}</TooltipContent>
        </Tooltip>
      </div>
      <span className="figure">{value}</span>
    </div>
  );
}

export function CurrentPayPanel({
  employee,
  onSetInitialCompensation,
}: {
  employee: EmployeeDetail;
  /** Absent hides the action — `EmployeeDetailScreen` only passes it when the signed-in role can
   * manage employees (`canManageEmployees`), matching the same gate `EmployeeController#
   * setInitialCompensation` enforces server-side. */
  onSetInitialCompensation?: () => void;
}) {
  if (!employee.currentBasePay) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Current pay</CardTitle>
        </CardHeader>
        <CardContent>
          <EmptyState
            title="No compensation record"
            detail="This employee has no pay on record yet — set their starting salary to begin the ledger."
            action={
              onSetInitialCompensation ? (
                <Button size="sm" onClick={onSetInitialCompensation}>Set starting salary</Button>
              ) : undefined
            }
          />
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Current pay</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <Money value={employee.currentBasePay} size="figure-lg" />

        {employee.components.length > 0 ? (
          <ul className="flex flex-col gap-1">
            {employee.components.map((component, index) => (
              <li key={index} className="type-body-sm flex items-center justify-between gap-4">
                <span className="text-muted-foreground">
                  {COMPONENT_LABEL[component.componentType] ?? component.componentType}
                  {component.percentOfBase != null
                    ? ` (${(Number(component.percentOfBase) * 100).toFixed(1)}% of base)`
                    : ""}
                </span>
                <Money value={component.amount} size="figure-sm" />
              </li>
            ))}
          </ul>
        ) : null}

        <BandBar
          variant="detail"
          salary={employee.currentBasePay}
          band={employee.band}
          position={{
            status: employee.bandStatus ?? "NO_BAND",
            percentThroughRange: Number(employee.rangePenetration ?? 0),
            compaRatio: Number(employee.compaRatio ?? 0),
          }}
        />

        {employee.band ? (
          <div className="flex gap-6">
            <Figure
              label="Compa-ratio"
              value={employee.compaRatio != null ? formatCompaRatio(Number(employee.compaRatio)) : "—"}
              formula="Compa-ratio = base pay ÷ band midpoint"
            />
            <Figure
              label="Range penetration"
              value={employee.rangePenetration != null ? formatPercent(Number(employee.rangePenetration)) : "—"}
              formula="Range penetration = (base pay − band min) ÷ (band max − band min)"
            />
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
