package com.acme.salaryos.analytics.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * FR-6.4: pay gap by demographic group. {@code employee_demographics} is joined ONLY here, never
 * given a JPA relationship to {@code Employee} (CLAUDE.md §6.6) — and every query below suppresses
 * a small group with its own {@code HAVING count(*) >= 5}, in SQL, so there is no code path that
 * ever fetches a group under five into the application at all (backend doc §6's own stated design
 * goal, quoted verbatim in its {@code PayGapQuery} example).
 *
 * <p>Only {@code gender} is used as the grouping dimension — {@code ethnicity_code} exists on the
 * same table and could be added the same way later, but doubling the grouping dimensions here
 * doubles the suppression bookkeeping for no requirement text asking for it yet.
 */
@Component
public class PayGapQuery {

	private final JdbcTemplate jdbcTemplate;

	public PayGapQuery(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** Org-wide, ignoring job level entirely — never returns a gender group under five people. */
	private static final String UNADJUSTED_GROUPS_SQL = """
			SELECT d.gender AS grp, count(*) AS n,
			       percentile_cont(0.5) WITHIN GROUP (ORDER BY c.normalized_annual_base) AS median
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e ON e.id = c.employee_id
			  JOIN salary_schema.employee_demographics d ON d.employee_id = e.id
			 WHERE e.status = 'ACTIVE' AND d.gender IS NOT NULL
			 GROUP BY d.gender
			HAVING count(*) >= 5
			""";

	/** One row per (job level × country × gender) group with at least five people — the
	 * level-adjusted cohort table's raw material, assembled into cohorts in {@code PayGapQuery}'s
	 * caller since a cohort needs at least two surviving groups to have a "gap" at all. */
	private static final String COHORT_GROUPS_SQL = """
			SELECT jl.id AS job_level_id, jl.title AS job_level_label, jl.sort_order,
			       l.country_code, co.name AS country_label,
			       d.gender AS grp, count(*) AS n,
			       percentile_cont(0.5) WITHIN GROUP (ORDER BY c.normalized_annual_base) AS median
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e   ON e.id = c.employee_id
			  JOIN salary_schema.job_levels jl ON jl.id = e.job_level_id
			  JOIN salary_schema.locations l   ON l.id = e.location_id
			  JOIN salary_schema.countries co  ON co.code = l.country_code
			  JOIN salary_schema.employee_demographics d ON d.employee_id = e.id
			 WHERE e.status = 'ACTIVE' AND d.gender IS NOT NULL
			 GROUP BY jl.id, jl.title, jl.sort_order, l.country_code, co.name, d.gender
			HAVING count(*) >= 5
			ORDER BY jl.sort_order, co.name
			""";

	/** Aggregate count only — how many (level × country) pairings have ANY demographic coverage at
	 * all, regardless of group size. Never returns anything about a specific small group; just a
	 * total, used to compute how many cohorts {@link #cohortGroups} left out. */
	private static final String TOTAL_COHORTS_SQL = """
			SELECT count(DISTINCT (jl.id, l.country_code))
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e   ON e.id = c.employee_id
			  JOIN salary_schema.job_levels jl ON jl.id = e.job_level_id
			  JOIN salary_schema.locations l   ON l.id = e.location_id
			  JOIN salary_schema.employee_demographics d ON d.employee_id = e.id
			 WHERE e.status = 'ACTIVE' AND d.gender IS NOT NULL
			""";

	public record GroupRow(String group, int count, BigDecimal median) {
	}

	public record CohortGroupRow(UUID jobLevelId, String jobLevelLabel, String countryCode, String countryLabel, String group, int count, BigDecimal median) {
	}

	public List<GroupRow> unadjustedGroups() {
		return jdbcTemplate.query(UNADJUSTED_GROUPS_SQL,
				(rs, rowNum) -> new GroupRow(rs.getString("grp"), rs.getInt("n"), rs.getBigDecimal("median")));
	}

	public List<CohortGroupRow> cohortGroups() {
		return jdbcTemplate.query(COHORT_GROUPS_SQL, (rs, rowNum) -> new CohortGroupRow(
				(UUID) rs.getObject("job_level_id"), rs.getString("job_level_label"),
				rs.getString("country_code"), rs.getString("country_label"),
				rs.getString("grp"), rs.getInt("n"), rs.getBigDecimal("median")));
	}

	public int totalCohortsWithDemographicCoverage() {
		Integer count = jdbcTemplate.queryForObject(TOTAL_COHORTS_SQL, Integer.class);
		return count == null ? 0 : count;
	}

}
