import { describe, expect, it } from "vitest";
import { flagsFor } from "./band-health";
import type { BandHealthRow } from "@/lib/api/analytics";

/**
 * P11.4. `flagsFor` decides what the dots on a `/bands` cell mean, and the server independently
 * reports how many bands are in each state. Nothing links the two, so they can drift — and the
 * symptom is a summary strip reading "1 promotion cliff" over a matrix with none marked, which
 * makes both unbelievable.
 *
 * <p>These pin the thresholds. The reconciliation against a real 470-band response is done live
 * (see the P11.4 note in BuildPlan.md); this is the part that runs on every build.
 */

const STALE_AFTER_MONTHS = 18;

function row(overrides: Partial<BandHealthRow> = {}): BandHealthRow {
  return {
    bandId: "b1",
    jobFamily: "Engineering",
    levelCode: "L3",
    levelTitle: "Engineer",
    countryCode: "US",
    countryName: "United States",
    min: { amount: "100000.00", currency: "USD" },
    mid: { amount: "120000.00", currency: "USD" },
    max: { amount: "140000.00", currency: "USD" },
    rangeSpread: "0.4000",
    midpointProgression: "0.1500",
    gapToPreviousLevel: false,
    incumbents: 12,
    medianCompaRatio: "1.02",
    monthsSinceVersioned: 4,
    ...overrides,
  };
}

describe("flagsFor", () => {
  it("says nothing about a healthy band", () => {
    expect(flagsFor(row(), STALE_AFTER_MONTHS)).toEqual([]);
  });

  it("treats a promotion cliff as critical, not attention", () => {
    const flags = flagsFor(row({ gapToPreviousLevel: true }), STALE_AFTER_MONTHS);
    expect(flags).toHaveLength(1);
    expect(flags[0].severity).toBe("critical");
  });

  it("treats an empty band as attention — nobody's pay is wrong today", () => {
    const flags = flagsFor(row({ incumbents: 0 }), STALE_AFTER_MONTHS);
    expect(flags).toHaveLength(1);
    expect(flags[0].severity).toBe("attention");
  });

  it("flags staleness at the threshold, not one month past it", () => {
    expect(flagsFor(row({ monthsSinceVersioned: 17 }), STALE_AFTER_MONTHS)).toEqual([]);
    expect(flagsFor(row({ monthsSinceVersioned: 18 }), STALE_AFTER_MONTHS)).toHaveLength(1);
  });

  it("never invents a severity outside the two the palette defines", () => {
    const flags = flagsFor(
      row({ gapToPreviousLevel: true, incumbents: 0, monthsSinceVersioned: 40 }),
      STALE_AFTER_MONTHS,
    );
    expect(flags).toHaveLength(3);
    for (const flag of flags) {
      expect(["critical", "attention"]).toContain(flag.severity);
    }
  });

  it("puts the critical flag first, so the cell's first dot is its worst news", () => {
    const flags = flagsFor(
      row({ gapToPreviousLevel: true, incumbents: 0 }),
      STALE_AFTER_MONTHS,
    );
    expect(flags[0].severity).toBe("critical");
  });

  /** A band that has never been versioned reports null, which is not "stale forever". */
  it("does not call an unversioned band stale", () => {
    expect(flagsFor(row({ monthsSinceVersioned: null }), STALE_AFTER_MONTHS)).toEqual([]);
  });
});
