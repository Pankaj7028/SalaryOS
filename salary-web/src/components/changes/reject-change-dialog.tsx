"use client";

import { useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useRejectChange } from "@/lib/api/changes-queries";
import type { Change } from "@/lib/api/changes";

/** ui doc §8.5: "required note on reject" — enforced here (submit disabled until non-empty) and
 * again server-side (400 without one), same belt-and-suspenders pattern as every other mandatory
 * note in this app (propose's outside-band note, CLAUDE.md §6.1 discipline aside). */
export function RejectChangeDialog({
  open,
  onOpenChange,
  change,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  change: Change;
}) {
  const [note, setNote] = useState("");
  const rejectChange = useRejectChange();

  async function handleReject() {
    if (!note.trim()) return;
    await rejectChange.mutateAsync({ id: change.id, decisionNote: note });
    setNote("");
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) setNote(""); onOpenChange(next); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reject change</DialogTitle>
          <p className="type-caption text-muted-foreground">
            {change.employeeFirstName} {change.employeeLastName} · {change.employeeNumber}
          </p>
        </DialogHeader>

        <div className="flex flex-col gap-1">
          <Label htmlFor="reject-note">Reason (required)</Label>
          <Textarea
            id="reject-note"
            rows={3}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            autoFocus
          />
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            type="button"
            variant="destructive"
            onClick={handleReject}
            disabled={!note.trim() || rejectChange.isPending}
          >
            {rejectChange.isPending ? "Rejecting…" : "Reject"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
