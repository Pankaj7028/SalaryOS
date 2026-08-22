/**
 * Brand mark: a 28px rounded square carrying the letter mark on --primary, with
 * the wordmark and the org name beneath it as a label (§6.1).
 *
 * The wordmark/org-name text hides below 768px — the same breakpoint chrome.css
 * uses everywhere else to switch to the mobile Sheet nav. Found during P8's QA
 * pass: at 375px the topbar's right cluster (search icon, currency toggle,
 * theme, avatar — none of which shrink further) plus this text overflowed the
 * viewport by ~12px on every page. The name is still one tap away in the Sheet
 * nav's own header, so nothing is lost, only the redundant inline copy.
 */
export function Brand() {
  return (
    <div className="flex items-center gap-2.5">
      <div
        aria-hidden
        className="bg-primary text-primary-foreground grid size-7 shrink-0 place-items-center rounded-md text-sm font-semibold"
      >
        S
      </div>
      <div className="hidden leading-none md:block">
        <p className="type-subsection text-topbar-foreground">Salary OS</p>
        <p className="type-label text-muted-foreground mt-0.5">ACME</p>
      </div>
    </div>
  );
}
