"use client";

import { LogOut, UserCog } from "lucide-react";
import { useRouter } from "next/navigation";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import type { CurrentUser } from "@/lib/auth/current-user";
import { useLogout } from "@/lib/auth/auth-queries";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

/**
 * Avatar menu (§6.1): name, email, role badge, Account, Sign out.
 *
 * The identity comes from getCurrentUser(), a server-side read of
 * `GET /api/auth/me` — never a token read in the browser (§4.4).
 */
export function UserMenu({ user }: { user: CurrentUser }) {
  const router = useRouter();
  const logout = useLogout();
  const initials = user.name
    .split(" ")
    .map((p) => p[0])
    .join("");

  async function handleSignOut() {
    await logout.mutateAsync();
    router.push("/sign-in");
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label="Account menu"
          className="focus-visible:ring-ring rounded-full outline-none focus-visible:ring-2"
        >
          <Avatar className="size-8">
            <AvatarFallback className="type-caption">{initials}</AvatarFallback>
          </Avatar>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64">
        <div className="px-2 py-2">
          <p className="type-subsection">{user.name}</p>
          <p className="type-caption text-muted-foreground truncate">{user.email}</p>
          <Badge variant="secondary" className="type-label mt-2">
            {user.role.replace("_", " ")}
          </Badge>
        </div>
        <DropdownMenuSeparator />
        <DropdownMenuItem disabled className="gap-2">
          <UserCog aria-hidden className="size-4" />
          <span className="type-body-sm">Account</span>
        </DropdownMenuItem>
        <DropdownMenuItem className="gap-2" disabled={logout.isPending} onSelect={handleSignOut}>
          <LogOut aria-hidden className="size-4" />
          <span className="type-body-sm">Sign out</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
