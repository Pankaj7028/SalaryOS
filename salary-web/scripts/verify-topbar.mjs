/**
 * Verification for build step P3.4 — the topbar controls.
 *
 * The step's Verify ("server-rendered HTML already carries .app-dark when the
 * cookie says so") is proven by curl in the commit message — no JS at all, which
 * is the strongest form. This covers the behaviour around it.
 */
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";

const BASE = process.env.BASE_URL ?? "http://localhost:3100";
const OUT = "screenshots";
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch();
const problems = [];

// ---- explicit dark, straight from the cookie -------------------------------
const darkCtx = await browser.newContext({
  viewport: { width: 1280, height: 900 },
  deviceScaleFactor: 2,
});
await darkCtx.addCookies([
  { name: "sos.theme", value: "dark", url: BASE },
  { name: "sos.sidebar", value: "expanded", url: BASE },
]);
const dark = await darkCtx.newPage();
await dark.goto(BASE, { waitUntil: "load" });
await dark.evaluate(() => document.fonts.ready);
const darkCls = await dark.evaluate(() => document.documentElement.className);
console.log(`  cookie dark  → html class : ${/app-dark/.test(darkCls) ? "app-dark ✓" : darkCls}`);
if (!/app-dark/.test(darkCls)) problems.push("dark cookie did not yield app-dark");
const bg = await dark.evaluate(() => getComputedStyle(document.body).backgroundColor);
console.log(`  dark body background      : ${bg}`);
if (bg !== "rgb(9, 9, 11)") problems.push(`dark background should be #09090B, is ${bg}`);
await dark.screenshot({ path: `${OUT}/p3.4-topbar-dark.png` });

// ---- system follows the OS, with no explicit class from the server ---------
const sysCtx = await browser.newContext({
  viewport: { width: 1280, height: 900 },
  colorScheme: "dark",
  deviceScaleFactor: 2,
});
const sys = await sysCtx.newPage();
await sys.goto(BASE, { waitUntil: "load" });
const sysCls = await sys.evaluate(() => document.documentElement.className);
console.log(`  no cookie + OS dark       : ${/app-dark/.test(sysCls) ? "app-dark ✓ (pre-paint script)" : sysCls}`);
if (!/app-dark/.test(sysCls)) problems.push("system theme did not resolve to app-dark under an OS dark preference");

// ---- the menu switches and persists ---------------------------------------
const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const page = await ctx.newPage();
await page.goto(BASE, { waitUntil: "load" });
await page.getByRole("button", { name: /^Theme:/ }).click();
await page.getByRole("menuitem", { name: "Dark" }).click();
await page.waitForTimeout(200);
const afterPick = await page.evaluate(() => document.documentElement.className);
if (!/app-dark/.test(afterPick)) problems.push("picking Dark did not apply app-dark");
const cookie = (await ctx.cookies()).find((c) => c.name === "sos.theme");
console.log(`  picked Dark → cookie      : ${cookie?.value}`);
if (cookie?.value !== "dark") problems.push(`theme cookie should be "dark", is ${cookie?.value}`);
await page.reload({ waitUntil: "load" });
const afterReload = await page.evaluate(() => document.documentElement.className);
console.log(`  after reload              : ${/app-dark/.test(afterReload) ? "app-dark ✓" : afterReload}`);
if (!/app-dark/.test(afterReload)) problems.push("theme choice did not survive a reload");

// ---- currency toggle writes the URL ---------------------------------------
await page.getByRole("combobox", { name: "Currency" }).click();
await page.getByRole("option", { name: "As paid" }).click();
await page.waitForURL(/ccy=local/, { timeout: 5000 }).catch(() => {});
console.log(`  currency "As paid" → URL  : ${new URL(page.url()).search || "(none)"}`);
if (!/ccy=local/.test(page.url())) problems.push(`currency did not reach the URL: ${page.url()}`);
await page.reload({ waitUntil: "load" });
const restored = await page.getByRole("combobox", { name: "Currency" }).textContent();
console.log(`  after reload, toggle shows: ${restored?.trim()}`);
if (!/As paid/.test(restored ?? "")) problems.push("currency did not survive a reload from the URL");

// ---- ⌘K --------------------------------------------------------------------
await page.keyboard.press("ControlOrMeta+k");
await page.waitForTimeout(300);
const dialog = page.getByRole("dialog");
const opened = await dialog.isVisible();
console.log(`  ⌘K opens the palette      : ${opened}`);
if (!opened) problems.push("⌘K did not open the command palette");
await page.keyboard.press("Escape");

await browser.close();

if (problems.length) {
  console.error(`✗ topbar verification (${problems.length}):`);
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log("✓ theme from cookie server-side, system resolves pre-paint, currency lives in the URL, ⌘K opens");
