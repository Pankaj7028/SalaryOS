import type { Role } from "./roles";

export type CurrentUser = {
  name: string;
  email: string;
  role: Role;
};

/**
 * PLACEHOLDER identity, and the single seam where the real one lands.
 *
 * At P2.5 this becomes a fetch of `GET /api/auth/me` with the session cookie —
 * never a token read in the browser (CLAUDE.md §4.4). Everything that needs to
 * know who the user is already goes through here, so that change is local.
 *
 * Change `role` to try the sidebar as another role until then.
 */
export async function getCurrentUser(): Promise<CurrentUser> {
  return {
    name: "Dana Whitfield",
    email: "dana.whitfield@acme.example",
    role: "HR_MANAGER",
  };
}
