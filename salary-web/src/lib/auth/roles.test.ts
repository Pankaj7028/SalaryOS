import { describe, expect, it } from "vitest";
import {
  AREA_ACCESS,
  NAV_VISIBILITY,
  ROLES,
  type Area,
  type Role,
  canAccess,
  canApproveChanges,
  canProposeChanges,
  canSee,
} from "./roles";
import { NAV_GROUPS } from "@/lib/nav";

const areas = Object.keys(AREA_ACCESS) as Area[];

describe("nav visibility never exceeds access", () => {
  /**
   * The invariant from CLAUDE.md §7: "Visibility may be narrower than access,
   * never wider." A nav entry the role cannot actually reach is a link straight
   * into a 403 — and worse, it advertises that the area exists.
   */
  it.each(areas)("%s is not visible to any role that cannot access it", (area) => {
    const access = new Set<Role>(AREA_ACCESS[area]);
    for (const role of NAV_VISIBILITY[area]) {
      expect(access.has(role), `${role} sees ${area} in the nav but cannot access it`).toBe(true);
    }
  });

  it("holds for every role/area pair", () => {
    for (const role of ROLES) {
      for (const area of areas) {
        if (canSee(role, area)) expect(canAccess(role, area)).toBe(true);
      }
    }
  });
});

describe("the tables stay in step with the sidebar", () => {
  it("every nav item points at a declared area", () => {
    const hrefs = NAV_GROUPS.flatMap((g) => g.items.map((i) => i.href));
    for (const href of hrefs) {
      expect(areas, `${href} is in the sidebar but absent from AREA_ACCESS`).toContain(href);
    }
  });

  it("every declared area appears in the sidebar", () => {
    const hrefs = new Set(NAV_GROUPS.flatMap((g) => g.items.map((i) => i.href)));
    for (const area of areas) {
      expect(hrefs.has(area), `${area} is declared but unreachable from the sidebar`).toBe(true);
    }
  });

  it("both tables cover exactly the same areas", () => {
    expect(Object.keys(NAV_VISIBILITY).sort()).toEqual(areas.slice().sort());
  });
});

describe("mirrors the RBAC table in CLAUDE.md §7", () => {
  /**
   * Written out longhand from the doc rather than derived from the code, so that
   * editing roles.ts to match a mistake cannot make this pass.
   */
  it("HR_ADMIN alone manages users and imports", () => {
    expect(AREA_ACCESS["/admin/users"]).toEqual(["HR_ADMIN"]);
    expect(AREA_ACCESS["/admin/import"]).toEqual(["HR_ADMIN"]);
  });

  it("the audit log is HR_ADMIN and AUDITOR only — HR_MANAGER is deliberately excluded", () => {
    expect(canAccess("HR_ADMIN", "/admin/audit")).toBe(true);
    expect(canAccess("AUDITOR", "/admin/audit")).toBe(true);
    expect(canAccess("HR_MANAGER", "/admin/audit")).toBe(false);
    expect(canAccess("COMP_ANALYST", "/admin/audit")).toBe(false);
  });

  it("every role may view employees and their pay", () => {
    for (const role of ROLES) expect(canAccess(role, "/employees")).toBe(true);
  });

  it("AUDITOR runs no insights and manages no bands", () => {
    expect(canAccess("AUDITOR", "/insights/pay")).toBe(false);
    expect(canAccess("AUDITOR", "/insights/equity")).toBe(false);
    expect(canAccess("AUDITOR", "/bands")).toBe(false);
  });

  it("COMP_ANALYST proposes changes but manages no bands", () => {
    expect(canAccess("COMP_ANALYST", "/changes")).toBe(true);
    expect(canAccess("COMP_ANALYST", "/bands")).toBe(false);
  });

  it("only HR_ADMIN and HR_MANAGER may approve or reject a change — COMP_ANALYST can propose but not decide", () => {
    expect(canApproveChanges("HR_ADMIN")).toBe(true);
    expect(canApproveChanges("HR_MANAGER")).toBe(true);
    expect(canApproveChanges("COMP_ANALYST")).toBe(false);
    expect(canApproveChanges("AUDITOR")).toBe(false);
  });

  it("proposing is one role wider than approving, and AUDITOR proposes nothing", () => {
    expect(canProposeChanges("HR_ADMIN")).toBe(true);
    expect(canProposeChanges("HR_MANAGER")).toBe(true);
    expect(canProposeChanges("COMP_ANALYST")).toBe(true);
    expect(canProposeChanges("AUDITOR")).toBe(false);
  });

  it("every approver may also propose — the narrower right implies the wider one", () => {
    for (const role of ROLES) {
      if (canApproveChanges(role)) {
        expect(canProposeChanges(role)).toBe(true);
      }
    }
  });

  it("there is no role hierarchy — HR_ADMIN is listed explicitly everywhere it is allowed", () => {
    // If someone introduces a hierarchy, the natural shortcut is to drop HR_ADMIN
    // from the narrow entries and let it inherit. This catches that.
    const adminAreas = areas.filter((a) => canAccess("HR_ADMIN", a));
    expect(adminAreas.sort()).toEqual(areas.slice().sort());
  });
});
