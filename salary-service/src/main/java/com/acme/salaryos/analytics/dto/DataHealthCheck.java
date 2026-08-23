package com.acme.salaryos.analytics.dto;

/**
 * One named data-quality check and how many rows currently fail it.
 *
 * <p>{@code filter} is the employee-list query string that reproduces the failing rows where one
 * exists, so the console can drill through to the actual people instead of just naming a number —
 * {@code null} where the list has no filter that expresses the check. Reusing the existing list
 * means the drill-through inherits its RBAC and its audit trail for free.
 */
public record DataHealthCheck(
		String key,
		String label,
		String explanation,
		DataHealthSeverity severity,
		int count,
		String filter) {
}
