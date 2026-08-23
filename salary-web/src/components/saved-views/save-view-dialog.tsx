"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Controller, useForm } from "react-hook-form";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useSaveView } from "@/lib/api/saved-views-queries";
import {
  saveViewFormSchema,
  type SaveViewFormValues,
} from "@/components/saved-views/save-view-form-schema";
import { saveableQueryString } from "@/components/saved-views/saveable-query-string";

export function SaveViewDialog({
  open,
  onOpenChange,
  route,
  queryString,
  existingNames,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  route: string;
  queryString: string;
  /** This user's own view names on this route — reusing one overwrites, so say so before they do. */
  existingNames: string[];
}) {
  const saveView = useSaveView();
  const {
    register,
    handleSubmit,
    reset,
    control,
    watch,
    formState: { errors },
  } = useForm<SaveViewFormValues>({
    resolver: zodResolver(saveViewFormSchema),
    defaultValues: { name: "", visibility: "private" },
  });

  const toSave = saveableQueryString(queryString);
  const filterCount = Array.from(new URLSearchParams(toSave).keys()).length;
  const name = watch("name").trim();
  const overwrites = name.length > 0 && existingNames.some((existing) => existing.toLowerCase() === name.toLowerCase());

  async function onSubmit(values: SaveViewFormValues) {
    await saveView.mutateAsync({
      name: values.name.trim(),
      route,
      queryString: toSave,
      shared: values.visibility === "shared",
    });
    reset();
    onOpenChange(false);
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) reset();
        onOpenChange(next);
      }}
    >
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>Save this view</DialogTitle>
            <p className="type-caption text-muted-foreground">
              Saves the question, not the answer. Opening it later re-runs the same query against
              today&rsquo;s data, with your own permissions.
            </p>
          </DialogHeader>

          <div className="mt-4 flex flex-col gap-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor="savedViewName">Name</Label>
              <Input id="savedViewName" maxLength={80} autoComplete="off" {...register("name")} />
              {errors.name ? (
                <p role="alert" className="type-body-sm text-critical">{errors.name.message}</p>
              ) : null}
              {overwrites && !errors.name ? (
                <p className="type-caption text-attention">
                  You already have a view called &ldquo;{name}&rdquo;. Saving replaces it.
                </p>
              ) : null}
            </div>

            <div className="flex flex-col gap-1">
              <Label htmlFor="savedViewVisibility">Visible to</Label>
              <Controller
                control={control}
                name="visibility"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="savedViewVisibility" size="sm">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="private">Only me</SelectItem>
                      <SelectItem value="shared">Everyone</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
              <p className="type-caption text-muted-foreground">
                Sharing shares the question. Each person still sees only what their own role allows.
              </p>
            </div>

            <div className="border-border bg-muted/30 rounded-md border px-3 py-2">
              <p className="type-label text-muted-foreground">What gets saved</p>
              <p className="figure-sm mt-1 break-all">
                {route}
                {toSave ? `?${toSave}` : ""}
              </p>
              <p className="type-caption text-muted-foreground mt-1">
                {filterCount === 0
                  ? "No filters — the unfiltered list."
                  : `${filterCount} ${filterCount === 1 ? "setting" : "settings"}. Your page position isn't saved.`}
              </p>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" size="sm" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={saveView.isPending}>
              {saveView.isPending ? "Saving…" : overwrites ? "Replace view" : "Save view"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
