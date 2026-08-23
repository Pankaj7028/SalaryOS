package com.acme.salaryos;

import com.acme.salaryos.analytics.dto.CompaRatioDistributionResponse;
import com.acme.salaryos.analytics.dto.FxBasis;
import com.acme.salaryos.analytics.dto.HeadcountResponse;
import com.acme.salaryos.analytics.query.FxBasisQuery;
import com.acme.salaryos.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P10.1's Verify clause. FR-6.8 wants every analytics response to state its FX basis;
 * {@link FxBasis} reports it as a span because an aggregate over many employees has no single
 * governing rate month (CLAUDE.md §6.4).
 *
 * <p>The shared Testcontainers container accumulates rows from every other test class, so nothing
 * here asserts an absolute count. Each assertion compares the service's own answer against an
 * independent SQL query over the identical join — self-consistent whatever else is in the
 * database, which is the only shape of assertion that survives a shared container.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class FxBasisTest {

	@Autowired
	private AnalyticsService analyticsService;
	@Autowired
	private FxBasisQuery fxBasisQuery;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static final String DIRECT_SQL = """
			SELECT count(DISTINCT r.fx_rate_id)  AS rates,
			       count(DISTINCT f.rate_month)  AS months
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.compensation_records r ON r.id = c.compensation_record_id
			  JOIN salary_schema.fx_rates f             ON f.id = r.fx_rate_id
			""";

	@Test
	void payrollCostFxBasisMatchesDirectSql() {
		FxBasis basis = analyticsService.payrollCost().fxBasis();

		var expected = jdbcTemplate.queryForMap(DIRECT_SQL);
		assertThat(basis.distinctRates()).isEqualTo(((Number) expected.get("rates")).intValue());
		assertThat(basis.monthsSpanned()).isEqualTo(((Number) expected.get("months")).intValue());
	}

	/**
	 * The three current-comp reports draw from one population, so they must agree exactly. If they
	 * ever diverge, one of them has started scoping its own figures differently — which would make
	 * its stated basis a lie rather than merely imprecise.
	 */
	@Test
	void allCurrentCompReportsAgreeOnTheirBasis() {
		FxBasis payrollCost = analyticsService.payrollCost().fxBasis();
		FxBasis outOfBand = analyticsService.outOfBand().fxBasis();
		FxBasis payGap = analyticsService.payGap().fxBasis();

		assertThat(outOfBand).isEqualTo(payrollCost);
		assertThat(payGap).isEqualTo(payrollCost);
	}

	/** The span has to be a real, ordered interval, not two unrelated dates. */
	@Test
	void spanIsOrderedAndCoversAtLeastAsManyMonthsAsItHasRates() {
		FxBasis basis = analyticsService.payrollCost().fxBasis();

		if (basis.distinctRates() > 0) {
			assertThat(basis.earliestMonth()).isNotNull();
			assertThat(basis.latestMonth()).isNotNull();
			assertThat(basis.earliestMonth()).isBeforeOrEqualTo(basis.latestMonth());
			assertThat(basis.monthsSpanned()).isLessThanOrEqualTo(basis.distinctRates());
			assertThat(basis.earliestMonth().getDayOfMonth()).isEqualTo(1);
		}
	}

	/**
	 * An empty population is a real state, not an error — a date window with no applied changes
	 * aggregates to {@code count = 0} with {@code NULL} min/max. This must come back as an absent
	 * span rather than throwing on a null date.
	 */
	@Test
	void emptyPopulationYieldsAnAbsentSpanRatherThanFailing() {
		FxBasis basis = fxBasisQuery.forAppliedChanges(LocalDate.of(1990, 1, 1), LocalDate.of(1990, 12, 31));

		assertThat(basis.distinctRates()).isZero();
		assertThat(basis.monthsSpanned()).isZero();
		assertThat(basis.earliestMonth()).isNull();
		assertThat(basis.latestMonth()).isNull();
	}

	/**
	 * The scope correction made during P10.1, pinned so a later step cannot quietly undo it.
	 * Headcount carries no money at all; compa-ratio is pay ÷ band mid with both sides already in
	 * the same currency. Neither has an FX basis, and giving them one would fabricate a basis for a
	 * figure that has none — the exact mistake the original {@code null} was avoiding.
	 */
	@Test
	void responsesWithoutMoneyCarryNoFxBasis() {
		assertThat(componentNames(HeadcountResponse.class)).doesNotContain("fxBasis");
		assertThat(componentNames(CompaRatioDistributionResponse.class)).doesNotContain("fxBasis");
	}

	private static String[] componentNames(Class<?> record) {
		return Arrays.stream(record.getRecordComponents())
				.map(RecordComponent::getName)
				.toArray(String[]::new);
	}

}
