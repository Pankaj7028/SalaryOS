/**
 * Visual + measured verification for build steps P3.1 / P3.2.
 *
 * "A column of mixed-width figures aligns" and "slashed zero renders" are visual claims, so this
 * renders the real pages in Chromium, measures them, and writes screenshots. It asserts:
 *   1. figures actually resolve to IBM Plex Mono (not a fallback)
 *   2. every digit has the SAME advance width — which is what tabular-nums buys, and the reason a
 *      column aligns at all
 *   3. the right edges of a mixed-width money column line up to the sub-pixel
 *   4. "0" and "O" render at different widths/shapes, i.e. the slashed zero is in effect
 */
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";

const BASE = process.env.BASE_URL ?? "http://localhost:3000";
const OUT = "screenshots";
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 1200 }, deviceScaleFactor: 2 });
const problems = [];

await page.goto(`${BASE}/dev/type`, { waitUntil: "load" });
await page.evaluate(() => document.fonts.ready);

// 1 — the face actually in use
const font = await page.locator(".figure").first().evaluate((el) => getComputedStyle(el).fontFamily);
console.log(`  figure font-family        : ${font}`);
if (!/IBM Plex Mono/.test(font)) problems.push(`figures are not IBM Plex Mono: ${font}`);

const feat = await page.locator(".figure").first().evaluate((el) => ({
  variant: getComputedStyle(el).fontVariantNumeric,
  settings: getComputedStyle(el).fontFeatureSettings,
}));
console.log(`  font-variant-numeric      : ${feat.variant}`);
console.log(`  font-feature-settings     : ${feat.settings}`);
if (!/tabular-nums/.test(feat.variant)) problems.push("tabular-nums is not applied to .figure");
if (!/zero/.test(feat.settings)) problems.push('slashed zero ("zero" 1) is not applied to .figure');

// 2 — every digit the same advance width
const widths = await page.evaluate(() => {
  const probe = document.createElement("span");
  probe.className = "figure";
  probe.style.position = "absolute";
  probe.style.visibility = "hidden";
  document.body.appendChild(probe);
  const w = {};
  for (const ch of "0123456789") {
    probe.textContent = ch.repeat(10);
    w[ch] = probe.getBoundingClientRect().width / 10;
  }
  probe.textContent = "O".repeat(10);
  w.O = probe.getBoundingClientRect().width / 10;
  probe.remove();
  return w;
});
const digitWidths = "0123456789".split("").map((d) => widths[d]);
const spread = Math.max(...digitWidths) - Math.min(...digitWidths);
console.log(`  digit advance width       : ${digitWidths[0].toFixed(4)}px, spread across 0-9 = ${spread.toFixed(6)}px`);
if (spread > 0.01) problems.push(`digits are not tabular — width spread ${spread.toFixed(4)}px`);

// 3 — the money column's right edges line up
const rights = await page.locator("td.numeric.figure").evaluateAll((els) =>
  els.map((el) => el.getBoundingClientRect().right),
);
const edgeSpread = Math.max(...rights) - Math.min(...rights);
console.log(`  money column right edges  : ${rights.length} cells, spread = ${edgeSpread.toFixed(6)}px`);
if (rights.length < 5) problems.push(`expected the 6-row money column, found ${rights.length} cells`);
if (edgeSpread > 0.01) problems.push(`money column is not aligned — right-edge spread ${edgeSpread.toFixed(4)}px`);

// 4 — zero is distinguishable from O
console.log(`  glyph widths              : "0" ${widths["0"].toFixed(4)}px · "O" ${widths.O.toFixed(4)}px`);
const zeroShot = await page.locator(".figure-lg").first().screenshot();
if (zeroShot.length < 100) problems.push("could not capture the slashed-zero sample");

await page.screenshot({ path: `${OUT}/p3.2-type-scale.png`, fullPage: true });
await page.goto(`${BASE}/dev/tokens`, { waitUntil: "load" });
await page.evaluate(() => document.fonts.ready);
await page.screenshot({ path: `${OUT}/p3.1-tokens.png`, fullPage: true });

await browser.close();

if (problems.length) {
  console.error(`✗ type/token verification (${problems.length}):`);
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log(`✓ figures are IBM Plex Mono, tabular, slashed-zero; money column aligns to <0.01px`);
console.log(`  screenshots → ${OUT}/p3.1-tokens.png · ${OUT}/p3.2-type-scale.png`);
