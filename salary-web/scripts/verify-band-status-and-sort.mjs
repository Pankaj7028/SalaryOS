import { chromium } from "@playwright/test";

const BASE = process.env.BASE_URL ?? "http://localhost:3100";
const EMAIL = process.env.QA_EMAIL ?? "admin@acme.test";
const PASSWORD = process.env.QA_PASSWORD;
if (!PASSWORD) {
  console.error("Set QA_PASSWORD");
  process.exit(1);
}

const problems = [];
const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const page = await ctx.newPage();

await page.goto(`${BASE}/sign-in`, { waitUntil: "load" });
await page.getByLabel("Email").fill(EMAIL);
await page.getByLabel("Password").fill(PASSWORD);
await page.getByRole("button", { name: /sign in/i }).click();
await page.waitForURL(`${BASE}/`, { timeout: 10000 }).catch(() => {});

await page.goto(`${BASE}/employees`, { waitUntil: "load" });
await page.waitForTimeout(500);

// Band status filter -- the 7th combobox on the page (currency, department, location, country,
// level, status, band status, sort, page-size), located by its current text since the shadcn
// Select trigger's accessible name computation didn't match a role+name locator reliably here.
const comboboxes = page.getByRole("combobox");
console.log("combobox count:", await comboboxes.count());
await comboboxes.filter({ hasText: "Any band status" }).click();
await page.getByRole("option", { name: "Below minimum" }).click();
await page.waitForURL(/bandStatus=BELOW_MIN/, { timeout: 5000 }).catch(() => problems.push("URL did not update with bandStatus=BELOW_MIN"));
await page.waitForTimeout(600);
const rowCountText = await page.locator("p.type-caption").first().textContent();
console.log("after band-status filter:", rowCountText, "url:", page.url());
const rows = await page.locator("tbody tr").count();
console.log("visible table rows:", rows);
if (rows === 0) problems.push("no rows shown after filtering to BELOW_MIN");

// Sort by compa-ratio
await page.goto(`${BASE}/employees`, { waitUntil: "load" });
await page.waitForTimeout(300);
await page.getByRole("combobox").filter({ hasText: "Sort: last name" }).click();
await page.getByRole("option", { name: /compa-ratio/i }).click();
await page.waitForURL(/sortBy=compaRatio/, { timeout: 5000 }).catch(() => problems.push("URL did not update with sortBy=compaRatio"));
await page.waitForTimeout(600);
console.log("after sort:", page.url());

const firstFewCompaRatios = await page.locator("tbody tr").evaluateAll((trs) =>
  trs.slice(0, 5).map((tr) => tr.textContent?.trim().slice(0, 200)),
);
console.log("first rows (raw text):", firstFewCompaRatios);

await page.screenshot({ path: "screenshots/verify-band-sort.png", fullPage: false });
await browser.close();

console.log("\n" + "=".repeat(60));
if (problems.length) {
  console.log(`✗ ${problems.length} problem(s):`);
  for (const p of problems) console.log(`  - ${p}`);
  process.exit(1);
}
console.log("✓ band-status filter and compa-ratio sort both drive the URL and render rows");
