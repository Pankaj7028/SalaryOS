package com.acme.salaryos.analytics.query;

import com.acme.salaryos.analytics.dto.DataHealthCheck;
import com.acme.salaryos.analytics.dto.DataHealthSeverity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * P11.1. Named data-quality checks, each a plain {@code count(*)} in the database.
 *
 * <p><strong>Checks the schema already makes impossible are not here.</strong> Duplicate employee
 * numbers cannot occur ({@code employee_number} is {@code UNIQUE}), an FTE outside 0.01–1.00 cannot
 * occur (a {@code CHECK}), and a termination date without {@code status = 'TERMINATED'} cannot
 * occur ({@code emp_termination_date_requires_status}). Reporting a check that is structurally
 * always zero is noise pretending to be a safety net — the same reasoning that dropped
 * {@code missingCoverage} at P10.1.
 *
 * <p>Every query is schema-qualified per CLAUDE.md §6.5.
 */
@Component
public class DataHealthQuery {

	private final JdbcTemplate jdbcTemplate;

	public DataHealthQuery(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private static final String TOTAL_EMPLOYEES_SQL =
			"SELECT count(*) FROM salary_schema.employees WHERE status <> 'TERMINATED'";

	/** FR-4.4: never silently given a compa-ratio of 1.0 — but someone has to be told they exist. */
	private static final String NO_BAND_SQL = """
			SELECT count(*) FROM salary_schema.employee_current_comp WHERE band_status = 'NO_BAND'
			""";

	/**
	 * An active employee with no ledger row at all. Every figure about them — cost, compa-ratio,
	 * pay gap — silently omits them, because every analytics query joins from the projection.
	 */
	private static final String NO_COMPENSATION_SQL = """
			SELECT count(*)
			  FROM salary_schema.employees e
			 WHERE e.status <> 'TERMINATED'
			   AND NOT EXISTS (SELECT 1 FROM salary_schema.employee_current_comp c WHERE c.employee_id = e.id)
			""";

	/** FR-2.5's flag, surfaced as a queue rather than a per-row badge nobody goes looking for. */
	private static final String BAND_MISMATCHED_SQL = """
			SELECT count(*) FROM salary_schema.employees WHERE band_mismatched AND status <> 'TERMINATED'
			""";

	/**
	 * Paid in a currency that is not their country's default. Legitimate (expats, regional
	 * contracts), which is why this is a WARNING — but at import scale it is far more often a
	 * column that got filled in with the wrong default.
	 */
	private static final String CURRENCY_MISMATCH_SQL = """
			SELECT count(*)
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e  ON e.id = c.employee_id
			  JOIN salary_schema.locations l  ON l.id = e.location_id
			  JOIN salary_schema.countries co ON co.code = l.country_code
			 WHERE c.currency <> co.default_currency
			""";

	/** Pay that starts before the person does — a date column transposed on import. */
	private static final String PAY_BEFORE_HIRE_SQL = """
			SELECT count(DISTINCT r.employee_id)
			  FROM salary_schema.compensation_records r
			  JOIN salary_schema.employees e ON e.id = r.employee_id
			 WHERE r.effective_from < e.hire_date
			""";

	/**
	 * Terminated, but the ledger period is still open — they keep accruing cost forever. P5.4's
	 * {@code terminate()} closes the period, so a row here means someone was terminated by a path
	 * that bypassed it (a direct SQL fix, or an import).
	 */
	private static final String TERMINATED_WITH_OPEN_PAY_SQL = """
			SELECT count(*)
			  FROM salary_schema.employees e
			  JOIN salary_schema.compensation_records r ON r.employee_id = e.id
			 WHERE e.status = 'TERMINATED'
			   AND r.effective_to IS NULL
			""";

	/** A full-time employee at less than 1.0 FTE — one of the two is wrong. */
	private static final String FULL_TIME_PARTIAL_FTE_SQL = """
			SELECT count(*)
			  FROM salary_schema.employees
			 WHERE status <> 'TERMINATED' AND employment_type = 'FULL_TIME' AND fte < 1.00
			""";

	/** Reporting to someone who has left — the org chart and any manager rollup are both wrong. */
	private static final String TERMINATED_MANAGER_SQL = """
			SELECT count(*)
			  FROM salary_schema.employees e
			  JOIN salary_schema.employees m ON m.id = e.manager_id
			 WHERE e.status <> 'TERMINATED' AND m.status = 'TERMINATED'
			""";

	/**
	 * A management cycle. Rare, catastrophic for anything that walks the chain — an org drill-down
	 * (P14.3) recurses forever on one of these. {@code UNION} (not {@code UNION ALL}) plus the
	 * depth guard terminates even when a cycle exists; without both, this query is itself the
	 * infinite loop it is looking for.
	 */
	private static final String CIRCULAR_MANAGEMENT_SQL = """
			WITH RECURSIVE chain(root_id, current_id, depth) AS (
			    SELECT e.id, e.manager_id, 1
			      FROM salary_schema.employees e
			     WHERE e.manager_id IS NOT NULL
			    UNION
			    SELECT c.root_id, e.manager_id, c.depth + 1
			      FROM chain c
			      JOIN salary_schema.employees e ON e.id = c.current_id
			     WHERE e.manager_id IS NOT NULL
			       AND c.depth < 50
			)
			SELECT count(DISTINCT root_id) FROM chain WHERE current_id = root_id
			""";

	public int totalEmployees() {
		return count(TOTAL_EMPLOYEES_SQL);
	}

	/** Ordered most-severe first — the console shows what needs attention, not what sorts first. */
	public List<DataHealthCheck> checks() {
		return List.of(
				check("noCompensation", "Active employees with no pay record",
						"Every cost, compa-ratio and pay-gap figure silently omits them.",
						DataHealthSeverity.CRITICAL, NO_COMPENSATION_SQL, null),
				check("terminatedWithOpenPay", "Terminated employees still being paid",
						"Their pay period was never closed, so they accrue cost indefinitely.",
						DataHealthSeverity.CRITICAL, TERMINATED_WITH_OPEN_PAY_SQL, "status=TERMINATED"),
				check("payBeforeHire", "Pay starting before the hire date",
						"Usually two date columns transposed during an import.",
						DataHealthSeverity.CRITICAL, PAY_BEFORE_HIRE_SQL, null),
				check("circularManagement", "Circular management chains",
						"Anything that walks the reporting line never terminates.",
						DataHealthSeverity.CRITICAL, CIRCULAR_MANAGEMENT_SQL, null),
				check("noBand", "Employees with no matching salary band",
						"No compa-ratio can be computed, so they fall out of band analysis.",
						DataHealthSeverity.WARNING, NO_BAND_SQL, "bandStatus=NO_BAND"),
				check("bandMismatched", "Level or location changed since pay last did",
						"Flagged by FR-2.5 and cleared only when a compensation change resolves it.",
						DataHealthSeverity.WARNING, BAND_MISMATCHED_SQL, null),
				check("terminatedManager", "Reporting to a terminated manager",
						"The org chart and every manager rollup are wrong for these people.",
						DataHealthSeverity.WARNING, TERMINATED_MANAGER_SQL, null),
				check("fullTimePartialFte", "Full-time employees below 1.0 FTE",
						"Either the employment type or the FTE is wrong; annualisation uses FTE.",
						DataHealthSeverity.WARNING, FULL_TIME_PARTIAL_FTE_SQL, null),
				check("currencyMismatch", "Paid in a non-default currency for their country",
						"Legitimate for expats and regional contracts — but often a wrong default on import.",
						DataHealthSeverity.INFO, CURRENCY_MISMATCH_SQL, null));
	}

	private DataHealthCheck check(String key, String label, String explanation,
			DataHealthSeverity severity, String sql, String filter) {
		return new DataHealthCheck(key, label, explanation, severity, count(sql), filter);
	}

	private int count(String sql) {
		Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
		return result == null ? 0 : result;
	}

}
