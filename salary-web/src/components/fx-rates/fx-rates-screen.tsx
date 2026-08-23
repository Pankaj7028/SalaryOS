"use client";

import { useMemo, useState } from "react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, TableSkeleton } from "@/components/feedback/states";
import { useFxRateAdmin } from "@/lib/api/fx-rates-queries";
import { useCountries } from "@/lib/api/reference-queries";
import { useSession } from "@/lib/auth/auth-queries";
import { canManageFxRates } from "@/lib/auth/roles";
import { AddFxRateDialog } from "@/components/fx-rates/add-fx-rate-dialog";
import { FxCoverageMatrix } from "@/components/fx-rates/fx-coverage-matrix";
import type { MissingFxRateMonth } from "@/lib/api/fx-rates";

/**
 * `/admin/fx-rates` (ui doc §8.9). Normalisation pins one rate per (currency, month) at write
 * time (CLAUDE.md §6.4) — this screen exists to keep the trailing window covered going forward,
 * not to edit or recompute a rate a past compensation record may already reference.
 */
export function FxRatesScreen() {
  const admin = useFxRateAdmin();
  const countries = useCountries();
  const session = useSession();
  const canManage = session.data ? canManageFxRates(session.data.role) : false;
  const [dialogPrefill, setDialogPrefill] = useState<MissingFxRateMonth | null | undefined>(undefined);

  const baseCurrencyOptions = useMemo(
    () => Array.from(new Set((countries.data ?? []).map((c) => c.defaultCurrency))).sort(),
    [countries.data],
  );

  if (admin.isLoading) {
    return <TableSkeleton columns={["100px", "90px", "90px", "120px"]} rows={6} />;
  }

  if (admin.isError) {
    return (
      <ErrorState
        title="Couldn't load FX rates"
        detail="Check your connection and try again."
        action={<Button size="sm" variant="outline" onClick={() => admin.refetch()}>Retry</Button>}
      />
    );
  }

  const rates = admin.data?.rates ?? [];
  const missing = admin.data?.missing ?? [];
  const coverage = admin.data?.coverage;

  return (
    <div className="space-y-6">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="type-title">FX rates</h1>
          <p className="type-caption text-muted-foreground mt-1">
            One pinned rate per currency and month. Reports never recompute at today&rsquo;s rate (CLAUDE.md §6.4).
          </p>
        </div>
        {canManage ? (
          <Button size="sm" onClick={() => setDialogPrefill(null)}>
            Add rate
          </Button>
        ) : null}
      </header>

      <section className="space-y-2">
        <h2 className="type-section">Missing months</h2>
        {missing.length === 0 ? (
          <p className="type-body-sm text-muted-foreground">
            The trailing 13 months are fully covered for every currency in use.
          </p>
        ) : (
          <ul className="flex flex-wrap gap-2" aria-label="Months missing a pinned rate">
            {missing.map((m) => (
              <li key={`${m.baseCurrency}|${m.rateMonth}`}>
                {canManage ? (
                  <button
                    type="button"
                    onClick={() => setDialogPrefill(m)}
                    className="border-attention/40 bg-attention-subtle text-attention hover:bg-attention/15 inline-flex items-center gap-1.5 rounded-full border px-3 py-1"
                  >
                    <span className="type-caption">{m.baseCurrency} → {m.quoteCurrency}</span>
                    <span className="figure-sm">{m.rateMonth}</span>
                    <span className="type-caption font-medium">+ Add</span>
                  </button>
                ) : (
                  <span className="border-attention/40 bg-attention-subtle text-attention inline-flex items-center gap-1.5 rounded-full border px-3 py-1">
                    <span className="type-caption">{m.baseCurrency} → {m.quoteCurrency}</span>
                    <span className="figure-sm">{m.rateMonth}</span>
                  </span>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-2">
        <div>
          <h2 className="type-section">Coverage</h2>
          <p className="type-caption text-muted-foreground mt-1">
            Only the currencies people are actually paid in. A currency nobody is on cannot block a
            write.
          </p>
        </div>
        {coverage ? (
          <FxCoverageMatrix coverage={coverage} canManage={canManage} onAddRate={setDialogPrefill} />
        ) : null}
      </section>

      <section className="space-y-2">
        <h2 className="type-section">Pinned rates</h2>
        {rates.length === 0 ? (
          <EmptyState
            title="No rates pinned yet"
            detail="Normalisation can't proceed for a currency until its month has a pinned rate."
          />
        ) : (
          <div className="border-border overflow-x-auto rounded-lg border">
            <Table>
              <TableHeader className="bg-muted/40">
                <TableRow className="h-10">
                  <TableHead className="type-label text-muted-foreground">Month</TableHead>
                  <TableHead className="type-label text-muted-foreground">From</TableHead>
                  <TableHead className="type-label text-muted-foreground">To</TableHead>
                  <TableHead className="type-label text-muted-foreground">Rate</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rates.map((rate) => (
                  <TableRow key={rate.id} className="h-10">
                    <TableCell className="figure-sm">{rate.rateMonth}</TableCell>
                    <TableCell className="figure-sm">{rate.baseCurrency}</TableCell>
                    <TableCell className="figure-sm">{rate.quoteCurrency}</TableCell>
                    <TableCell className="figure-sm numeric">{rate.rate}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </section>

      {dialogPrefill !== undefined ? (
        <AddFxRateDialog
          open
          onOpenChange={(open) => !open && setDialogPrefill(undefined)}
          prefill={dialogPrefill ?? undefined}
          baseCurrencyOptions={baseCurrencyOptions}
        />
      ) : null}
    </div>
  );
}
