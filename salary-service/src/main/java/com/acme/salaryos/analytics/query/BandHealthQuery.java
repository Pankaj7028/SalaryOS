package com.acme.salaryos.analytics.query;

import com.acme.salaryos.analytics.dto.BandHealthRow;
import com.acme.salaryos.common.money.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * P11.3. Diagnostics on the band structure itself.
 *
 * <p>Adjacency is per <strong>job family and country</strong>, not per level code alone —
 * {@code job_levels} are scoped to a family ({@code UNIQUE (job_family_id, level_code)}), so "the
 * level below this one" only means anything within one family, and comparing an L4 Engineering band
 * to an L3 Finance band would produce a progression figure about nothing.
 *
 * <p>Only in-force bands ({@code effective_to IS NULL}) are judged. A superseded version is history
 * and its spread is not a live problem.
 *
 * <p>Every figure stays in the band's own currency — no normalisation, so no FX basis applies.
 */
@Component
public class BandHealthQuery {

	private final JdbcTemplate jdbcTemplate;

	public BandHealthQuery(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * {@code lag()} over (family, country) ordered by the level's own {@code sort_order} gives each
	 * band the one below it. The incumbent stats come from a LATERAL against the projection, which
	 * already excludes terminated employees.
	 */
	private static final String BAND_HEALTH_SQL = """
			WITH in_force AS (
			    SELECT b.id, b.country_code, b.currency,
			           b.min_amount, b.mid_amount, b.max_amount, b.effective_from,
			           jl.level_code, jl.title AS level_title, jl.sort_order, jl.job_family_id,
			           jf.name AS family_name, co.name AS country_name
			      FROM salary_schema.salary_bands b
			      JOIN salary_schema.job_levels jl   ON jl.id = b.job_level_id
			      JOIN salary_schema.job_families jf ON jf.id = jl.job_family_id
			      JOIN salary_schema.countries co    ON co.code = b.country_code
			     WHERE b.effective_to IS NULL
			),
			with_previous AS (
			    SELECT f.*,
			           lag(f.mid_amount) OVER w AS previous_mid,
			           lag(f.max_amount) OVER w AS previous_max
			      FROM in_force f
			    WINDOW w AS (PARTITION BY f.job_family_id, f.country_code ORDER BY f.sort_order)
			)
			SELECT p.id, p.family_name, p.level_code, p.level_title, p.country_code, p.country_name,
			       p.currency, p.min_amount, p.mid_amount, p.max_amount,
			       p.previous_mid, p.previous_max,
			       (EXTRACT(YEAR FROM age(current_date, p.effective_from)) * 12
			        + EXTRACT(MONTH FROM age(current_date, p.effective_from)))::int AS months_since_versioned,
			       coalesce(stats.incumbents, 0) AS incumbents,
			       stats.median_compa_ratio
			  FROM with_previous p
			  LEFT JOIN LATERAL (
			      SELECT count(*) AS incumbents,
			             percentile_cont(0.5) WITHIN GROUP (ORDER BY c.compa_ratio) AS median_compa_ratio
			        FROM salary_schema.employee_current_comp c
			       WHERE c.band_id = p.id
			  ) stats ON true
			 ORDER BY p.family_name, p.country_name, p.sort_order
			""";

	public List<BandHealthRow> rows() {
		return jdbcTemplate.query(BAND_HEALTH_SQL, BandHealthQuery::toRow);
	}

	private static BandHealthRow toRow(ResultSet rs, int rowNum) throws SQLException {
		String currency = rs.getString("currency");
		BigDecimal min = rs.getBigDecimal("min_amount");
		BigDecimal mid = rs.getBigDecimal("mid_amount");
		BigDecimal max = rs.getBigDecimal("max_amount");
		BigDecimal previousMid = rs.getBigDecimal("previous_mid");
		BigDecimal previousMax = rs.getBigDecimal("previous_max");

		return new BandHealthRow(
				UUID.fromString(rs.getString("id")),
				rs.getString("family_name"),
				rs.getString("level_code"),
				rs.getString("level_title"),
				rs.getString("country_code"),
				rs.getString("country_name"),
				new Money(min, currency),
				new Money(mid, currency),
				new Money(max, currency),
				ratio(max, min),
				previousMid == null ? null : ratio(mid, previousMid),
				previousMax != null && previousMax.compareTo(min) < 0,
				rs.getInt("incumbents"),
				rs.getBigDecimal("median_compa_ratio"),
				rs.getInt("months_since_versioned"));
	}

	/**
	 * {@code numerator/denominator - 1}, i.e. spread or progression as a proportion. The
	 * {@code min_amount > 0} CHECK on the table means the divisor cannot be zero, so no guard is
	 * needed — and adding one would suggest the constraint is not trusted.
	 */
	private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
		return numerator.divide(denominator, 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
	}

}
