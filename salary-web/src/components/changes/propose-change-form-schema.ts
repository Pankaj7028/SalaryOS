import { z } from "zod";

/**
 * Mirrors `ProposeChangeRequest` on the backend (CLAUDE.md's forms convention: one Zod schema per
 * form, shared with the API types). `newBaseAmount` stays a string end to end — CLAUDE.md §6.1
 * forbids money arithmetic in TypeScript. Whether a note is *required* depends on the live impact
 * preview's `noteRequired` (landing outside the band), which this static schema can't see — the
 * dialog enforces that separately, next to the note field, matching ui doc §8.4.
 */
export const proposeChangeFormSchema = z.object({
  effectiveDate: z.string().min(1, "Required."),
  newBaseAmount: z.string().min(1, "Required.").refine((v) => Number(v) > 0, "Must be greater than zero."),
  changeReason: z.string().min(1, "Required."),
  performanceRating: z.string().optional(),
  note: z.string().optional(),
});

export type ProposeChangeFormValues = z.infer<typeof proposeChangeFormSchema>;
