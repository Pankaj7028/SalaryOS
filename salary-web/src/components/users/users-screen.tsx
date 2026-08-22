"use client";

import { useState } from "react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, TableSkeleton } from "@/components/feedback/states";
import { useUsers } from "@/lib/api/users-queries";
import { useSession } from "@/lib/auth/auth-queries";
import { ROLE_LABEL } from "@/components/users/role-label";
import { CreateUserDialog } from "@/components/users/create-user-dialog";
import { EditUserDialog } from "@/components/users/edit-user-dialog";
import type { User } from "@/lib/api/users";

const COLUMNS = ["220px", "160px", "140px", "90px", "140px"];

/**
 * `/admin/users` (ui doc §8.9, "Manage users & roles" — HR_ADMIN alone, CLAUDE.md §7). Backend
 * built at P8.1; this screen was the missing piece found during P8's QA pass.
 */
export function UsersScreen() {
  const users = useUsers();
  const session = useSession();
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<User | null>(null);

  return (
    <div className="space-y-4">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="type-title">Users</h1>
          <p className="type-caption text-muted-foreground mt-1">
            {users.data ? `${users.data.length} users` : "Loading…"}
          </p>
        </div>
        <Button size="sm" onClick={() => setCreateOpen(true)}>New user</Button>
      </header>

      {users.isError ? (
        <ErrorState
          title="Couldn't load users"
          detail="Check your connection and try again."
          action={<Button size="sm" variant="outline" onClick={() => users.refetch()}>Retry</Button>}
        />
      ) : users.isLoading ? (
        <TableSkeleton columns={COLUMNS} rows={6} />
      ) : (users.data ?? []).length === 0 ? (
        <EmptyState title="No users yet" detail="Create the first account to get started." />
      ) : (
        <div className="border-border overflow-x-auto rounded-lg border">
          <Table>
            <TableHeader className="bg-muted/40">
              <TableRow className="h-10">
                <TableHead className="type-label text-muted-foreground">Email</TableHead>
                <TableHead className="type-label text-muted-foreground">Name</TableHead>
                <TableHead className="type-label text-muted-foreground">Role</TableHead>
                <TableHead className="type-label text-muted-foreground">Status</TableHead>
                <TableHead className="type-label text-muted-foreground">Created</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(users.data ?? []).map((user) => (
                <TableRow
                  key={user.id}
                  className="h-10 cursor-pointer"
                  onClick={() => setEditing(user)}
                >
                  <TableCell className="type-body-sm">
                    {user.email} {user.id === session.data?.id ? <span className="text-muted-foreground">(you)</span> : null}
                  </TableCell>
                  <TableCell className="type-body-sm">{user.fullName}</TableCell>
                  <TableCell className="type-body-sm">{ROLE_LABEL[user.role]}</TableCell>
                  <TableCell className={user.status === "INACTIVE" ? "type-body-sm text-muted-foreground" : "type-body-sm"}>
                    {user.status === "ACTIVE" ? "Active" : "Inactive"}
                  </TableCell>
                  <TableCell className="figure-sm text-muted-foreground">
                    {new Date(user.createdAt).toLocaleDateString()}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <CreateUserDialog open={createOpen} onOpenChange={setCreateOpen} />
      {editing ? (
        <EditUserDialog
          open
          onOpenChange={(open) => !open && setEditing(null)}
          user={editing}
          isSelf={editing.id === session.data?.id}
        />
      ) : null}
    </div>
  );
}
