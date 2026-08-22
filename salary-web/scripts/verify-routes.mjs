/**
 * Route + role crawl — every nav-reachable page, for every role, actually navigated to in a real
 * browser and checked for a client-side crash or a 404. Written during P8's QA pass after a
 * `.toFixed()` crash and four missing nav targets shipped past `npm run build` (a route only
 * fails a Next.js build if NO page exists for a nav href at all; a page that exists but throws at
 * runtime, or one that's simply missing while its sibling routes are fine, both build clean).
 *
 * Needs one seeded user per role — set via env vars if the defaults below don't exist in your
 * database (`QA_HR_ADMIN_EMAIL` etc.). All share `QA_PASSWORD` (default `Password123!`) unless
 * overridden per role (`QA_HR_ADMIN_PASSWORD` etc.) — SeedRunner-generated accounts each have
 * their own password, not one shared one.
 */
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";

const BASE = process.env.BASE_URL ?? "http://localhost:3000";
const OUT = "screenshots";
mkdirSync(OUT, { recursive: true });

const EMPLOYEE_ID = process.env.EMPLOYEE_ID;
if (!EMPLOYEE_ID) {
  console.error("Set EMPLOYEE_ID to a real employee id (GET /api/employees) before running this.");
  process.exit(1);
}

// Mirrors lib/auth/roles.ts AREA_ACCESS — keep in sync by hand; there's no way to import a .ts
// file into a plain node script here.
const ROUTE_ROLES = {
  "/": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST", "AUDITOR"],
  "/employees": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST", "AUDITOR"],
  [`/employees/${EMPLOYEE_ID}`]: ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST", "AUDITOR"],
  "/changes": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST"],
  "/bands": ["HR_ADMIN", "HR_MANAGER"],
  "/levels": ["HR_ADMIN", "HR_MANAGER"],
  "/locations": ["HR_ADMIN", "HR_MANAGER"],
  "/insights/pay": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST"],
  "/insights/equity": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST"],
  "/insights/reports": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST"],
  "/admin/users": ["HR_ADMIN"],
  "/admin/import": ["HR_ADMIN"],
  "/admin/fx-rates": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST", "AUDITOR"],
  "/admin/audit": ["HR_ADMIN", "AUDITOR"],
};

// Each seeded account can have its own password (SeedRunner generates one per user, not one
// shared password) -- QA_<ROLE>_PASSWORD overrides QA_PASSWORD per role when they differ.
const PASSWORD = process.env.QA_PASSWORD ?? "Password123!";
const USERS = {
  HR_ADMIN: process.env.QA_HR_ADMIN_EMAIL ?? "admin@acme.test",
  HR_MANAGER: process.env.QA_HR_MANAGER_EMAIL ?? "qa.manager@acme.test",
  COMP_ANALYST: process.env.QA_COMP_ANALYST_EMAIL ?? "qa.analyst@acme.test",
  AUDITOR: process.env.QA_AUDITOR_EMAIL ?? "qa.auditor@acme.test",
};
const PASSWORDS = {
  HR_ADMIN: process.env.QA_HR_ADMIN_PASSWORD ?? PASSWORD,
  HR_MANAGER: process.env.QA_HR_MANAGER_PASSWORD ?? PASSWORD,
  COMP_ANALYST: process.env.QA_COMP_ANALYST_PASSWORD ?? PASSWORD,
  AUDITOR: process.env.QA_AUDITOR_PASSWORD ?? PASSWORD,
};

async function login(page, email, password) {
  await page.goto(`${BASE}/sign-in`, { waitUntil: "load" });
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: /sign in/i }).click();
  await page.waitForURL(`${BASE}/`, { timeout: 10000 }).catch(() => {});
  if (page.url() !== `${BASE}/`) {
    throw new Error(`login failed for ${email} — still on ${page.url()}`);
  }
}

function isCrash(bodyText) {
  return (
    bodyText.includes("This page couldn") || // Next.js client-side error boundary: "couldn't load"
    bodyText.includes("Application error") ||
    (bodyText.includes("404") && bodyText.length < 300)
  );
}

async function crawlRole(role) {
  const email = USERS[role];
  console.log(`\n=== ${role} (${email}) ===`);
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();
  const consoleErrors = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") consoleErrors.push(msg.text());
  });
  page.on("pageerror", (err) => consoleErrors.push("pageerror: " + err.message));

  await login(page, email, PASSWORDS[role]);
  const problems = [];

  for (const [route, allowedRoles] of Object.entries(ROUTE_ROLES)) {
    const shouldAllow = allowedRoles.includes(role);
    consoleErrors.length = 0;
    let response;
    try {
      response = await page.goto(`${BASE}${route}`, { waitUntil: "load", timeout: 15000 });
    } catch (e) {
      problems.push(`${role} ${route}: navigation failed — ${e.message}`);
      continue;
    }
    await page.waitForTimeout(400); // let any post-load client error surface
    const status = response ? response.status() : "?";
    const bodyText = await page.locator("body").innerText().catch(() => "");
    const crashed = isCrash(bodyText);
    const h1 = await page.locator("h1").first().innerText().catch(() => "(no h1)");
    // pageerrors (uncaught exceptions) are real regardless of role; a bare 403/404 resource fetch
    // is expected noise on a route this role can't access, so only pageerrors fail a gated route.
    const realConsoleErrors = consoleErrors.filter((e) => e.startsWith("pageerror:"));

    if (shouldAllow) {
      const flag = crashed || status >= 400 || realConsoleErrors.length > 0;
      console.log(`  ${flag ? "✗" : "✓"} ${route.padEnd(30)} status=${status} h1="${h1}"`);
      if (flag) {
        problems.push(`${role} ${route}: expected OK, got status=${status} crashed=${crashed} h1="${h1}"`);
        if (realConsoleErrors.length) problems.push(`${role} ${route}: ${realConsoleErrors.join(" | ")}`);
      }
    } else {
      // Not in this role's access -- direct navigation should degrade gracefully (a 403 the
      // screen turns into an error state), never a hard client-side crash.
      console.log(`  ${crashed ? "✗" : "·"} ${route.padEnd(30)} (not in role's access) status=${status} h1="${h1}"`);
      if (crashed || realConsoleErrors.length) {
        problems.push(`${role} ${route}: role-gated route crashed instead of degrading gracefully — h1="${h1}" ${realConsoleErrors.join(" | ")}`);
      }
    }
  }

  await page.screenshot({ path: `${OUT}/p8-qa-routes-${role}.png`, fullPage: true });
  await browser.close();
  return problems;
}

const allProblems = [];
for (const role of Object.keys(USERS)) {
  try {
    allProblems.push(...(await crawlRole(role)));
  } catch (e) {
    allProblems.push(`${role}: ${e.message}`);
  }
}

if (allProblems.length) {
  console.error(`\n✗ ${allProblems.length} problem(s):`);
  for (const p of allProblems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log("\n✓ every nav-reachable route renders for every role that can reach it, and every role-gated route degrades gracefully for the roles that can't");
