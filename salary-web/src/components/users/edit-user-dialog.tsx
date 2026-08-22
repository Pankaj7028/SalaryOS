"use client";

import { useState, type FormEvent } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useIssueResetToken, useUpdateUser } from "@/lib/api/users-queries";
import { ROLES, type Role } from "@/lib/auth/roles";
import { ROLE_LABEL } from "@/components/users/role-label";
import type { User } from "@/lib/api/users";

/**
 * A full replace, matching `UpdateUserRequest`'s own "every write states the whole intended
 * state" convention (P8.1). Self-role-change and last-active-HR-Admin protection are enforced
 * server-side (`CannotChangeOwnRoleException` / `LastActiveHrAdminException`) — this dialog
 * doesn't duplicate that logic, it just surfaces whatever message comes back.
 */
export function EditUserDialog({
  open,
  onOpenChange,
  user,
  isSelf,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  user: User;
  isSelf: boolean;
}) {
  const updateUser = useUpdateUser();
  const issueResetToken = useIssueResetToken();
  const [fullName, setFullName] = useState(user.fullName);
  const [role, setRole] = useState<Role>(user.role);
  const [status, setStatus] = useState<"ACTIVE" | "INACTIVE">(user.status);
  const [issuedToken, setIssuedToken] = useState<{ token: string; expiresAt: string } | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    await updateUser.mutateAsync({ id: user.id, input: { fullName, role, status } });
    onOpenChange(false);
  }

  async function handleIssueResetToken() {
    const result = await issueResetToken.mutateAsync(user.id);
    setIssuedToken(result);
  }

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) setIssuedToken(null); onOpenChange(next); }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit} noValidate>
          <DialogHeader>
            <DialogTitle>{user.email}</DialogTitle>
            {isSelf ? <p className="type-caption text-muted-foreground">This is you — your own role can&apos;t be changed here.</p> : null}
          </DialogHeader>
          <div className="mt-4 flex flex-col gap-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor="edit-user-name">Full name</Label>
              <Input id="edit-user-name" required value={fullName} onChange={(e) => setFullName(e.target.value)} />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div className="flex flex-col gap-1">
                <Label htmlFor="edit-user-role">Role</Label>
                <Select value={role} onValueChange={(value) => setRole(value as Role)} disabled={isSelf}>
                  <SelectTrigger id="edit-user-role" size="sm"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {ROLES.map((r) => (
                      <SelectItem key={r} value={r}>{ROLE_LABEL[r]}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor="edit-user-status">Status</Label>
                <Select value={status} onValueChange={(value) => setStatus(value as "ACTIVE" | "INACTIVE")}>
                  <SelectTrigger id="edit-user-status" size="sm"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ACTIVE">Active</SelectItem>
                    <SelectItem value="INACTIVE">Inactive</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="border-border flex flex-col gap-2 rounded-lg border border-dashed p-3">
              <div className="flex items-center justify-between gap-2">
                <span className="type-body-sm text-muted-foreground">Password reset</span>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={issueResetToken.isPending}
                  onClick={handleIssueResetToken}
                >
                  {issueResetToken.isPending ? "Issuing…" : "Issue reset token"}
                </Button>
              </div>
              {issuedToken ? (
                <div className="flex flex-col gap-1">
                  <span className="type-caption text-muted-foreground">
                    Shown once — give this to {user.fullName} now. Expires {new Date(issuedToken.expiresAt).toLocaleTimeString()}.
                  </span>
                  <code className="figure-sm bg-muted/40 rounded px-2 py-1.5 break-all">{issuedToken.token}</code>
                </div>
              ) : null}
            </div>
          </div>
          <DialogFooter>
            <Button type="button" size="sm" variant="outline" onClick={() => onOpenChange(false)}>
              Close
            </Button>
            <Button type="submit" size="sm" disabled={updateUser.isPending}>
              {updateUser.isPending ? "Saving…" : "Save"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
