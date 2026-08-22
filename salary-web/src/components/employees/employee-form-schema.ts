import { z } from "zod";

/** Mirrors `EmployeeCreateRequest` on the backend (CLAUDE.md's forms convention: one Zod schema
 * per form, shared with the API types). `fte` stays a string end to end (§6.1: no money
 * arithmetic in TypeScript) — the server is the only thing that parses it as a number.
 *
 * No `managerId` field: assigning a manager needs a person-search picker this pass didn't build
 * (there's no edit-employee screen yet either, so this matches that same scope trim) — a new hire
 * can be created without one and the field stays settable only by a future edit screen. */
export const employeeFormSchema = z.object({
  employeeNumber: z.string().min(1, "Required."),
  firstName: z.string().min(1, "Required."),
  lastName: z.string().min(1, "Required."),
  workEmail: z.string().min(1, "Required.").email("Enter a valid email."),
  departmentId: z.string().min(1, "Required."),
  locationId: z.string().min(1, "Required."),
  jobFamilyId: z.string().min(1, "Required."),
  jobLevelId: z.string().min(1, "Required."),
  hireDate: z.string().min(1, "Required."),
  employmentType: z.string().min(1, "Required."),
  fte: z
    .string()
    .min(1, "Required.")
    .refine((v) => Number(v) >= 0.01 && Number(v) <= 1, "FTE must be between 0.01 and 1.00."),
});

export type EmployeeFormValues = z.infer<typeof employeeFormSchema>;
