"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Search } from "lucide-react";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { NAV_GROUPS } from "@/lib/nav";

/**
 * ⌘K palette (§6.1). Looking up one person is the most common task in this
 * product, so it must never require the list page.
 *
 * The employee results below are STUBS. Real search — by name, employee number
 * and email, showing pay and band status inline — needs the API and arrives with
 * the employees module at P4. Deliberately shaped like the real thing so the
 * wiring is the only thing left to do.
 */
const STUB_PEOPLE = [
  { name: "Ada Okonkwo", ref: "EMP-01042", detail: "Senior Engineer · London" },
  { name: "Ravi Menon", ref: "EMP-02918", detail: "Engineering Manager · Bengaluru" },
  { name: "Marta Silva", ref: "EMP-00317", detail: "Product Designer · Lisbon" },
];

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const router = useRouter();

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setOpen((v) => !v);
      }
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="border-border text-muted-foreground hover:bg-accent focus-visible:ring-ring type-body-sm hidden h-8 w-[320px] items-center justify-between rounded-md border px-3 outline-none focus-visible:ring-2 lg:flex"
      >
        <span className="flex items-center gap-2">
          <Search aria-hidden className="size-3.5" />
          Search employees
        </span>
        <kbd className="figure-sm text-muted-foreground">⌘K</kbd>
      </button>

      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="Search employees"
        className="hover:bg-accent focus-visible:ring-ring grid size-8 place-items-center rounded-md outline-none focus-visible:ring-2 lg:hidden"
      >
        <Search aria-hidden className="size-4" />
      </button>

      <CommandDialog
        open={open}
        onOpenChange={setOpen}
        title="Search"
        description="Search employees, or jump to a section."
      >
        <CommandInput placeholder="Search employees by name, number or email…" />
        <CommandList>
          <CommandEmpty>No results.</CommandEmpty>
          <CommandGroup heading="People (sample data — live search lands at P4)">
            {STUB_PEOPLE.map((p) => (
              <CommandItem key={p.ref} value={`${p.name} ${p.ref}`} onSelect={() => setOpen(false)}>
                <div className="flex w-full items-center justify-between gap-3">
                  <span className="type-body-sm">{p.name}</span>
                  <span className="figure-sm text-muted-foreground">{p.ref}</span>
                </div>
                <span className="type-caption text-muted-foreground">{p.detail}</span>
              </CommandItem>
            ))}
          </CommandGroup>
          {NAV_GROUPS.map((group) => (
            <CommandGroup key={group.caption} heading={group.caption}>
              {group.items.map((item) => (
                <CommandItem
                  key={item.href}
                  value={item.label}
                  onSelect={() => {
                    setOpen(false);
                    router.push(item.href);
                  }}
                >
                  <item.icon aria-hidden className="size-4" />
                  <span className="type-body-sm">{item.label}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          ))}
        </CommandList>
      </CommandDialog>
    </>
  );
}
