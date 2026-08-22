import { z } from "zod";

/** Mirrors `CreateFxRateRequest` on the backend (CLAUDE.md's forms convention: one Zod schema per
 * form, shared with the API types). `rate` stays a string end to end (§6.1: no money arithmetic
 * in TypeScript) — the server is the only thing that parses it as a number. */
export const fxRateFormSchema = z.object({
  baseCurrency: z.string().length(3, "Use a 3-letter currency code, e.g. EUR."),
  quoteCurrency: z.string().length(3, "Use a 3-letter currency code, e.g. USD."),
  rateMonth: z.string().min(1, "Required."),
  rate: z.string().min(1, "Required.").refine((v) => Number(v) > 0, "Rate must be greater than zero."),
});

export type FxRateFormValues = z.infer<typeof fxRateFormSchema>;
