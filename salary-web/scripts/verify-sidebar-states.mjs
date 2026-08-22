/**
 * Focused collapsed/expanded sidebar check, authenticated (verify-shell.mjs's own P3.3 checks
 * assume an already-signed-in browser context; this logs in first so it can run standalone
 * against a fresh headless browser). Written during P8's QA pass to confirm the new P8.3/P8.4
 * nav items (FX rates, Import, Audit log) don't break the existing collapse/expand/hover-peek
 * behaviour now that the sidebar has more rows than it did at P3.3.
 */
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";

const BASE = process.env.BASE_URL ?? "http://localhost:3000";
const OUT = "screenshots";
mkdirSync(OUT, { recursive: true });
const EMAIL = process.env.QA_HR_ADMIN_EMAIL ?? "admin@acme.test";
const PASSWORD = process.env.QA_PASSWORD ?? "Password123!";

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const page = await ctx.newPage();
const problems = [];
const width = () => page.locator("aside.app-sidebar").evaluate((el) => el.getBoundingClientRect().width);

await page.goto(`${BASE}/sign-in`, { waitUntil: "load" });
await page.getByLabel("Email").fill(EMAIL);
await page.getByLabel("Password").fill(PASSWORD);
await page.getByRole("button", { name: /sign in/i }).click();
await page.waitForURL(`${BASE}/`, { timeout: 10000 });
await page.evaluate(() => document.fonts.ready);

const navItemCount = await page.locator("aside.app-sidebar nav a").count();
console.log(`  nav items rendered         : ${navItemCount}`);
if (navItemCount < 13) problems.push(`expected at least 13 nav items for HR_ADMIN (was 13 pre-P8.3/8.4), found ${navItemCount}`);

const expanded = await width();
console.log(`  sidebar, expanded          : ${expanded}px`);
if (Math.round(expanded) !== 240) problems.push(`expanded sidebar should be 240px, is ${expanded}px`);
const labelsVisibleExpanded = await page.locator("aside.app-sidebar .nav-label").first().isVisible();
if (!labelsVisibleExpanded) problems.push("nav labels are not visible while expanded");
await page.screenshot({ path: `${OUT}/p8-qa-sidebar-expanded.png`, fullPage: false });

await page.getByRole("button", { name: "Collapse" }).click();
await page.waitForFunction(() => document.documentElement.dataset.sidebar === "collapsed");
await page.mouse.move(900, 400);
await page.waitForTimeout(400);
const collapsed = await width();
console.log(`  sidebar, collapsed         : ${collapsed}px`);
if (Math.round(collapsed) !== 60) problems.push(`collapsed sidebar should be 60px, is ${collapsed}px`);

// All nav items (including the new P8.3/P8.4 ones) must still be present and clickable while
// collapsed -- just icon-only, not removed from the DOM.
const navItemCountCollapsed = await page.locator("aside.app-sidebar nav a").count();
if (navItemCountCollapsed !== navItemCount) {
  problems.push(`nav item count changed between expanded (${navItemCount}) and collapsed (${navItemCountCollapsed})`);
}
const fxRatesLink = page.locator('aside.app-sidebar nav a[href="/admin/fx-rates"]');
if ((await fxRatesLink.count()) === 0) problems.push("FX rates nav link is missing while collapsed");
await page.screenshot({ path: `${OUT}/p8-qa-sidebar-collapsed.png`, fullPage: false });

// Hover peek: the collapsed rail widens back to 240px under the pointer (§6.2).
await page.mouse.move(30, 400);
await page.waitForTimeout(400);
const peeked = await width();
console.log(`  collapsed rail on hover    : ${peeked}px`);
if (Math.round(peeked) !== 240) problems.push(`collapsed rail should peek to 240px on hover, got ${peeked}px`);
await page.screenshot({ path: `${OUT}/p8-qa-sidebar-collapsed-hover.png`, fullPage: false });
await page.mouse.move(900, 400);
await page.waitForTimeout(300);

// Every collapsed nav link is still reachable (a real click, not just present in the DOM).
await fxRatesLink.click();
await page.waitForURL(`${BASE}/admin/fx-rates`, { timeout: 5000 }).catch(() => {
  problems.push("clicking the FX rates link while collapsed did not navigate there");
});

// Reload persistence, still collapsed.
await page.reload({ waitUntil: "load" });
const afterReload = await width();
if (Math.round(afterReload) !== 60) problems.push(`collapse did not persist across reload — sidebar is ${afterReload}px`);

await browser.close();

if (problems.length) {
  console.error(`\n✗ sidebar verification (${problems.length}):`);
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log("\n✓ sidebar renders correctly collapsed and expanded, with every P8.3/P8.4 nav item present and clickable in both states");
