import { toast } from "sonner";

/**
 * The ONLY way to raise a toast in salary-web (CLAUDE.md §9, ui doc §7.7).
 *
 * Importing `toast` directly into a feature is what makes dwell times and
 * positions drift apart within a week. There is exactly one <Toaster>, at the
 * root — a second one silently shadows it and nothing appears and nothing errors.
 *
 * Dwell times are fixed here: success 3s, info 4s, warning 5s, failure 6s. A
 * failure gets longest because it is the one people need time to read.
 */
const DWELL = { success: 3000, info: 4000, warning: 5000, failure: 6000 } as const;

/**
 * @param summary past-tense outcome, 2–4 words, no full stop: "Change approved".
 * @param detail names the record and the specifics. Omit rather than invent.
 */
export function success(summary: string, detail?: string) {
  toast.success(summary, { description: detail, duration: DWELL.success });
}

export function info(summary: string, detail?: string) {
  toast.info(summary, { description: detail, duration: DWELL.info });
}

export function warning(summary: string, detail?: string) {
  toast.warning(summary, { description: detail, duration: DWELL.warning });
}

/**
 * Failure toast. `detail` is the server's ProblemDetail.detail where there is
 * one; where the server gave no reason, nothing is shown rather than filler —
 * "Something went wrong" tells the reader strictly less than silence does.
 *
 * Not for field validation (that belongs inline, next to the field, and never
 * both), nor for 401/403/network, which the fetch wrapper reports centrally.
 */
export function failure(error: unknown, summary: string) {
  const detail =
    typeof error === "object" && error !== null && "detail" in error
      ? String((error as { detail: unknown }).detail)
      : error instanceof Error && error.message
        ? error.message
        : undefined;
  toast.error(summary, { description: detail, duration: DWELL.failure });
}

/** Bulk actions emit ONE toast carrying the counts, never one per row. */
export function bulk(summary: string, counts: { done: number; rejected?: number }) {
  const parts = [`${counts.done} succeeded`];
  if (counts.rejected) parts.push(`${counts.rejected} rejected`);
  toast.success(summary, { description: parts.join(" · "), duration: DWELL.success });
}
