import { z } from "zod";

/** Mirrors `SaveViewRequest`'s bean validation — same limits, so a valid form is a valid request. */
export const saveViewFormSchema = z.object({
  name: z.string().trim().min(1, "Give the view a name.").max(80, "80 characters at most."),
  visibility: z.enum(["private", "shared"]),
});

export type SaveViewFormValues = z.infer<typeof saveViewFormSchema>;
