"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useState } from "react";

/**
 * P10.5 — "25–50 of 9,847", Previous/Next, and a jump to a page number.
 *
 * <p>The count is what makes the rest of this possible. Until P10.5 the list could say what was on
 * this page and nothing about the shape of the answer: 50 rows and a Next button is the same screen
 * whether the filter matched 51 people or 5,100, and those are very different answers to "how many
 * people are below their band minimum".
 *
 * <p><b>Jumping is by row offset, not by cursor</b> — a cursor names the last row you saw, and page
 * 12 is defined entirely by rows you have not seen. The server accepts either and the URL carries
 * whichever was used (CLAUDE.md §9), so a pasted link reopens the same page for the next person.
 */
export function ListPagination({
  pageStart,
  pageSize,
  itemCount,
  totalCount,
  hasNext,
  hasPrevious,
  onPrevious,
  onNext,
  onJump,
}: {
  /** 0-based index of this page's first row within the whole filtered result set. */
  pageStart: number;
  pageSize: number;
  itemCount: number;
  totalCount: number;
  hasNext: boolean;
  hasPrevious: boolean;
  onPrevious: () => void;
  onNext: () => void;
  onJump: (offset: number) => void;
}) {
  const lastPage = Math.max(1, Math.ceil(totalCount / pageSize));
  const currentPage = Math.floor(pageStart / pageSize) + 1;
  const [draft, setDraft] = useState("");

  function jump() {
    const page = Number(draft);
    if (!Number.isInteger(page) || page < 1 || page > lastPage) return;
    setDraft("");
    onJump((page - 1) * pageSize);
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
      <p className="type-body-sm text-muted-foreground">
        {itemCount === 0 ? (
          "No results"
        ) : (
          <>
            <span className="figure-sm text-foreground">
              {(pageStart + 1).toLocaleString()}–{(pageStart + itemCount).toLocaleString()}
            </span>{" "}
            of <span className="figure-sm text-foreground">{totalCount.toLocaleString()}</span>
          </>
        )}
      </p>

      <div className="flex items-center gap-2">
        {lastPage > 1 ? (
          <div className="mr-1 flex items-center gap-1.5">
            <label htmlFor="pageJump" className="type-caption text-muted-foreground">
              Page
            </label>
            <Input
              id="pageJump"
              inputMode="numeric"
              className="figure-sm h-8 w-16"
              placeholder={String(currentPage)}
              value={draft}
              onChange={(event) => setDraft(event.target.value.replace(/\D/g, ""))}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  jump();
                }
              }}
              aria-label={`Jump to a page, 1 to ${lastPage}`}
            />
            <span className="type-caption text-muted-foreground">
              of {lastPage.toLocaleString()}
            </span>
            <Button size="sm" variant="ghost" onClick={jump} disabled={draft === ""}>
              Go
            </Button>
          </div>
        ) : null}

        <Button size="sm" variant="outline" disabled={!hasPrevious} onClick={onPrevious}>
          Previous
        </Button>
        <Button size="sm" variant="outline" disabled={!hasNext} onClick={onNext}>
          Next
        </Button>
      </div>
    </div>
  );
}
