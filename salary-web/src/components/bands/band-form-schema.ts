import { z } from "zod";

/**
 * Shared by create and version forms — mirrors `CreateBandRequest`/`UpdateBandRequest` on the
 * backend (CLAUDE.md's forms convention: one Zod schema per form, shared with the API types).
 * `minAmount`/`midAmount`/`maxAmount` stay strings end to end — CLAUDE.md §6.1 forbids money
 * arithmetic in TypeScript, but a `min ≤ mid ≤ max` *comparison* for form UX (never a computed,
 * displayed figure) is fine; the server's own check (`BandOrderingException`) is still the
 * authority, this only avoids a round-trip for the common typo.
 */
export const bandFormSchema = z
  .object({
    currency: z.string().length(3, "Use a 3-letter currency code, e.g. USD."),
    minAmount: z.string().min(1, "Required."),
    midAmount: z.string().min(1, "Required."),
    maxAmount: z.string().min(1, "Required."),
    effectiveFrom: z.string().min(1, "Required."),
    note: z.string().optional(),
  })
  .refine((data) => Number(data.minAmount) <= Number(data.midAmount), {
    message: "Minimum must be ≤ midpoint.",
    path: ["midAmount"],
  })
  .refine((data) => Number(data.midAmount) <= Number(data.maxAmount), {
    message: "Midpoint must be ≤ maximum.",
    path: ["maxAmount"],
  });

export type BandFormValues = z.infer<typeof bandFormSchema>;
