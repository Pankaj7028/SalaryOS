"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

/**
 * Currency toggle (§6.1). A product control, not a preference: it switches every
 * money figure on screen between the currency each person is actually paid in
 * and the normalised base currency.
 *
 * Its state lives in the URL (?ccy=USD|local), never in component state, so a
 * shared link shows the recipient exactly what the sender was looking at
 * (CLAUDE.md §9).
 */
export type Currency = "USD" | "local";

/**
 * Reads its own value from the URL rather than taking a prop: layouts do not
 * receive searchParams in the App Router, and the topbar lives in a layout.
 */
export function CurrencyToggle() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const value: Currency = params.get("ccy") === "local" ? "local" : "USD";

  function onChange(next: string) {
    const q = new URLSearchParams(params.toString());
    if (next === "USD") q.delete("ccy");
    else q.set("ccy", next);
    const qs = q.toString();
    router.push(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  }

  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger size="sm" className="w-[104px]" aria-label="Currency">
        <SelectValue />
      </SelectTrigger>
      <SelectContent align="end">
        <SelectItem value="USD">USD</SelectItem>
        <SelectItem value="local">As paid</SelectItem>
      </SelectContent>
    </Select>
  );
}
