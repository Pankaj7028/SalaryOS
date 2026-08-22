/**
 * P9.5: keyboard-only pass through the core flow, and the 375px pass — the two checks in
 * ui.md §12's checklist (items 9 and 10) that are behaviour, not markup, so they need a real
 * browser rather than a static scan. Contrast (item 6) is covered separately by `npm run
 * check:tokens`, which is pure colour math and needs no browser at all.
 *
 * Needs the backend running and seeded (P9.1) and the frontend served at BASE_URL. Uses the
 * seeded HR_ADMIN account by default — override via QA_* env vars if your local seed differs.
 */
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";

const BASE = process.env.BASE_URL ?? "http://localhost:3000";
const OUT = "screenshots";
mkdirSync(OUT, { recursive: true });

const EMAIL = process.env.QA_EMAIL ?? "admin@acme.test";
const PASSWORD = process.env.QA_PASSWORD;
const EMPLOYEE_ID = process.env.EMPLOYEE_ID;
if (!PASSWORD) {
  console.error("Set QA_PASSWORD to the seeded admin@acme.test password (printed once by SeedRunner).");
  process.exit(1);
}
if (!EMPLOYEE_ID) {
  console.error("Set EMPLOYEE_ID to a real employee id with current pay (see employee_current_comp).");
  process.exit(1);
}

const problems = [];

/** No visible focus ring (outline or box-shadow-based ring) is a real failure mode here —
 * `outline: none` with nothing standing in for it is exactly what ui.md §10 forbids. */
async function hasVisibleFocusIndicator(page) {
  return page.evaluate(() => {
    const el = document.activeElement;
    if (!el || el === document.body) return false;
    const s = getComputedStyle(el);
    const hasOutline = s.outlineStyle !== "none" && parseFloat(s.outlineWidth) > 0;
    const hasRingShadow = s.boxShadow && s.boxShadow !== "none";
    return hasOutline || hasRingShadow;
  });
}

async function focusedDescription(page) {
  return page.evaluate(() => {
    const el = document.activeElement;
    if (!el) return "(nothing)";
    return `${el.tagName.toLowerCase()}${el.id ? "#" + el.id : ""} "${(el.textContent || el.getAttribute("aria-label") || el.getAttribute("placeholder") || "").trim().slice(0, 40)}"`;
  });
}

// ---------------------------------------------------------------------------
// Part 1: keyboard-only pass through the core flow (ui.md §12.9)
// ---------------------------------------------------------------------------
async function keyboardPass() {
  console.log("\n=== keyboard-only pass ===");
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();

  // Sign in using only the keyboard.
  await page.goto(`${BASE}/sign-in`, { waitUntil: "load" });
  await page.keyboard.press("Tab"); // first focusable — should land in/near the email field
  await page.getByLabel("Email").focus();
  if (!(await hasVisibleFocusIndicator(page))) problems.push("no visible focus ring on the email field");
  await page.keyboard.type(EMAIL);
  await page.keyboard.press("Tab");
  console.log(`  focused after email->Tab: ${await focusedDescription(page)}`);
  await page.keyboard.type(PASSWORD);
  await page.keyboard.press("Enter"); // submit via keyboard, not a click
  await page.waitForURL(`${BASE}/`, { timeout: 10000 }).catch(() => {});
  if (page.url() !== `${BASE}/`) {
    problems.push(`keyboard sign-in did not land on the dashboard (still on ${page.url()})`);
    await browser.close();
    return;
  }
  console.log("  signed in via keyboard only");

  // Reach the Employees nav item by Tab and activate with Enter — no mouse.
  await page.goto(`${BASE}/`, { waitUntil: "load" });
  let reachedEmployeesNav = false;
  for (let i = 0; i < 40; i++) {
    await page.keyboard.press("Tab");
    // Specifically the nav link (an <a href="/employees"> reading exactly "Employees") — a naive
    // text-contains match also caught the unrelated "Search employees ⌘K" button.
    const isNavLink = await page.evaluate(() => {
      const el = document.activeElement;
      return !!el && el.tagName === "A" && el.getAttribute("href") === "/employees" && el.textContent.trim() === "Employees";
    });
    if (isNavLink) {
      reachedEmployeesNav = true;
      console.log(`  reached Employees nav link after ${i + 1} tabs`);
      if (!(await hasVisibleFocusIndicator(page))) problems.push("no visible focus ring on the Employees nav link");
      break;
    }
  }
  if (!reachedEmployeesNav) {
    problems.push("could not Tab to the Employees nav link within 40 tabs from the dashboard");
  }
  else {
    await page.keyboard.press("Enter");
    await page.waitForURL(`${BASE}/employees`, { timeout: 10000 }).catch(() => {});
    if (!page.url().startsWith(`${BASE}/employees`)) problems.push("Enter on the Employees nav link did not navigate there");
    else console.log("  navigated to /employees via Enter");
  }

  // Employee detail: open directly (nav-crawling every row by keyboard is its own test), then
  // drive "Propose change" and its dialog by keyboard alone — this is the part of the flow ui.md
  // §10 specifically calls out ("dialogs trap focus and restore it on close").
  await page.goto(`${BASE}/employees/${EMPLOYEE_ID}`, { waitUntil: "load" });
  const proposeButton = page.getByRole("button", { name: "Propose change" });
  await proposeButton.waitFor({ state: "visible", timeout: 10000 });
  if (await proposeButton.isDisabled()) {
    problems.push(`Propose change is disabled for employee ${EMPLOYEE_ID} — pick one with current pay`);
  }
  else {
    await proposeButton.focus();
    if (!(await hasVisibleFocusIndicator(page))) problems.push("no visible focus ring on the Propose change button");
    await page.keyboard.press("Enter");
    const dialog = page.getByRole("dialog");
    await dialog.waitFor({ state: "visible", timeout: 5000 });
    const focusInsideDialog = await page.evaluate(() => {
      const dlg = document.querySelector('[role="dialog"]');
      return dlg ? dlg.contains(document.activeElement) : false;
    });
    console.log(`  dialog opened via Enter; focus moved inside: ${focusInsideDialog}`);
    if (!focusInsideDialog) problems.push("opening the Propose change dialog did not move focus inside it");

    // Tab several times and confirm focus never escapes the dialog (a real focus trap, not just
    // an initial focus move).
    let trapped = true;
    for (let i = 0; i < 8; i++) {
      await page.keyboard.press("Tab");
      const inside = await page.evaluate(() => {
        const dlg = document.querySelector('[role="dialog"]');
        return dlg ? dlg.contains(document.activeElement) : false;
      });
      if (!inside) {
        trapped = false;
        break;
      }
    }
    if (!trapped) problems.push("focus escaped the Propose change dialog while tabbing through its controls");
    else console.log("  focus stayed trapped inside the dialog across 8 tabs");

    await page.keyboard.press("Escape");
    await dialog.waitFor({ state: "hidden", timeout: 5000 }).catch(() => {});
    const focusReturned = await page.evaluate(() => {
      const active = document.activeElement;
      return active && active.textContent && active.textContent.includes("Propose change");
    });
    console.log(`  Escape closed the dialog; focus returned to trigger: ${focusReturned}`);
    if (!focusReturned) problems.push("focus did not return to the Propose change button after closing the dialog with Escape");
  }

  await page.screenshot({ path: `${OUT}/p9.5-keyboard-final.png`, fullPage: false });
  await browser.close();
}

// ---------------------------------------------------------------------------
// Part 2: 375px pass (ui.md §12.10) — no horizontal scroll, table degrades to cards.
// ---------------------------------------------------------------------------
async function mobilePass() {
  console.log("\n=== 375px pass ===");
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 375, height: 812 } });
  const page = await ctx.newPage();

  await page.goto(`${BASE}/sign-in`, { waitUntil: "load" });
  await page.getByLabel("Email").fill(EMAIL);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: /sign in/i }).click();
  await page.waitForURL(`${BASE}/`, { timeout: 10000 }).catch(() => {});

  const routes = ["/", "/employees", `/employees/${EMPLOYEE_ID}`, "/bands", "/insights/pay"];
  for (const route of routes) {
    await page.goto(`${BASE}${route}`, { waitUntil: "load" });
    await page.waitForTimeout(200);
    const overflow = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }));
    const overflows = overflow.scrollWidth > overflow.clientWidth + 1; // +1px rounding slack
    console.log(`  ${route.padEnd(28)} scrollWidth=${overflow.scrollWidth} clientWidth=${overflow.clientWidth} ${overflows ? "OVERFLOWS" : "ok"}`);
    if (overflows) problems.push(`${route} has horizontal overflow at 375px (${overflow.scrollWidth}px content in a ${overflow.clientWidth}px viewport)`);
    const shot = route === "/" ? "dashboard" : route.replace(/\//g, "_");
    await page.screenshot({ path: `${OUT}/p9.5-375px${shot}.png`, fullPage: true });
  }

  // The employees list specifically must degrade table -> cards under md (ui.md §12.10).
  await page.goto(`${BASE}/employees`, { waitUntil: "load" });
  await page.locator('ul[aria-label="Employees"]').first().waitFor({ state: "attached", timeout: 10000 }).catch(() => {});
  const tableVisible = await page.locator("table").first().isVisible().catch(() => false);
  const cardsVisible = await page.locator('ul[aria-label="Employees"]').first().isVisible().catch(() => false);
  console.log(`  /employees at 375px: table visible=${tableVisible}, card list visible=${cardsVisible}`);
  if (tableVisible) problems.push("the desktop table is visible at 375px instead of the card layout");
  if (!cardsVisible) problems.push("the card layout is not visible at 375px on /employees");

  await browser.close();
}

await keyboardPass();
await mobilePass();

console.log("\n" + "=".repeat(60));
if (problems.length) {
  console.log(`✗ ${problems.length} problem(s):`);
  for (const p of problems) console.log(`  - ${p}`);
  process.exit(1);
}
console.log("✓ keyboard-only pass and 375px pass both clean");
