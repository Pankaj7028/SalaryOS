import { Suspense } from "react";
import { EmployeesScreen } from "@/components/employees/employees-screen";
import { TableSkeleton } from "@/components/feedback/states";

const SKELETON_COLUMNS = ["180px", "140px", "120px", "100px", "110px", "60px", "70px", "70px"];

export default function EmployeesPage() {
  return (
    <Suspense fallback={<TableSkeleton columns={SKELETON_COLUMNS} />}>
      <EmployeesScreen />
    </Suspense>
  );
}
