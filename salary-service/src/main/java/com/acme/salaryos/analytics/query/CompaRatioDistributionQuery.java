package com.acme.salaryos.analytics.query;

import com.acme.salaryos.analytics.dto.CompaRatioGroupMedian;
import com.acme.salaryos.analytics.dto.CompaRatioHistogramBucket;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * FR-6.3: compa-ratio histogram and quartiles, filterable by department, level, and country — all
 * three optional, expressed as {@code (:param IS NULL OR column = :param)} in SQL rather than
 * built up by string concatenation in Java, so every query text stays one self-contained,
 * scannable constant ({@code NativeQuerySchemaQualificationTest}'s declaration-scanning, added at
 * P7.1). Only employees with a band count (a {@code NO_BAND} employee has no compa-ratio to place
 * anywhere — same exclusion FR-6.2's {@code OutOfBandQuery} applies for the same reason).
 */
@Component
public class CompaRatioDistributionQuery {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public CompaRatioDistributionQuery(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private static final String NO_BAND_COUNT_SQL = """
			SELECT count(*) AS n
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e ON e.id = c.employee_id
			  JOIN salary_schema.locations l ON l.id = e.location_id
			 WHERE c.compa_ratio IS NULL
			   AND (:departmentId::uuid IS NULL OR e.department_id = :departmentId::uuid)
			   AND (:jobLevelId::uuid  IS NULL OR e.job_level_id  = :jobLevelId::uuid)
			   AND (:countryCode::bpchar IS NULL OR l.country_code  = :countryCode::bpchar)
			""";

	private static final String QUARTILES_SQL = """
			SELECT count(*) AS n,
			       percentile_cont(0.25) WITHIN GROUP (ORDER BY c.compa_ratio) AS p25,
			       percentile_cont(0.50) WITHIN GROUP (ORDER BY c.compa_ratio) AS median,
			       percentile_cont(0.75) WITHIN GROUP (ORDER BY c.compa_ratio) AS p75
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e ON e.id = c.employee_id
			  JOIN salary_schema.locations l ON l.id = e.location_id
			 WHERE c.compa_ratio IS NOT NULL
			   AND (:departmentId::uuid IS NULL OR e.department_id = :departmentId::uuid)
			   AND (:jobLevelId::uuid  IS NULL OR e.job_level_id  = :jobLevelId::uuid)
			   AND (:countryCode::bpchar IS NULL OR l.country_code  = :countryCode::bpchar)
			""";

	private static final String HISTOGRAM_SQL = """
			SELECT CASE
			         WHEN c.compa_ratio < 0.80 THEN '<0.80'
			         WHEN c.compa_ratio < 0.90 THEN '0.80-0.90'
			         WHEN c.compa_ratio < 1.00 THEN '0.90-1.00'
			         WHEN c.compa_ratio < 1.10 THEN '1.00-1.10'
			         WHEN c.compa_ratio < 1.20 THEN '1.10-1.20'
			         ELSE '>=1.20'
			       END AS bucket,
			       count(*) AS n
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e ON e.id = c.employee_id
			  JOIN salary_schema.locations l ON l.id = e.location_id
			 WHERE c.compa_ratio IS NOT NULL
			   AND (:departmentId::uuid IS NULL OR e.department_id = :departmentId::uuid)
			   AND (:jobLevelId::uuid  IS NULL OR e.job_level_id  = :jobLevelId::uuid)
			   AND (:countryCode::bpchar IS NULL OR l.country_code  = :countryCode::bpchar)
			 GROUP BY bucket
			""";

	private static final String BY_DEPARTMENT_SQL = """
			SELECT d.id::text AS key, d.name AS label, count(*) AS n,
			       percentile_cont(0.5) WITHIN GROUP (ORDER BY c.compa_ratio) AS median
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e   ON e.id = c.employee_id
			  JOIN salary_schema.locations l   ON l.id = e.location_id
			  JOIN salary_schema.departments d ON d.id = e.department_id
			 WHERE c.compa_ratio IS NOT NULL
			   AND (:departmentId::uuid IS NULL OR e.department_id = :departmentId::uuid)
			   AND (:jobLevelId::uuid  IS NULL OR e.job_level_id  = :jobLevelId::uuid)
			   AND (:countryCode::bpchar IS NULL OR l.country_code  = :countryCode::bpchar)
			 GROUP BY d.id, d.name
			 ORDER BY d.name
			""";

	private static final String BY_LEVEL_SQL = """
			SELECT jl.id::text AS key, jl.title AS label, count(*) AS n,
			       percentile_cont(0.5) WITHIN GROUP (ORDER BY c.compa_ratio) AS median
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e   ON e.id = c.employee_id
			  JOIN salary_schema.locations l   ON l.id = e.location_id
			  JOIN salary_schema.job_levels jl ON jl.id = e.job_level_id
			 WHERE c.compa_ratio IS NOT NULL
			   AND (:departmentId::uuid IS NULL OR e.department_id = :departmentId::uuid)
			   AND (:jobLevelId::uuid  IS NULL OR e.job_level_id  = :jobLevelId::uuid)
			   AND (:countryCode::bpchar IS NULL OR l.country_code  = :countryCode::bpchar)
			 GROUP BY jl.id, jl.title, jl.sort_order
			 ORDER BY jl.sort_order
			""";

	private static final String BY_COUNTRY_SQL = """
			SELECT l.country_code AS key, co.name AS label, count(*) AS n,
			       percentile_cont(0.5) WITHIN GROUP (ORDER BY c.compa_ratio) AS median
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e  ON e.id = c.employee_id
			  JOIN salary_schema.locations l  ON l.id = e.location_id
			  JOIN salary_schema.countries co ON co.code = l.country_code
			 WHERE c.compa_ratio IS NOT NULL
			   AND (:departmentId::uuid IS NULL OR e.department_id = :departmentId::uuid)
			   AND (:jobLevelId::uuid  IS NULL OR e.job_level_id  = :jobLevelId::uuid)
			   AND (:countryCode::bpchar IS NULL OR l.country_code  = :countryCode::bpchar)
			 GROUP BY l.country_code, co.name
			 ORDER BY co.name
			""";

	public record Quartiles(int count, BigDecimal p25, BigDecimal median, BigDecimal p75) {
	}

	public int noBandCount(UUID departmentId, UUID jobLevelId, String countryCode) {
		Integer count = jdbcTemplate.queryForObject(NO_BAND_COUNT_SQL, params(departmentId, jobLevelId, countryCode), Integer.class);
		return count == null ? 0 : count;
	}

	public Quartiles quartiles(UUID departmentId, UUID jobLevelId, String countryCode) {
		return jdbcTemplate.queryForObject(QUARTILES_SQL, params(departmentId, jobLevelId, countryCode),
				(rs, rowNum) -> new Quartiles(rs.getInt("n"), rs.getBigDecimal("p25"), rs.getBigDecimal("median"), rs.getBigDecimal("p75")));
	}

	public List<CompaRatioHistogramBucket> histogram(UUID departmentId, UUID jobLevelId, String countryCode) {
		return jdbcTemplate.query(HISTOGRAM_SQL, params(departmentId, jobLevelId, countryCode),
				(rs, rowNum) -> new CompaRatioHistogramBucket(rs.getString("bucket"), rs.getInt("n")));
	}

	public List<CompaRatioGroupMedian> byDepartment(UUID departmentId, UUID jobLevelId, String countryCode) {
		return jdbcTemplate.query(BY_DEPARTMENT_SQL, params(departmentId, jobLevelId, countryCode), this::toGroupMedian);
	}

	public List<CompaRatioGroupMedian> byLevel(UUID departmentId, UUID jobLevelId, String countryCode) {
		return jdbcTemplate.query(BY_LEVEL_SQL, params(departmentId, jobLevelId, countryCode), this::toGroupMedian);
	}

	public List<CompaRatioGroupMedian> byCountry(UUID departmentId, UUID jobLevelId, String countryCode) {
		return jdbcTemplate.query(BY_COUNTRY_SQL, params(departmentId, jobLevelId, countryCode), this::toGroupMedian);
	}

	private CompaRatioGroupMedian toGroupMedian(ResultSet rs, int rowNum) throws SQLException {
		return new CompaRatioGroupMedian(rs.getString("key"), rs.getString("label"), rs.getInt("n"), rs.getBigDecimal("median"));
	}

	private SqlParameterSource params(UUID departmentId, UUID jobLevelId, String countryCode) {
		return new MapSqlParameterSource()
				.addValue("departmentId", departmentId)
				.addValue("jobLevelId", jobLevelId)
				.addValue("countryCode", countryCode);
	}

}
