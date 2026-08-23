package com.acme.salaryos;

import com.acme.salaryos.fx.dto.FxCoverageResponse;
import com.acme.salaryos.fx.service.FxRateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P10.2's Verify. The coverage matrix must show exactly the currencies {@code employee_current_comp}
 * actually pays in, and each cell's covered flag must agree with whether the rate row exists.
 *
 * <p>Like every class on the shared Testcontainers container, assertions reconcile the service
 * against independent SQL rather than absolute counts — except the flip scenario, which seeds its
 * own in-use currency so it owns every row it touches (the vacuous-pass lesson from BandHealthTest,
 * applied here from the start).
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class FxCoverageTest {

	/** Obscure code — never a country default in any test fixture ('ZZ'/'QX'/'QM' are taken). */
	private static final String OWN_CURRENCY = "XWJ";
	/**
	 * This class's own country. NOT 'ZZ' (V2ReferenceDataMigrationTest's deliberately-invalid FK
	 * sentinel — creating it for real makes that test's insert succeed and fail), and not 'QX'/'QM',
	 * which BandHealthTest and MarketDataImportTest already own. `countries` has no migration-seeded
	 * rows at all, so a test needing one must create it, and whatever code it picks becomes valid for
	 * every class sharing this container. Default currency stays USD, not OWN_CURRENCY, so this row
	 * cannot widen the chip list `missingMonths()` builds from every country default.
	 */
	private static final String OWN_COUNTRY = "QW";

	@Autowired
	private FxRateService fxRateService;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void rowsAreExactlyTheCurrenciesInUseWithTheirHeadcounts() {
		FxCoverageResponse coverage = fxRateService.coverage();

		Map<String, Long> expected = jdbcTemplate.query(
				"SELECT currency, count(*) AS n FROM salary_schema.employee_current_comp GROUP BY currency",
				(rs, i) -> Map.entry(rs.getString("currency").trim(), rs.getLong("n")))
				.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		if (expected.isEmpty()) {
			seedInUseCurrency();
			expected = jdbcTemplate.query(
					"SELECT currency, count(*) AS n FROM salary_schema.employee_current_comp WHERE currency = ? GROUP BY currency",
					(rs, i) -> Map.entry(rs.getString("currency").trim(), rs.getLong("n")),
					OWN_CURRENCY)
					.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			coverage = fxRateService.coverage();
		}

		assertThat(coverage.rows())
				.isNotEmpty()
				.extracting(r -> r.currency().trim())
				.containsExactlyInAnyOrderElementsOf(expected.keySet());
		for (var row : coverage.rows()) {
			assertThat(row.employeeCount())
					.as("headcount for %s", row.currency())
					.isEqualTo(expected.get(row.currency().trim()));
		}
	}

	@Test
	void windowMatchesTheChipListWindowAndEveryCellReflectsItsRateRow() {
		FxCoverageResponse coverage = fxRateService.coverage();

		LocalDate current = LocalDate.now().withDayOfMonth(1);
		assertThat(coverage.months()).hasSize(16);
		assertThat(coverage.months().get(0)).isEqualTo(current.minusMonths(12));
		assertThat(coverage.months().get(coverage.months().size() - 1)).isEqualTo(current.plusMonths(3));

		var pinned = jdbcTemplate.query(
				"SELECT base_currency, rate_month FROM salary_schema.fx_rates WHERE quote_currency = 'USD'",
				(rs, i) -> rs.getString("base_currency").trim() + "|" + rs.getDate("rate_month").toLocalDate());

		for (var row : coverage.rows()) {
			assertThat(row.cells()).isNotEmpty();
			for (var cell : row.cells()) {
				boolean exists = pinned.contains(row.currency().trim() + "|" + cell.month());
				assertThat(cell.covered())
						.as("%s @ %s", row.currency(), cell.month())
						.isEqualTo(exists);
			}
		}
	}

	/**
	 * The step's own scenario — a missing month for an in-use currency shows as an uncovered cell —
	 * made deterministic: seed one employee paid in {@link #OWN_CURRENCY}, then flip a month that no
	 * ledger record pins (so the FK on {@code compensation_records.fx_rate_id} is never in play).
	 */
	@Test
	void addingAndDeletingARateFlipsItsCellForAnInUseCurrency() {
		seedInUseCurrency();

		LocalDate probeMonth = LocalDate.now().withDayOfMonth(1).plusMonths(3);
		jdbcTemplate.update("DELETE FROM salary_schema.fx_rates WHERE base_currency = ? AND quote_currency = 'USD' "
				+ "AND rate_month = ? AND NOT EXISTS (SELECT 1 FROM salary_schema.compensation_records r WHERE r.fx_rate_id = fx_rates.id)",
				OWN_CURRENCY, probeMonth);

		assertThat(cell(fxRateService.coverage(), probeMonth)).isFalse();

		UUID id = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO salary_schema.fx_rates (id, rate_month, base_currency, quote_currency, rate) "
				+ "VALUES (?, ?, ?, 'USD', 1)", id, probeMonth, OWN_CURRENCY);
		assertThat(cell(fxRateService.coverage(), probeMonth)).isTrue();

		jdbcTemplate.update("DELETE FROM salary_schema.fx_rates WHERE id = ?", id);
		assertThat(cell(fxRateService.coverage(), probeMonth)).isFalse();
	}

	private Boolean cell(FxCoverageResponse coverage, LocalDate month) {
		return coverage.rows().stream()
				.filter(r -> r.currency().trim().equals(OWN_CURRENCY))
				.findFirst()
				.orElseThrow()
				.cells().stream()
				.filter(c -> c.month().equals(month))
				.findFirst()
				.orElseThrow()
				.covered();
	}

	/**
	 * One employee paid in {@link #OWN_CURRENCY} — the minimum chain that makes a currency "in use":
	 * country → location → job family → job level → employee → ledger row → projection row.
	 *
	 * <p>Idempotent end to end, because both test methods call it against the same shared container.
	 * `locations` and `compensation_records` have no unique key to hang an ON CONFLICT off, so those
	 * two are guarded with NOT EXISTS instead — a second `locations` row named the same thing made
	 * the lookup below ambiguous, and a second open-ended ledger row would trip `comp_no_overlap`.
	 */
	private void seedInUseCurrency() {
		jdbcTemplate.update(
				"INSERT INTO salary_schema.users (id, email, full_name, password_hash, role) "
						+ "VALUES (?, 'fx-cov@acme.test', 'FX Coverage', '{argon2}stub', 'HR_ADMIN') ON CONFLICT (email) DO NOTHING",
				UUID.randomUUID());
		UUID createdBy = jdbcTemplate.queryForObject(
				"SELECT id FROM salary_schema.users WHERE email = 'fx-cov@acme.test'", UUID.class);

		jdbcTemplate.update("INSERT INTO salary_schema.countries (code, name, default_currency) "
				+ "VALUES (?, 'Fx Coverage Land', 'USD') ON CONFLICT (code) DO NOTHING", OWN_COUNTRY);

		jdbcTemplate.update("INSERT INTO salary_schema.departments (id, name, code) "
				+ "VALUES (gen_random_uuid(), 'Engineering', 'ENG-FXCOV') ON CONFLICT (code) DO NOTHING");
		UUID departmentId = jdbcTemplate.queryForObject(
				"SELECT id FROM salary_schema.departments WHERE code = 'ENG-FXCOV'", UUID.class);

		jdbcTemplate.update("INSERT INTO salary_schema.locations (id, country_code, city, name) "
				+ "SELECT gen_random_uuid(), ?, 'Austin', 'Austin HQ Fx' WHERE NOT EXISTS "
				+ "(SELECT 1 FROM salary_schema.locations WHERE name = 'Austin HQ Fx')", OWN_COUNTRY);
		UUID locationId = jdbcTemplate.queryForObject(
				"SELECT id FROM salary_schema.locations WHERE name = 'Austin HQ Fx'", UUID.class);

		jdbcTemplate.update("INSERT INTO salary_schema.job_families (id, name, code) "
				+ "VALUES (gen_random_uuid(), 'Fx Coverage', 'FXCOV') ON CONFLICT (code) DO NOTHING");
		UUID jobFamilyId = jdbcTemplate.queryForObject(
				"SELECT id FROM salary_schema.job_families WHERE code = 'FXCOV'", UUID.class);

		jdbcTemplate.update("INSERT INTO salary_schema.job_levels (id, job_family_id, level_code, title, sort_order) "
				+ "VALUES (gen_random_uuid(), ?, 'L3', 'Fx Coverage L3', 3) "
				+ "ON CONFLICT (job_family_id, level_code) DO NOTHING", jobFamilyId);
		UUID jobLevelId = jdbcTemplate.queryForObject(
				"SELECT id FROM salary_schema.job_levels WHERE job_family_id = ? AND level_code = 'L3'",
				UUID.class, jobFamilyId);

		jdbcTemplate.update(
				"INSERT INTO salary_schema.employees "
						+ "(id, employee_number, first_name, last_name, work_email, department_id, location_id, "
						+ " job_family_id, job_level_id, hire_date, employment_type, fte) "
						+ "VALUES (gen_random_uuid(), 'E-FXCOV-1', 'Fx', 'Coverage', 'fx-cov-emp@acme.test', ?, ?, "
						+ " ?, ?, current_date, 'FULL_TIME', 1.00) ON CONFLICT (employee_number) DO NOTHING",
				departmentId, locationId, jobFamilyId, jobLevelId);
		UUID employeeId = jdbcTemplate.queryForObject(
				"SELECT id FROM salary_schema.employees WHERE employee_number = 'E-FXCOV-1'", UUID.class);

		jdbcTemplate.update("INSERT INTO salary_schema.fx_rates (id, rate_month, base_currency, quote_currency, rate) "
				+ "VALUES (gen_random_uuid(), date_trunc('month', current_date)::date, ?, 'USD', 1) "
				+ "ON CONFLICT (rate_month, base_currency, quote_currency) DO NOTHING", OWN_CURRENCY);
		UUID fxRateId = jdbcTemplate.queryForObject(
				"SELECT id FROM salary_schema.fx_rates WHERE base_currency = ? AND quote_currency = 'USD' "
						+ "AND rate_month = date_trunc('month', current_date)::date",
				UUID.class, OWN_CURRENCY);

		jdbcTemplate.update(
				"INSERT INTO salary_schema.compensation_records "
						+ "(id, employee_id, effective_from, base_amount, currency, pay_frequency, "
						+ " annual_base_amount, normalized_annual_base, base_currency, fx_rate_id, change_reason, created_by) "
						+ "SELECT gen_random_uuid(), ?, date_trunc('month', current_date)::date, 100000, ?, 'ANNUAL', "
						+ " 100000, 100000, 'USD', ?, 'INITIAL', ? WHERE NOT EXISTS "
						+ "(SELECT 1 FROM salary_schema.compensation_records WHERE employee_id = ?)",
				employeeId, OWN_CURRENCY, fxRateId, createdBy, employeeId);
		UUID recordId = jdbcTemplate.queryForObject(
				"SELECT id FROM salary_schema.compensation_records WHERE employee_id = ?", UUID.class, employeeId);

		jdbcTemplate.update(
				"INSERT INTO salary_schema.employee_current_comp "
						+ "(employee_id, compensation_record_id, base_amount, currency, annual_base_amount, "
						+ " normalized_annual_base, band_id, compa_ratio, range_penetration, band_status) "
						+ "VALUES (?, ?, 100000, ?, 100000, 100000, NULL, NULL, NULL, 'NO_BAND') "
						+ "ON CONFLICT (employee_id) DO NOTHING",
				employeeId, recordId, OWN_CURRENCY);

		Integer projected = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM salary_schema.employee_current_comp WHERE currency = ?",
				Integer.class, OWN_CURRENCY);
		assertThat(projected).as("exactly one projection row is paid in %s", OWN_CURRENCY).isEqualTo(1);
	}

}
