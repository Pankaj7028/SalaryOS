/**
 * The single source of truth for every role decision in salary-web.
 *
 * CLAUDE.md §10: no route, nav item or component may contain a role literal —
 * they all read this file. And §7: **hiding a nav item is not access control.**
 * The real boundary is the @PreAuthorize on the controller. What lives here only
 * decides what is worth showing; the server decides what is allowed.
 *
 * Roles are FLAT (§4.3). There is no hierarchy and HR_ADMIN is not implicitly
 * allowed everything — each role is listed where it is permitted, or it is not
 * permitted. Every entry below mirrors one row of the RBAC table in CLAUDE.md §7.
 */
export const ROLES = ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST", "AUDITOR"] as const;

export type Role = (typeof ROLES)[number];

/** Roles permitted to reach each area of the application. Mirrors CLAUDE.md §7. */
export const AREA_ACCESS = {
  // Landing page. Every role needs somewhere to arrive after signing in; the
  // cards it shows are themselves filtered by capability at P7.6.
  "/": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST", "AUDITOR"],

  // "View employees & their pay" — all four roles.
  "/employees": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST", "AUDITOR"],

  // "Propose a compensation change". Approving is narrower (HR_ADMIN, HR_MANAGER)
  // and is enforced per action, not by hiding the screen.
  "/changes": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST"],

  // "Manage salary bands & levels".
  "/bands": ["HR_ADMIN", "HR_MANAGER"],
  "/levels": ["HR_ADMIN", "HR_MANAGER"],
  "/locations": ["HR_ADMIN", "HR_MANAGER"],

  // "Run insights (aggregate)".
  "/insights/pay": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST"],
  "/insights/equity": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST"],
  "/insights/reports": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST"],

  // "Manage users & roles" — HR_ADMIN alone.
  "/admin/users": ["HR_ADMIN"],
  // "Import / bulk upload" — HR_ADMIN alone.
  "/admin/import": ["HR_ADMIN"],
  // "Read the audit log" — HR_ADMIN and AUDITOR. Note HR_MANAGER is absent, which
  // is the point of having no role hierarchy.
  "/admin/audit": ["HR_ADMIN", "AUDITOR"],
  // FX rates aren't a separate row in CLAUDE.md §7 — mapped the same way the backend
  // maps them (FxRateController's own comment): normalisation reference data, readable
  // by anyone who sees pay data, managed by HR Admin/HR Manager (`canManageFxRates` below).
  "/admin/fx-rates": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST", "AUDITOR"],
} as const satisfies Record<string, readonly Role[]>;

export type Area = keyof typeof AREA_ACCESS;

/**
 * Roles that see each nav item.
 *
 * Must be a SUBSET of AREA_ACCESS for the same area — visibility may be narrower
 * than access, never wider. roles.test.ts fails the build if that ever inverts.
 */
export const NAV_VISIBILITY = {
  "/": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST", "AUDITOR"],
  "/employees": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST", "AUDITOR"],
  "/changes": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST"],
  "/bands": ["HR_ADMIN", "HR_MANAGER"],
  "/levels": ["HR_ADMIN", "HR_MANAGER"],
  "/locations": ["HR_ADMIN", "HR_MANAGER"],
  "/insights/pay": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST"],
  "/insights/equity": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST"],
  "/insights/reports": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST"],
  "/admin/users": ["HR_ADMIN"],
  "/admin/import": ["HR_ADMIN"],
  "/admin/audit": ["HR_ADMIN", "AUDITOR"],
  "/admin/fx-rates": ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST", "AUDITOR"],
} as const satisfies Record<Area, readonly Role[]>;

export function canAccess(role: Role, area: Area): boolean {
  return (AREA_ACCESS[area] as readonly Role[]).includes(role);
}

export function canSee(role: Role, area: Area): boolean {
  return (NAV_VISIBILITY[area] as readonly Role[]).includes(role);
}

export function isArea(href: string): href is Area {
  return href in AREA_ACCESS;
}

/**
 * "Approve / reject a change" (CLAUDE.md §7) — narrower than `/changes` area access itself, which
 * also includes COMP_ANALYST (who may propose but not decide). ui doc §8.5: the tab a role cannot
 * act on stays visible — its action buttons are just absent, never disabled-with-a-tooltip — so
 * this gates the buttons only, not the screen or the row.
 */
const CHANGE_APPROVER_ROLES: readonly Role[] = ["HR_ADMIN", "HR_MANAGER"];

export function canApproveChanges(role: Role): boolean {
  return CHANGE_APPROVER_ROLES.includes(role);
}

/**
 * "Propose a compensation change" (CLAUDE.md §7) — one role wider than approving, because a
 * COMP_ANALYST may propose but not decide. Mirrors the `@PreAuthorize` on `ChangeController#propose`
 * and `#bulkPropose`; the boundary is that annotation, this only decides whether the bulk-propose
 * action appears on a selection at all.
 */
const CHANGE_PROPOSER_ROLES: readonly Role[] = ["HR_ADMIN", "HR_MANAGER", "COMP_ANALYST"];

export function canProposeChanges(role: Role): boolean {
  return CHANGE_PROPOSER_ROLES.includes(role);
}

/** "Manage salary bands & levels"-equivalent for FX rates (CLAUDE.md §7) — same two roles,
 * same reasoning as `FxRateController#add`'s own `@PreAuthorize`. */
const FX_RATE_MANAGER_ROLES: readonly Role[] = ["HR_ADMIN", "HR_MANAGER"];

export function canManageFxRates(role: Role): boolean {
  return FX_RATE_MANAGER_ROLES.includes(role);
}

/** "Create / edit employee record" (CLAUDE.md §7) — same two roles `EmployeeController#create`/
 * `#update`/`#setInitialCompensation` all require. */
const EMPLOYEE_MANAGER_ROLES: readonly Role[] = ["HR_ADMIN", "HR_MANAGER"];

export function canManageEmployees(role: Role): boolean {
  return EMPLOYEE_MANAGER_ROLES.includes(role);
}
