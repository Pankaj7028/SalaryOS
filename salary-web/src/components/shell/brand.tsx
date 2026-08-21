/**
 * Brand mark: a 28px rounded square carrying the letter mark on --primary, with
 * the wordmark and the org name beneath it as a label (§6.1).
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
      <div className="leading-none">
        <p className="type-subsection text-topbar-foreground">Salary OS</p>
        <p className="type-label text-muted-foreground mt-0.5">ACME</p>
      </div>
    </div>
  );
}
