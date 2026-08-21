import { EmployeeDetailScreen } from "@/components/employees/employee-detail-screen";

export default async function EmployeeDetailPage({ params }: PageProps<"/employees/[id]">) {
  const { id } = await params;
  return <EmployeeDetailScreen id={id} />;
}
