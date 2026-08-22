/**
 * 375px nav check, authenticated. Written during P8's QA pass after finding the topbar
 * overflowed the viewport by ~12px on every page (the Brand wordmark plus the fixed-width
 * currency toggle didn't leave room for the search/theme/avatar cluster) — `verify-shell.mjs`'s
 * own 375px check never caught it because it runs unauthenticated and never reaches the topbar
 * at all. This one logs in first.
 */
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";

const BASE = process.env.BASE_URL ?? "http://localhost:3000";
const OUT = "screenshots";
mkdirSync(OUT, { recursive: true });
const EMAIL = process.env.QA_HR_ADMIN_EMAIL ?? "admin@acme.test";
const PASSWORD = process.env.QA_PASSWORD ?? "Password123!";

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 375, height: 812 } });
const page = await ctx.newPage();
const problems = [];

await page.goto(`${BASE}/sign-in`, { waitUntil: "load" });
await page.getByLabel("Email").fill(EMAIL);
await page.getByLabel("Password").fill(PASSWORD);
await page.getByRole("button", { name: /sign in/i }).click();
await page.waitForURL(`${BASE}/`, { timeout: 10000 });
await page.waitForTimeout(500);

const scroll = await page.evaluate(() => ({ doc: document.documentElement.scrollWidth, win: window.innerWidth }));
console.log(`  Overview scrollWidth        : ${scroll.doc}px vs viewport ${scroll.win}px`);
if (scroll.doc > scroll.win) problems.push(`horizontal scroll on Overview at 375px: ${scroll.doc}px > ${scroll.win}px`);

const burger = page.getByRole("button", { name: "Open navigation" });
if (!(await burger.isVisible())) problems.push("hamburger is not visible at 375px");
await burger.click();
await page.waitForTimeout(400);
const navCount = await page.getByRole("navigation", { name: "Main" }).locator("a").count();
console.log(`  mobile sheet nav item count : ${navCount}`);
if (navCount < 13) problems.push(`expected at least 13 nav items in the mobile sheet, found ${navCount}`);
await page.screenshot({ path: `${OUT}/p8-qa-mobile-nav.png` });

await page.getByRole("link", { name: "Audit log" }).click();
await page.waitForTimeout(1000);
const h1 = await page.locator("h1").first().innerText().catch(() => "(none)");
console.log(`  navigated via sheet to      : "${h1}"`);
if (h1 !== "Audit log") problems.push(`clicking "Audit log" in the sheet landed on "${h1}", not the audit screen`);
const scroll2 = await page.evaluate(() => ({ doc: document.documentElement.scrollWidth, win: window.innerWidth }));
console.log(`  Audit log scrollWidth       : ${scroll2.doc}px vs viewport ${scroll2.win}px`);
if (scroll2.doc > scroll2.win) problems.push(`horizontal scroll on Audit log at 375px: ${scroll2.doc}px > ${scroll2.win}px`);
await page.screenshot({ path: `${OUT}/p8-qa-mobile-audit.png` });

await browser.close();

if (problems.length) {
  console.error(`\n✗ mobile nav verification (${problems.length}):`);
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log("\n✓ no horizontal scroll at 375px, sheet nav has every item and actually navigates");
