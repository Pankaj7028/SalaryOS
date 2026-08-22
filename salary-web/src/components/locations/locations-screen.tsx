"use client";

import { useMemo } from "react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, TableSkeleton } from "@/components/feedback/states";
import { useCountries, useLocations } from "@/lib/api/reference-queries";

/**
 * `/locations` (ui doc §6's nav shell). Reference data — `GET /api/reference/locations` is the
 * only endpoint (no create/update/delete on the backend), so this is a browse screen, same
 * reasoning as `/levels`.
 */
export function LocationsScreen() {
  const locations = useLocations();
  const countries = useCountries();

  const countryNames = useMemo(
    () => new Map((countries.data ?? []).map((c) => [c.code, c.name])),
    [countries.data],
  );

  const isLoading = locations.isLoading || countries.isLoading;
  const isError = locations.isError || countries.isError;

  const sorted = useMemo(
    () =>
      [...(locations.data ?? [])].sort((a, b) => {
        const countryCompare = (countryNames.get(a.countryCode) ?? a.countryCode).localeCompare(
          countryNames.get(b.countryCode) ?? b.countryCode,
        );
        return countryCompare !== 0 ? countryCompare : a.city.localeCompare(b.city);
      }),
    [locations.data, countryNames],
  );

  return (
    <div className="space-y-4">
      <header>
        <h1 className="type-title">Locations</h1>
        <p className="type-caption text-muted-foreground mt-1">
          Every office location, grouped by country. Locations back salary bands (job level ×
          country) and every employee&rsquo;s country-of-pay.
        </p>
      </header>

      {isError ? (
        <ErrorState
          title="Couldn't load locations"
          detail="Check your connection and try again."
          action={<Button size="sm" variant="outline" onClick={() => { locations.refetch(); countries.refetch(); }}>Retry</Button>}
        />
      ) : isLoading ? (
        <TableSkeleton columns={["180px", "160px", "220px", "80px"]} rows={8} />
      ) : sorted.length === 0 ? (
        <EmptyState title="No locations yet" detail="Locations are seeded reference data." />
      ) : (
        <div className="border-border overflow-x-auto rounded-lg border">
          <Table>
            <TableHeader className="bg-muted/40">
              <TableRow className="h-10">
                <TableHead className="type-label text-muted-foreground">Country</TableHead>
                <TableHead className="type-label text-muted-foreground">City</TableHead>
                <TableHead className="type-label text-muted-foreground">Name</TableHead>
                <TableHead className="type-label text-muted-foreground">Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sorted.map((location) => (
                <TableRow key={location.id} className="h-10">
                  <TableCell className="type-body-sm">{countryNames.get(location.countryCode) ?? location.countryCode}</TableCell>
                  <TableCell className="type-body-sm">{location.city}</TableCell>
                  <TableCell className="type-body-sm">{location.name}</TableCell>
                  <TableCell className={location.isActive ? "type-body-sm" : "type-body-sm text-muted-foreground"}>
                    {location.isActive ? "Active" : "Inactive"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
