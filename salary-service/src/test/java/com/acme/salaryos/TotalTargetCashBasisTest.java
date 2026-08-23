package com.acme.salaryos;

import com.acme.salaryos.analytics.dto.AnalyticsBasis;
import com.acme.salaryos.analytics.dto.PayrollCostResponse;
import com.acme.salaryos.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P10.6's Verify clause. FR-3.4 stores recurring components and says each is included in total
 * target cash; before this step no analytic read them, so "what do we spend on pay" answered only
 * base.
 *
 * <p>Assertions reconcile the service against independent SQL over the identical joins rather than
 * asserting absolute figures — the shared Testcontainers container carries every other test's rows.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class TotalTargetCashBasisTest {

	@Autowired
	private AnalyticsService analyticsService;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static final String BASE_TOTAL_SQL = """
			SELECT coalesce(sum(c.normalized_annual_base), 0)
			  FROM salary_schema.employee_current_comp c
			""";

	private static final String COMPONENT_TOTAL_SQL = """
			SELECT coalesce(sum(cc.amount * ccf.rate), 0)
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.compensation_records r        ON r.id = c.compensation_record_id
			  JOIN salary_schema.fx_rates rf                   ON rf.id = r.fx_rate_id
			  JOIN salary_schema.compensation_components cc    ON cc.compensation_record_id = c.compensation_record_id
			  JOIN salary_schema.fx_rates ccf
			    ON ccf.base_currency  = cc.currency
			   AND ccf.quote_currency = rf.quote_currency
			   AND ccf.rate_month     = rf.rate_month
			 WHERE cc.is_recurring
			""";

	/** BASE must be byte-identical to what this endpoint returned before P10.6 existed. */
	@Test
	void baseBasisMatchesADirectSumOfNormalisedBase() {
		BigDecimal total = analyticsService.payrollCost(AnalyticsBasis.BASE).overall().total().amount();

		assertThat(total).isEqualByComparingTo(jdbcTemplate.queryForObject(BASE_TOTAL_SQL, BigDecimal.class));
	}

	/** Total target cash = base + recurring components, each at its own record's pinned rate. */
	@Test
	void totalTargetCashEqualsBasePlusNormalisedRecurringComponents() {
		BigDecimal totalCash = analyticsService.payrollCost(AnalyticsBasis.TOTAL_TARGET_CASH)
				.overall().total().amount();

		BigDecimal expected = jdbcTemplate.queryForObject(BASE_TOTAL_SQL, BigDecimal.class)
				.add(jdbcTemplate.queryForObject(COMPONENT_TOTAL_SQL, BigDecimal.class));

		assertThat(totalCash).isEqualByComparingTo(expected);
	}

	/**
	 * Components can only add, never subtract — if total cash ever came in below base, the LATERAL
	 * join would be dropping employees rather than adding their components.
	 */
	@Test
	void totalTargetCashIsNeverBelowBaseAndCountsTheSamePeople() {
		PayrollCostResponse base = analyticsService.payrollCost(AnalyticsBasis.BASE);
		PayrollCostResponse cash = analyticsService.payrollCost(AnalyticsBasis.TOTAL_TARGET_CASH);

		assertThat(cash.overall().total().amount())
				.isGreaterThanOrEqualTo(base.overall().total().amount());
		assertThat(cash.overall().headcount()).isEqualTo(base.overall().headcount());
		assertThat(cash.population().headcount()).isEqualTo(base.population().headcount());
	}

	/** FR-6.8: a figure has to say what it counts, or the two bases are indistinguishable downstream. */
	@Test
	void theResponseStatesItsOwnBasis() {
		assertThat(analyticsService.payrollCost(AnalyticsBasis.BASE).basis())
				.isEqualTo(AnalyticsBasis.BASE);
		assertThat(analyticsService.payrollCost(AnalyticsBasis.TOTAL_TARGET_CASH).basis())
				.isEqualTo(AnalyticsBasis.TOTAL_TARGET_CASH);
	}

	/** Every breakdown must reconcile to `overall` on both bases — the response promises they agree. */
	@Test
	void breakdownsReconcileToOverallOnBothBases() {
		for (AnalyticsBasis basis : AnalyticsBasis.values()) {
			PayrollCostResponse response = analyticsService.payrollCost(basis);
			BigDecimal overall = response.overall().total().amount();

			assertThat(sum(response, "country")).as("byCountry on %s", basis).isEqualByComparingTo(overall);
			assertThat(sum(response, "department")).as("byDepartment on %s", basis).isEqualByComparingTo(overall);
			assertThat(sum(response, "level")).as("byLevel on %s", basis).isEqualByComparingTo(overall);
		}
	}

	private static BigDecimal sum(PayrollCostResponse response, String dimension) {
		var rows = switch (dimension) {
			case "country" -> response.byCountry();
			case "department" -> response.byDepartment();
			default -> response.byLevel();
		};
		return rows.stream().map(group -> group.total().amount())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

}
