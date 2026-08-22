"use client";

import { useMemo } from "react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, TableSkeleton } from "@/components/feedback/states";
import { useJobFamilies, useJobLevels } from "@/lib/api/reference-queries";

/**
 * `/levels` (ui doc §6's nav shell). Reference data — `GET /api/reference/job-levels` is the only
 * endpoint this domain has (no create/update/delete exists on the backend), so this is a browse
 * screen, the same role bands play for job level × country: something to look up, not edit.
 * Levels are seeded once per job family and rarely change; adding management here is future work
 * once there's an API to back it.
 */
export function LevelsScreen() {
  const jobLevels = useJobLevels();
  const jobFamilies = useJobFamilies();

  const familyNames = useMemo(
    () => new Map((jobFamilies.data ?? []).map((f) => [f.id, f.name])),
    [jobFamilies.data],
  );

  const isLoading = jobLevels.isLoading || jobFamilies.isLoading;
  const isError = jobLevels.isError || jobFamilies.isError;

  const sorted = useMemo(
    () =>
      [...(jobLevels.data ?? [])].sort((a, b) => {
        const familyCompare = (familyNames.get(a.jobFamilyId) ?? "").localeCompare(familyNames.get(b.jobFamilyId) ?? "");
        return familyCompare !== 0 ? familyCompare : a.sortOrder - b.sortOrder;
      }),
    [jobLevels.data, familyNames],
  );

  return (
    <div className="space-y-4">
      <header>
        <h1 className="type-title">Job levels</h1>
        <p className="type-caption text-muted-foreground mt-1">
          Every level, grouped by job family, in progression order. Levels back salary bands
          (job level × country) — see <span className="type-body-sm">Salary bands</span> to manage pay ranges.
        </p>
      </header>

      {isError ? (
        <ErrorState
          title="Couldn't load job levels"
          detail="Check your connection and try again."
          action={<Button size="sm" variant="outline" onClick={() => { jobLevels.refetch(); jobFamilies.refetch(); }}>Retry</Button>}
        />
      ) : isLoading ? (
        <TableSkeleton columns={["220px", "100px", "260px", "80px"]} rows={8} />
      ) : sorted.length === 0 ? (
        <EmptyState title="No job levels yet" detail="Job levels are seeded reference data." />
      ) : (
        <div className="border-border overflow-x-auto rounded-lg border">
          <Table>
            <TableHeader className="bg-muted/40">
              <TableRow className="h-10">
                <TableHead className="type-label text-muted-foreground">Job family</TableHead>
                <TableHead className="type-label text-muted-foreground">Code</TableHead>
                <TableHead className="type-label text-muted-foreground">Title</TableHead>
                <TableHead className="type-label text-muted-foreground">Order</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sorted.map((level) => (
                <TableRow key={level.id} className="h-10">
                  <TableCell className="type-body-sm">{familyNames.get(level.jobFamilyId) ?? "—"}</TableCell>
                  <TableCell className="figure-sm">{level.levelCode}</TableCell>
                  <TableCell className="type-body-sm">{level.title}</TableCell>
                  <TableCell className="figure-sm">{level.sortOrder}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
