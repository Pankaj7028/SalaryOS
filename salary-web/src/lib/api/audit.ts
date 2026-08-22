import { apiFetch } from "./client";

/** FR-7.1/7.2: one row per write or read of individual pay data — append-only, never edited. */
export type AuditEvent = {
  id: string;
  occurredAt: string;
  actorUserId: string | null;
  actorEmail: string | null;
  actorFullName: string | null;
  actorRole: string;
  action: string;
  entityType: string;
  entityId: string | null;
  beforeJson: string | null;
  afterJson: string | null;
  ip: string | null;
};

export type AuditSearchFilters = {
  actorUserId?: string;
  entityType?: string;
  action?: string;
  from?: string;
  to?: string;
};

function buildQuery(filters: AuditSearchFilters): string {
  const params = new URLSearchParams();
  if (filters.actorUserId) params.set("actorUserId", filters.actorUserId);
  if (filters.entityType) params.set("entityType", filters.entityType);
  if (filters.action) params.set("action", filters.action);
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  return params.toString();
}

export async function fetchAuditEvents(filters: AuditSearchFilters): Promise<AuditEvent[]> {
  const query = buildQuery(filters);
  const response = await apiFetch(`/api/admin/audit${query ? `?${query}` : ""}`);
  return (await response.json()) as AuditEvent[];
}

/** FR-7.4: CSV of the exact same filter as {@link fetchAuditEvents} — a plain navigation downloads
 * it (same pattern as `employeesExportUrl`), carrying the session cookie because it's a top-level GET. */
export function auditExportUrl(filters: AuditSearchFilters): string {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  const query = buildQuery(filters);
  return `${base}/api/admin/audit/export${query ? `?${query}` : ""}`;
}
