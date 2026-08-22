"use client";

import { useQuery } from "@tanstack/react-query";
import { auditKeys } from "@/lib/api/keys";
import { fetchAuditEvents, type AuditSearchFilters } from "@/lib/api/audit";

export function useAuditEvents(filters: AuditSearchFilters) {
  return useQuery({
    queryKey: auditKeys.search(filters),
    queryFn: () => fetchAuditEvents(filters),
  });
}
