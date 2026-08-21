/**
 * Verification for build step P3.3 — the application shell.
 *
 * The step's Verify is "collapse persists across reload; 375px pass", both of
 * which are behaviour rather than markup, so this drives a real browser.
 */
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";

const BASE = process.env.BASE_URL ?? "http://localhost:3000";
const OUT = "screenshots";
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
const page = await ctx.newPage();
const problems = [];
const width = () => page.locator("aside.app-sidebar").evaluate((el) => el.getBoundingClientRect().width);

await page.goto(BASE, { waitUntil: "load" });
await page.evaluate(() => document.fonts.ready);

const expanded = await width();
console.log(`  sidebar, expanded          : ${expanded}px`);
if (Math.round(expanded) !== 240) problems.push(`expanded sidebar should be 240px, is ${expanded}px`);
await page.screenshot({ path: `${OUT}/p3.3-shell-expanded.png`, fullPage: false });

// collapse
await page.getByRole("button", { name: "Collapse" }).click();
await page.waitForFunction(() => document.documentElement.dataset.sidebar === "collapsed");
// Move the pointer off the rail before measuring. The collapsed rail peeks back
// open on hover (§6.2), and after the click the mouse is still sitting on it —
// so measuring here without moving would read 240px and be reading it correctly.
await page.mouse.move(900, 400);
await page.waitForTimeout(400); // let the 240ms width transition settle
const collapsed = await width();
console.log(`  sidebar, collapsed         : ${collapsed}px`);
if (Math.round(collapsed) !== 60) problems.push(`collapsed sidebar should be 60px, is ${collapsed}px`);
await page.screenshot({ path: `${OUT}/p3.3-shell-collapsed.png`, fullPage: false });

// THE verify: does it survive a reload?
await page.reload({ waitUntil: "load" });
const afterReload = await width();
const attr = await page.evaluate(() => document.documentElement.dataset.sidebar);
console.log(`  after reload               : ${afterReload}px (data-sidebar="${attr}")`);
if (Math.round(afterReload) !== 60) problems.push(`collapse did not persist — sidebar is ${afterReload}px after reload`);

// the hover peek itself (§6.2): collapsed rail widens while the pointer is on it
await page.mouse.move(30, 400);
await page.waitForTimeout(400);
const peeked = await width();
console.log(`  collapsed rail on hover    : ${peeked}px`);
if (Math.round(peeked) !== 240) problems.push(`collapsed rail should peek to 240px on hover, got ${peeked}px`);
await page.mouse.move(900, 400);
await page.waitForTimeout(300);

// and does expanding come back?
await page.getByRole("button", { name: "Collapse" }).click();
await page.mouse.move(900, 400);
await page.waitForTimeout(400);
await page.reload({ waitUntil: "load" });
const restored = await width();
console.log(`  expanded again, reloaded   : ${restored}px`);
if (Math.round(restored) !== 240) problems.push(`expand did not persist — sidebar is ${restored}px after reload`);

// ---- 375px -----------------------------------------------------------------
const mobile = await ctx.newPage();
await mobile.setViewportSize({ width: 375, height: 812 });
await mobile.goto(BASE, { waitUntil: "load" });
await mobile.evaluate(() => document.fonts.ready);

const scroll = await mobile.evaluate(() => ({
  doc: document.documentElement.scrollWidth,
  win: window.innerWidth,
}));
console.log(`  375px document scrollWidth : ${scroll.doc}px vs viewport ${scroll.win}px`);
if (scroll.doc > scroll.win) problems.push(`horizontal scroll at 375px: ${scroll.doc}px > ${scroll.win}px`);

const railVisible = await mobile.locator("aside.app-sidebar").isVisible();
console.log(`  375px static rail visible   : ${railVisible}`);
if (railVisible) problems.push("the static sidebar is still visible at 375px; it should be the Sheet");

const burger = mobile.getByRole("button", { name: "Open navigation" });
if (!(await burger.isVisible())) problems.push("hamburger is not visible at 375px");
await burger.click();
await mobile.waitForTimeout(400);
const sheetNav = await mobile.getByRole("navigation", { name: "Main" }).isVisible();
console.log(`  375px sheet opens           : ${sheetNav}`);
if (!sheetNav) problems.push("the navigation Sheet did not open at 375px");
await mobile.screenshot({ path: `${OUT}/p3.3-shell-375-sheet.png` });

// closes on navigate
await mobile.getByRole("link", { name: "Employees" }).click().catch(() => {});
await mobile.waitForTimeout(500);

await browser.close();

if (problems.length) {
  console.error(`✗ shell verification (${problems.length}):`);
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log("✓ collapse persists across reload in both directions; 375px has no horizontal scroll and uses the Sheet");
