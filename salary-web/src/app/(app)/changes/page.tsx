import { Suspense } from "react";
import { ChangesScreen } from "@/components/changes/changes-screen";
import { TableSkeleton } from "@/components/feedback/states";

const SKELETON_COLUMNS = ["170px", "160px", "90px", "100px", "120px", "110px", "140px"];

export default function ChangesPage() {
  return (
    <Suspense fallback={<TableSkeleton columns={SKELETON_COLUMNS} />}>
      <ChangesScreen />
    </Suspense>
  );
}
