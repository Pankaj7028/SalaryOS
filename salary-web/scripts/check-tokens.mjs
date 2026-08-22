/**
 * Fails if the design tokens drift apart.
 *
 * Three things must agree, or the /dev/tokens audit page quietly stops being an audit:
 *   1. every token declared in theme.css's light block appears in tokens.ts
 *   2. every token listed in tokens.ts is actually declared in theme.css
 *   3. every colour token in the light block is overridden in the dark block
 *
 * (3) is the one that bites: a token added to light only inherits the light value in dark, so
 * it looks fine until someone switches theme on a screen you did not check.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const css = readFileSync(join(here, "../src/app/theme.css"), "utf8");
const ts = readFileSync(join(here, "../src/lib/design/tokens.ts"), "utf8");

/** Token names declared inside a given selector block. */
function declared(startPattern) {
  const start = css.indexOf(startPattern);
  if (start === -1) throw new Error(`block not found: ${startPattern}`);
  const block = css.slice(start, css.indexOf("\n}", start));
  return new Set([...block.matchAll(/^\s+--([a-z0-9-]+):/gm)].map((m) => m[1]));
}

const light = declared(":root,\n.app-light {");
const dark = declared(".app-dark {");

// tokens.ts deliberately omits --radius (a length, rendered separately by the audit page)
light.delete("radius");

const listed = new Set(
  [...ts.matchAll(/tokens: \[([^\]]*)\]/gs)].flatMap((m) =>
    [...m[1].matchAll(/"([a-z0-9-]+)"/g)].map((x) => x[1]),
  ),
);

const problems = [];
const diff = (a, b) => [...a].filter((x) => !b.has(x)).sort();

for (const t of diff(light, listed)) problems.push(`theme.css declares --${t}, tokens.ts omits it`);
for (const t of diff(listed, light))
  problems.push(`tokens.ts lists --${t}, theme.css never declares it`);
for (const t of diff(light, dark))
  problems.push(`--${t} has no .app-dark override (it will inherit the light value)`);

if (problems.length) {
  console.error(`✗ token drift (${problems.length}):`);
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log(`✓ tokens agree — ${light.size} colour tokens, all listed and all overridden in dark`);

/* ---------------------------------------------------------------------------
 * WCAG contrast, measured rather than eyeballed (UI doc §12.6).
 *
 * The doc lifts --primary to emerald-400 in dark precisely because the light
 * pine fails as text on near-black. That claim deserves a number, not a note.
 * ------------------------------------------------------------------------ */

/** Resolve a theme's tokens to concrete hex, following var() indirection. */
function resolve(block) {
  const start = css.indexOf(block);
  const body = css.slice(start, css.indexOf("\n}", start));
  const raw = new Map(
    [...body.matchAll(/^\s+--([a-z0-9-]+):\s*([^;]+);/gm)].map((m) => [m[1], m[2].trim()]),
  );
  const seen = new Set();
  const deref = (name) => {
    const v = raw.get(name);
    if (!v || seen.has(name)) return v;
    const ref = v.match(/^var\(--([a-z0-9-]+)\)$/);
    if (!ref) return v;
    seen.add(name);
    const out = deref(ref[1]);
    seen.delete(name);
    return out;
  };
  return new Map([...raw.keys()].map((k) => [k, deref(k)]));
}

const lum = (hex) => {
  const n = hex.replace("#", "");
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(n.slice(i, i + 2), 16) / 255);
  const f = (c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
};
const ratio = (a, b) => {
  const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
};

// [foreground, background, minimum] — 4.5 is WCAG AA for body text, 3.0 is WCAG 1.4.11 for a
// UI-component boundary that's the only way to identify it (an unfocused input/select/textarea
// border, per components/ui/input.tsx's border-input — --border itself is exempt, a decorative
// table/panel divider rather than a control's sole identifying edge). P9.5 found --input at
// ~1.4-1.9:1 in both themes with nothing here catching it; added so it can't regress silently.
const PAIRS = [
  ["foreground", "background", 4.5],
  ["card-foreground", "card", 4.5],
  ["muted-foreground", "background", 4.5],
  ["muted-foreground", "card", 4.5],
  ["primary", "background", 4.5],
  ["primary-foreground", "primary", 4.5],
  ["positive", "background", 4.5],
  ["attention", "background", 4.5],
  ["critical", "background", 4.5],
  ["neutral-figure", "background", 4.5],
  ["sidebar-foreground", "sidebar", 4.5],
  ["destructive-foreground", "destructive", 4.5],
  ["input", "background", 3.0],
  ["input", "card", 3.0],
];

const themes = { light: resolve(":root,\n.app-light {"), dark: resolve(".app-dark {") };
const fails = [];
const rows = [];

for (const [name, map] of Object.entries(themes)) {
  for (const [fg, bg, min] of PAIRS) {
    const a = map.get(fg);
    const b = map.get(bg) ?? themes.light.get(bg);
    if (!a || !b || !a.startsWith("#") || !b.startsWith("#")) continue;
    const r = ratio(a, b);
    rows.push(`  ${name.padEnd(5)} ${(fg + " on " + bg).padEnd(38)} ${r.toFixed(2).padStart(6)}:1`);
    if (r < min) fails.push(`${name}: --${fg} on --${bg} is ${r.toFixed(2)}:1, needs ${min}:1`);
  }
}

console.log(rows.join("\n"));
if (fails.length) {
  console.error(`✗ contrast (${fails.length}):`);
  for (const f of fails) console.error(`  - ${f}`);
  process.exit(1);
}
console.log(`✓ contrast — ${rows.length} pairs measured, all at or above WCAG AA`);
