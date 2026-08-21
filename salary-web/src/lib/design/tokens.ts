/**
 * The canonical token inventory, grouped as docs/salary-management-ui.md §2 groups them.
 *
 * This list must stay in step with `src/app/theme.css` — `scripts/check-tokens.mjs` fails if the
 * two ever disagree, so the audit page at /dev/tokens genuinely shows *every* token rather than
 * the ones somebody remembered to add.
 *
 * `--radius` is intentionally absent: it is a length, not a colour, and the audit page renders it
 * separately as a corner sample.
 */
export type TokenGroup = {
  title: string;
  note?: string;
  tokens: string[];
};

export const TOKEN_GROUPS: TokenGroup[] = [
  {
    title: "Surfaces",
    tokens: [
      "background",
      "foreground",
      "card",
      "card-foreground",
      "popover",
      "popover-foreground",
    ],
  },
  {
    title: "Primary",
    note: "Lifts to emerald-400 in dark — it is read as text as often as it is used as a fill.",
    tokens: ["primary", "primary-foreground", "primary-hover", "primary-active", "primary-subtle"],
  },
  {
    title: "Neutrals",
    tokens: [
      "secondary",
      "secondary-foreground",
      "muted",
      "muted-foreground",
      "accent",
      "accent-foreground",
    ],
  },
  {
    title: "Destructive & controls",
    tokens: ["destructive", "destructive-foreground", "border", "input", "ring"],
  },
  {
    title: "Band status & deltas",
    note: "The product semantics: in band, below minimum, above maximum, unchanged.",
    tokens: [
      "positive",
      "positive-subtle",
      "attention",
      "attention-subtle",
      "critical",
      "critical-subtle",
      "neutral-figure",
    ],
  },
  {
    title: "Chrome",
    note: "Dark is flat — topbar, sidebar and content all sit at --background.",
    tokens: [
      "topbar",
      "topbar-foreground",
      "sidebar",
      "sidebar-foreground",
      "sidebar-accent",
      "sidebar-accent-foreground",
      "sidebar-primary",
      "sidebar-primary-foreground",
      "sidebar-border",
      "sidebar-ring",
      "content",
    ],
  },
  {
    title: "Charts",
    note: "Ordered and colour-blind-safe. Never re-ordered per chart.",
    tokens: ["chart-1", "chart-2", "chart-3", "chart-4", "chart-5", "chart-6"],
  },
];

export const ALL_TOKENS: string[] = TOKEN_GROUPS.flatMap((g) => g.tokens);
