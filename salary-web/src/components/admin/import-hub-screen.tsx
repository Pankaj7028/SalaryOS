"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { EmployeeImportScreen } from "@/components/employee-import/employee-import-screen";
import { BandsImportPanel } from "@/components/bands/bands-import-panel";
import { ChangesBulkUploadPanel } from "@/components/changes/changes-bulk-upload-panel";

const TABS = [
  { tab: "employees", label: "Employees" },
  { tab: "bands", label: "Salary bands" },
  { tab: "changes", label: "Merit changes" },
];

/**
 * `/admin/import` (post-P9 QA pass). CLAUDE.md §7's RBAC table has exactly one "Import / bulk
 * upload: HR Admin" row — not three separate capabilities — so this is one screen with a tab per
 * CSV type, not three nav entries. Two of the three backends (bands, merit changes) have existed
 * since P5.3/P6.3 with no UI ever built for them; this is that UI. Tab state lives in `?tab=`
 * (CLAUDE.md §9), same pattern `/changes` already uses for its status tabs.
 */
export function ImportHubScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const tab = searchParams.get("tab") ?? "employees";

  function setTab(next: string) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("tab", next);
    router.push(`${pathname}?${params.toString()}`, { scroll: false });
  }

  return (
    <div className="space-y-4">
      <header>
        <h1 className="type-title">Import</h1>
        <p className="type-caption text-muted-foreground mt-1">
          Bulk changes from a CSV file, one tab per entity.
        </p>
      </header>

      <Tabs value={tab} onValueChange={setTab}>
        <TabsList>
          {TABS.map((t) => (
            <TabsTrigger key={t.tab} value={t.tab}>
              {t.label}
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value="employees" className="mt-4">
          <EmployeeImportScreen />
        </TabsContent>
        <TabsContent value="bands" className="mt-4">
          <BandsImportPanel />
        </TabsContent>
        <TabsContent value="changes" className="mt-4">
          <ChangesBulkUploadPanel />
        </TabsContent>
      </Tabs>
    </div>
  );
}
