import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Guards the two toast rules from CLAUDE.md §9 / ui doc §7.7. Both fail SILENTLY
 * in the browser, which is why they are asserted here rather than trusted:
 *   - a second <Toaster> shadows the root one; no toast appears and nothing errors
 *   - a direct toast() call bypasses the fixed dwell times, and the drift is
 *     invisible until the toasts are noticeably inconsistent
 */
const SRC = join(import.meta.dirname, "..");

/**
 * Application source only. Test files are excluded deliberately: this file
 * contains the very literals it scans for, and would otherwise report itself.
 */
function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) return sourceFiles(full);
    if (/\.test\.tsx?$/.test(entry)) return [];
    return /\.tsx?$/.test(entry) ? [full] : [];
  });
}

/**
 * Comments are stripped before matching. These rules are about what the code
 * DOES, and several of these files legitimately discuss <Toaster> and toast()
 * in prose — matching those would make the guard unusable.
 */
const stripComments = (src: string) =>
  src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");

const files = sourceFiles(SRC).map((f) => ({
  path: relative(SRC, f),
  text: stripComments(readFileSync(f, "utf8")),
}));

describe("there is exactly one toast host", () => {
  it("only the root layout renders <Toaster>", () => {
    const rendering = files.filter((f) => /<Toaster[\s/>]/.test(f.text)).map((f) => f.path);
    expect(rendering).toEqual(["app/layout.tsx"]);
  });

  it("only ui/sonner.tsx defines one", () => {
    const defining = files
      .filter((f) => /export\s*\{\s*Toaster|export function Toaster|const Toaster =/.test(f.text))
      .map((f) => f.path);
    expect(defining).toEqual(["components/ui/sonner.tsx"]);
  });
});

describe("toasts go through notify.ts", () => {
  /** Only notify.ts may import sonner's toast; ui/sonner.tsx imports the Toaster. */
  const ALLOWED = new Set(["lib/notify.ts", "components/ui/sonner.tsx"]);

  it("no feature imports sonner directly", () => {
    const offenders = files
      .filter((f) => !ALLOWED.has(f.path))
      .filter((f) => /from ["']sonner["']/.test(f.text))
      .map((f) => f.path);
    expect(
      offenders,
      `these must import from @/lib/notify instead: ${offenders.join(", ")}`,
    ).toEqual([]);
  });

  it("no feature calls toast() directly", () => {
    const offenders = files
      .filter((f) => !ALLOWED.has(f.path))
      .filter((f) => /\btoast\s*(\.\w+)?\s*\(/.test(f.text))
      .map((f) => f.path);
    expect(offenders).toEqual([]);
  });
});

describe("notify exposes the documented surface", () => {
  it("has success, info, warning, failure and bulk", async () => {
    const notify = await import("./notify");
    for (const fn of ["success", "info", "warning", "failure", "bulk"]) {
      expect(typeof notify[fn as keyof typeof notify]).toBe("function");
    }
  });
});
