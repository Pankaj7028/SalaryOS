import type { Role } from "@/lib/auth/roles";

export const ROLE_LABEL: Record<Role, string> = {
  HR_ADMIN: "HR Admin",
  HR_MANAGER: "HR Manager",
  COMP_ANALYST: "Comp Analyst",
  AUDITOR: "Auditor",
};
