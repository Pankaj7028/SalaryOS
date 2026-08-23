import { Suspense } from "react";
import { ImportHubScreen } from "@/components/admin/import-hub-screen";
import { TableSkeleton } from "@/components/feedback/states";

export default function ImportPage() {
  return (
    <Suspense fallback={<TableSkeleton columns={["100%"]} rows={4} />}>
      <ImportHubScreen />
    </Suspense>
  );
}
