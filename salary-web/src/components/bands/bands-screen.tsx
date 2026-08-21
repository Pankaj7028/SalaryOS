"use client";

import { useMemo, useState } from "react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Money } from "@/components/comp/money";
import { EmptyState, ErrorState, TableSkeleton } from "@/components/feedback/states";
import { useBands } from "@/lib/api/bands-queries";
import { useCountries, useJobLevels } from "@/lib/api/reference-queries";
import { CreateBandDialog } from "@/components/bands/create-band-dialog";
import { BandDetailDialog } from "@/components/bands/band-detail-dialog";
import type { Band } from "@/lib/api/bands";

type EmptyCell = { jobLevelId: string; countryCode: string };

/**
 * `/bands` (ui doc §8.6). Grid of level × country; each filled cell shows min–mid–max and
 * headcount and opens a version-history dialog with a "new version" sub-form. An empty cell opens
 * a create dialog. Job levels come from the reference endpoint (all of them, not just ones with a
 * band yet) so an empty cell is visible and clickable, not just absent.
 */
export function BandsScreen() {
  const bands = useBands();
  const jobLevels = useJobLevels();
  const countries = useCountries();
  const [emptyCell, setEmptyCell] = useState<EmptyCell | null>(null);
  const [selectedVersions, setSelectedVersions] = useState<Band[] | null>(null);

  const byCell = useMemo(() => {
    const map = new Map<string, Band[]>();
    for (const band of bands.data ?? []) {
      const key = `${band.jobLevelId}|${band.countryCode}`;
      const list = map.get(key) ?? [];
      list.push(band);
      map.set(key, list);
    }
    for (const list of map.values()) {
      list.sort((a, b) => (a.effectiveFrom < b.effectiveFrom ? 1 : -1));
    }
    return map;
  }, [bands.data]);

  if (bands.isLoading || jobLevels.isLoading || countries.isLoading) {
    return <TableSkeleton columns={["140px", "100px", "100px", "100px"]} rows={6} />;
  }

  if (bands.isError) {
    return (
      <ErrorState
        title="Couldn't load salary bands"
        detail="Check your connection and try again."
        action={<Button size="sm" variant="outline" onClick={() => bands.refetch()}>Retry</Button>}
      />
    );
  }

  const levels = jobLevels.data ?? [];
  const countryList = countries.data ?? [];

  if (levels.length === 0 || countryList.length === 0) {
    return <EmptyState title="No job levels or countries yet" detail="Bands are defined per job level and country — add those first." />;
  }

  const emptyCellLevel = emptyCell ? levels.find((l) => l.id === emptyCell.jobLevelId) : null;
  const emptyCellCountry = emptyCell ? countryList.find((c) => c.code === emptyCell.countryCode) : null;
  const selectedLevel = selectedVersions ? levels.find((l) => l.id === selectedVersions[0].jobLevelId) : null;
  const selectedCountry = selectedVersions ? countryList.find((c) => c.code === selectedVersions[0].countryCode) : null;

  return (
    <div className="space-y-4">
      <header>
        <h1 className="type-title">Salary bands</h1>
        <p className="type-caption text-muted-foreground mt-1">
          Job level × country. Click a filled cell for its version history, or an empty one to create a band.
        </p>
      </header>

      <div className="border-border overflow-x-auto rounded-lg border">
        <Table>
          <TableHeader className="bg-muted/40">
            <TableRow className="h-10">
              <TableHead className="type-label text-muted-foreground">Level</TableHead>
              {countryList.map((country) => (
                <TableHead key={country.code} className="type-label text-muted-foreground">
                  {country.name}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {levels.map((level) => (
              <TableRow key={level.id} className="h-14">
                <TableCell className="type-body-sm whitespace-nowrap">{level.title}</TableCell>
                {countryList.map((country) => {
                  const versions = byCell.get(`${level.id}|${country.code}`);
                  const inForce = versions?.[0];
                  return (
                    <TableCell key={country.code}>
                      {inForce ? (
                        <button
                          type="button"
                          onClick={() => setSelectedVersions(versions!)}
                          className="hover:bg-muted focus-visible:ring-ring/50 -mx-2 flex flex-col items-start gap-0.5 rounded-md px-2 py-1 text-left focus-visible:ring-3 focus-visible:outline-none"
                        >
                          <span className="figure-sm">
                            <Money value={inForce.min} size="figure-sm" showCurrency={false} />
                            {" – "}
                            <Money value={inForce.mid} size="figure-sm" showCurrency={false} />
                            {" – "}
                            <Money value={inForce.max} size="figure-sm" />
                          </span>
                          <span className="type-caption text-muted-foreground">
                            {inForce.headcount} {inForce.headcount === 1 ? "employee" : "employees"}
                          </span>
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setEmptyCell({ jobLevelId: level.id, countryCode: country.code })}
                          className="text-muted-foreground hover:border-border hover:text-foreground -mx-2 rounded-md border border-dashed border-transparent px-2 py-1 text-left"
                        >
                          <span className="type-caption">+ Add band</span>
                        </button>
                      )}
                    </TableCell>
                  );
                })}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {emptyCell && emptyCellLevel && emptyCellCountry ? (
        <CreateBandDialog
          open
          onOpenChange={(open) => !open && setEmptyCell(null)}
          jobLevelId={emptyCell.jobLevelId}
          countryCode={emptyCell.countryCode}
          levelTitle={emptyCellLevel.title}
          countryName={emptyCellCountry.name}
          defaultCurrency={emptyCellCountry.defaultCurrency}
        />
      ) : null}

      {selectedVersions && selectedLevel && selectedCountry ? (
        <BandDetailDialog
          open
          onOpenChange={(open) => !open && setSelectedVersions(null)}
          versions={selectedVersions}
          levelTitle={selectedLevel.title}
          countryName={selectedCountry.name}
        />
      ) : null}
    </div>
  );
}
