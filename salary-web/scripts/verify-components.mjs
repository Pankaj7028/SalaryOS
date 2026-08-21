/**
 * Verification for build step P3.7 — the compensation components.
 *
 * The rule worth measuring is §7.1's: an out-of-band marker must sit OUTSIDE the
 * track, never clamped to its end. Clamping is invisible in a screenshot review —
 * a marker pinned at the minimum looks like someone paid exactly the minimum —
 * so the marker's geometry is compared against the track's here.
 */
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";

const BASE = process.env.BASE_URL ?? "http://localhost:3100";
const OUT = "screenshots";
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1400 }, deviceScaleFactor: 2 });
const problems = [];

await page.goto(`${BASE}/dev/components`, { waitUntil: "load" });
await page.evaluate(() => document.fonts.ready);

/** For the Nth BandBar in the light panel, compare marker centre to track box. */
const geometry = await page.evaluate(() => {
  const panel = document.querySelector(".app-light");
  const bars = [...panel.querySelectorAll(".relative.h-3\\.5")];
  return bars.map((bar) => {
    const track = bar.querySelector(".bg-muted");
    const marker = bar.querySelector('[class*="bg-positive"],[class*="bg-attention"],[class*="bg-critical"]');
    if (!track || !marker) return null;
    const t = track.getBoundingClientRect();
    const m = marker.getBoundingClientRect();
    return {
      tone: marker.className.match(/bg-(positive|attention|critical)/)?.[1],
      markerCentre: m.left + m.width / 2,
      trackLeft: t.left,
      trackRight: t.right,
    };
  });
});

const bars = geometry.filter(Boolean);
console.log(`  BandBars measured in the light panel : ${bars.length}`);

// The gallery renders inline/default/detail per case, in case order.
const belowMin = bars.filter((b) => b.tone === "attention");
const aboveMax = bars.filter((b) => b.tone === "critical");
const inBand = bars.filter((b) => b.tone === "positive");

console.log(`  in-band markers                      : ${inBand.length}`);
for (const b of inBand) {
  if (b.markerCentre < b.trackLeft - 0.5 || b.markerCentre > b.trackRight + 0.5) {
    problems.push(`an in-band marker fell outside its track`);
  }
}

console.log(`  below-minimum markers                : ${belowMin.length}`);
for (const b of belowMin) {
  const overshoot = b.trackLeft - b.markerCentre;
  if (overshoot <= 0) {
    problems.push(`below-min marker is clamped to the track start (overshoot ${overshoot.toFixed(2)}px) — it must sit outside`);
  }
}
if (belowMin[0]) console.log(`    overshoot past track start         : ${(belowMin[0].trackLeft - belowMin[0].markerCentre).toFixed(2)}px`);

console.log(`  above-maximum markers                : ${aboveMax.length}`);
for (const b of aboveMax) {
  const overshoot = b.markerCentre - b.trackRight;
  if (overshoot <= 0) {
    problems.push(`above-max marker is clamped to the track end (overshoot ${overshoot.toFixed(2)}px) — it must sit outside`);
  }
}
if (aboveMax[0]) console.log(`    overshoot past track end           : ${(aboveMax[0].markerCentre - aboveMax[0].trackRight).toFixed(2)}px`);

// ---- no band is its own state, not a centred marker ------------------------
const noBandMarkers = await page.evaluate(() => {
  const links = [...document.querySelectorAll(".app-light a")].filter((a) => /No band/.test(a.textContent));
  return { links: links.length, dashed: document.querySelectorAll(".app-light .border-dashed").length };
});
console.log(`  no-band: dashed tracks ${noBandMarkers.dashed}, "No band …" links ${noBandMarkers.links}`);
if (noBandMarkers.dashed < 1) problems.push("the no-band state did not render a dashed track");
if (noBandMarkers.links < 1) problems.push("the no-band state did not link to band setup");

// ---- accessible sentences ---------------------------------------------------
const sentences = await page.locator(".app-light .sr-only").allInnerTexts();
console.log(`  accessible sentences                 : ${sentences.length}`);
for (const s of sentences.slice(0, 5)) console.log(`    · ${s}`);

const inBandSentence = sentences.find((s) => /in band/.test(s));
if (!inBandSentence || !/compa-ratio \d+\.\d{2}/.test(inBandSentence)) {
  problems.push(`in-band sentence missing a two-decimal compa-ratio: ${inBandSentence}`);
}
if (!inBandSentence || !/% through range/.test(inBandSentence)) {
  problems.push(`in-band sentence missing "% through range": ${inBandSentence}`);
}
if (!sentences.some((s) => /no band/i.test(s))) problems.push("no-band sentence missing");
if (!sentences.every((s) => /GBP/.test(s))) problems.push("a sentence omitted the currency code");

await page.screenshot({ path: `${OUT}/p3.7-components.png`, fullPage: true });
await browser.close();

if (problems.length) {
  console.error(`✗ component verification (${problems.length}):`);
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log("✓ out-of-band markers sit outside the track, no-band is its own state, sentences carry money + compa-ratio + range");
