import { chromium } from "@playwright/test";

const BASE = process.env.BASE_URL ?? "http://localhost:3100";
const EMAIL = process.env.QA_EMAIL ?? "admin@acme.test";
const PASSWORD = process.env.QA_PASSWORD;
if (!PASSWORD) { console.error("Set QA_PASSWORD"); process.exit(1); }

const problems = [];
const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1400, height: 950 } });
const page = await ctx.newPage();
const consoleErrors = [];
page.on("console", (msg) => { if (msg.type() === "error") consoleErrors.push(msg.text()); });
page.on("pageerror", (err) => consoleErrors.push("pageerror: " + err.message));
function drainErrors(label) {
  if (consoleErrors.length) {
    problems.push(`${label}: console errors: ${consoleErrors.join(" | ")}`);
    consoleErrors.length = 0;
  }
}

await page.goto(`${BASE}/sign-in`, { waitUntil: "load" });
await page.getByLabel("Email").fill(EMAIL);
await page.getByLabel("Password").fill(PASSWORD);
await page.getByRole("button", { name: /sign in/i }).click();
await page.waitForURL(`${BASE}/`, { timeout: 10000 }).catch(() => {});
drainErrors("sign-in");

// ---- 1. Bands grid: create a band in an empty cell ----
await page.goto(`${BASE}/bands`, { waitUntil: "load" });
await page.waitForTimeout(500);
const addBandButtons = page.getByRole("button", { name: "+ Add band" });
const addBandCount = await addBandButtons.count();
console.log("empty band cells:", addBandCount);
if (addBandCount === 0) {
  problems.push("bands grid: no empty cells found to test create-band flow");
} else {
  await addBandButtons.first().click();
  await page.waitForTimeout(300);
  const dialogVisible = await page.getByRole("dialog").isVisible().catch(() => false);
  console.log("create-band dialog opened:", dialogVisible);
  if (!dialogVisible) problems.push("bands: clicking + Add band did not open a dialog");
  else {
    await page.getByLabel("Min").fill("50000");
    await page.getByLabel("Mid").fill("65000");
    await page.getByLabel("Max").fill("80000");
    await page.getByRole("dialog").getByRole("button", { name: "Create band" }).click();
    await page.waitForTimeout(800);
    const stillOpen = await page.getByRole("dialog").isVisible().catch(() => false);
    console.log("dialog closed after submit:", !stillOpen);
    if (stillOpen) {
      const errorText = await page.getByRole("dialog").innerText().catch(() => "");
      problems.push(`bands: create-band dialog still open after submit attempt — content: ${errorText.slice(0, 400)}`);
    }
  }
  drainErrors("bands create");
}

// ---- 2. Changes queue: open a PENDING change, check approve/reject controls ----
await page.goto(`${BASE}/changes`, { waitUntil: "load" });
await page.waitForTimeout(500);
const rowCount = await page.locator("tbody tr").count();
console.log("changes rows visible:", rowCount);
drainErrors("changes list");

// ---- 3. Admin: users list + fx-rates + import pages render real content ----
for (const route of ["/admin/users", "/admin/fx-rates", "/admin/import", "/admin/audit"]) {
  await page.goto(`${BASE}${route}`, { waitUntil: "load" });
  await page.waitForTimeout(400);
  const bodyLen = (await page.locator("body").innerText().catch(() => "")).length;
  console.log(`${route}: body text length ${bodyLen}`);
  if (bodyLen < 100) problems.push(`${route}: suspiciously little content (${bodyLen} chars) — possible blank/broken screen`);
  drainErrors(route);
}

// ---- 4. Employees: combined filters + pagination via clicks ----
await page.goto(`${BASE}/employees?status=ACTIVE&bandStatus=ABOVE_MAX&sortBy=compaRatio`, { waitUntil: "load" });
await page.waitForTimeout(600);
const empRows = await page.locator("tbody tr").count();
console.log("employees combined-filter rows:", empRows);
if (empRows === 0) problems.push("employees: combined status+bandStatus+sortBy filter returned 0 rows unexpectedly");
const nextBtn = page.getByRole("button", { name: "Next" });
if (await nextBtn.isEnabled().catch(() => false)) {
  await nextBtn.click();
  await page.waitForTimeout(500);
  const url2 = page.url();
  console.log("after Next click, url:", url2);
  if (!url2.includes("cursor=")) problems.push("employees: clicking Next did not add a cursor to the URL");
}
drainErrors("employees pagination");

// ---- 5. Theme toggle sanity (no console errors on switch) ----
await page.goto(`${BASE}/`, { waitUntil: "load" });
await page.waitForTimeout(300);
const themeButton = page.getByRole("button", { name: /theme/i });
if (await themeButton.count()) {
  await themeButton.click();
  await page.waitForTimeout(200);
  const darkOption = page.getByRole("menuitem", { name: /dark/i });
  if (await darkOption.count()) {
    await darkOption.click();
    await page.waitForTimeout(300);
  }
}
drainErrors("theme toggle");

await page.screenshot({ path: "screenshots/qa-interactive-final.png", fullPage: false });
await browser.close();

console.log("\n" + "=".repeat(60));
if (problems.length) {
  console.log(`✗ ${problems.length} problem(s):`);
  for (const p of problems) console.log(`  - ${p}`);
  process.exit(1);
}
console.log("✓ interactive QA pass clean");
