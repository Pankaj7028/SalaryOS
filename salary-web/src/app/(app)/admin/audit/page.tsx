import { Suspense } from "react";
import { AuditScreen } from "@/components/audit/audit-screen";
import { TableSkeleton } from "@/components/feedback/states";

const SKELETON_COLUMNS = ["150px", "180px", "90px", "140px", "140px", "260px"];

export default function AuditPage() {
  return (
    <Suspense fallback={<TableSkeleton columns={SKELETON_COLUMNS} />}>
      <AuditScreen />
    </Suspense>
  );
}
