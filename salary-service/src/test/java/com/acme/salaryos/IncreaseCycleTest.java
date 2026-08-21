package com.acme.salaryos;

import com.acme.salaryos.analytics.dto.IncreaseCycleResponse;
import com.acme.salaryos.analytics.service.AnalyticsService;
import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.change.dto.ChangeResponse;
import com.acme.salaryos.change.dto.ProposeChangeRequest;
import com.acme.salaryos.change.service.ChangeService;
import com.acme.salaryos.compensation.effective.ApplyCommand;
import com.acme.salaryos.compensation.effective.EffectiveDating;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.repository.EmployeeRepository;
import com.acme.salaryos.fx.FxRate;
import com.acme.salaryos.fx.FxRateRepository;
import com.acme.salaryos.reference.domain.Country;
import com.acme.salaryos.reference.domain.Department;
import com.acme.salaryos.reference.domain.JobFamily;
import com.acme.salaryos.reference.domain.JobLevel;
import com.acme.salaryos.reference.domain.Location;
import com.acme.salaryos.reference.repository.CountryRepository;
import com.acme.salaryos.reference.repository.DepartmentRepository;
import com.acme.salaryos.reference.repository.JobFamilyRepository;
import com.acme.salaryos.reference.repository.JobLevelRepository;
import com.acme.salaryos.reference.repository.LocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P7.5's own Verify clause: increase spend for the seeded cycle matches a SQL sum. Uses a
 * distinctive, otherwise-unused effective date (2045) — every other change-lifecycle test either
 * uses a fixed year in 2031-2039, or (uniquely) {@code ApplyDueChangesJobTest}, which uses the
 * REAL current date via the injected system {@link java.time.Clock} — so this range can't collide
 * with either.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class IncreaseCycleTest {

	@Autowired
	private AnalyticsService analyticsService;
	@Autowired
	private ChangeService changeService;
	@Autowired
	private EmployeeRepository employeeRepository;
	@Autowired
	private EffectiveDating effectiveDating;
	@Autowired
	private CountryRepository countryRepository;
	@Autowired
	private LocationRepository locationRepository;
	@Autowired
	private DepartmentRepository departmentRepository;
	@Autowired
	private JobFamilyRepository jobFamilyRepository;
	@Autowired
	private JobLevelRepository jobLevelRepository;
	@Autowired
	private FxRateRepository fxRateRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2045, 6, 15);
	private static final LocalDate FROM_DATE = LocalDate.of(2045, 1, 1);
	private static final LocalDate TO_DATE = LocalDate.of(2045, 12, 31);

	@Test
	void totalIncreaseSpendMatchesADirectSqlSumForTheSeededCycle() {
		Fixtures fx = seedFixtures("CYCLE");
		UUID employeeId = hireWithComp(fx, "E-CYCLE-1", new BigDecimal("100000"));

		ChangeResponse change = changeService.propose(new ProposeChangeRequest(
				employeeId, EFFECTIVE_DATE, new BigDecimal("115000"), "USD", "MERIT", null, null), fx.proposerId());
		changeService.submit(change.id());
		changeService.approve(change.id(), fx.approverId(), "Approved.");
		changeService.applyDueChange(change.id(), EFFECTIVE_DATE);

		IncreaseCycleResponse response = analyticsService.increaseCycle(FROM_DATE, TO_DATE, null);

		// Independent direct SQL cross-check, scoped to this test's own employee -- the shared
		// container may carry other APPLIED changes too, so this never asserts an unscoped total.
		BigDecimal directDelta = jdbcTemplate.queryForObject(
				"""
				SELECT cr.normalized_annual_base - cc.current_base_amount
				         * (cr.normalized_annual_base / cr.annual_base_amount)
				  FROM salary_schema.compensation_changes cc
				  JOIN salary_schema.compensation_records cr ON cr.id = cc.applied_record_id
				 WHERE cc.id = ?
				""", BigDecimal.class, change.id());
		assertThat(directDelta).isEqualByComparingTo("15000.00"); // USD 1:1, so this IS the normalized delta

		assertThat(response.totalIncrease().amount()).isGreaterThanOrEqualTo(directDelta);
		assertThat(response.population().headcount()).isGreaterThanOrEqualTo(1);

		var meritRow = response.byReason().stream().filter(r -> "MERIT".equals(r.reasonCode())).findFirst().orElseThrow();
		assertThat(meritRow.count()).isGreaterThanOrEqualTo(1);
		assertThat(meritRow.totalIncrease().amount()).isGreaterThanOrEqualTo(directDelta);

		assertThat(response.fromDate()).isEqualTo(FROM_DATE);
		assertThat(response.toDate()).isEqualTo(TO_DATE);
		assertThat(response.baseCurrency()).isEqualTo("USD");
		assertThat(response.budget()).isNull();
		assertThat(response.budgetBurnPercent()).isNull();
	}

	@Test
	void aChangeThatIsOnlyApprovedNeverCountsAsSpendUntilItIsActuallyApplied() {
		Fixtures fx = seedFixtures("CYCLENOTAPPLIED");
		UUID employeeId = hireWithComp(fx, "E-CYCLENOTAPPLIED-1", new BigDecimal("100000"));

		ChangeResponse change = changeService.propose(new ProposeChangeRequest(
				employeeId, EFFECTIVE_DATE, new BigDecimal("999999"), "USD", "MERIT", null, null), fx.proposerId());
		changeService.submit(change.id());
		changeService.approve(change.id(), fx.approverId(), "Approved."); // APPROVED, never applied

		IncreaseCycleResponse response = analyticsService.increaseCycle(FROM_DATE, TO_DATE, null);

		// A merely-APPROVED ~900000 jump would dominate any total-increase figure if it leaked in --
		// confirm the response's total is nowhere near it (a promise, not spend, CLAUDE.md §8).
		assertThat(response.totalIncrease().amount()).isLessThan(new BigDecimal("899999"));
	}

	private UUID hireWithComp(Fixtures fx, String employeeNumber, BigDecimal amount) {
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber(employeeNumber)
				.firstName("First").lastName("Last")
				.workEmail(employeeNumber.toLowerCase() + "@acme.test")
				.departmentId(fx.departmentId()).locationId(fx.locationId())
				.jobFamilyId(fx.jobFamilyId()).jobLevelId(fx.jobLevelId())
				.hireDate(LocalDate.of(2020, 1, 1)).employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());
		effectiveDating.apply(new ApplyCommand(
				employee.getId(), LocalDate.of(2020, 1, 1), amount, "USD", "ANNUAL", "INITIAL", null, fx.proposerId()));
		return employee.getId();
	}

	private record Fixtures(UUID departmentId, UUID locationId, UUID jobFamilyId, UUID jobLevelId, UUID proposerId, UUID approverId) {
	}

	private Fixtures seedFixtures(String suffix) {
		countryRepository.findById("US").orElseGet(() -> countryRepository.save(
				Country.builder().code("US").name("United States").defaultCurrency("USD").build()));
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ ANALYTICS-" + suffix).build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering ANALYTICS-" + suffix).code("DEPT-ANALYTICS-" + suffix).build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-ANALYTICS-" + suffix).build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer ANALYTICS-" + suffix).sortOrder(4).build());
		User proposer = userRepository.save(User.builder()
				.email("proposer-cyc-" + suffix.toLowerCase() + "@acme.test")
				.fullName("Proposer").passwordHash("{argon2}stub").role("HR_MANAGER")
				.build());
		User approver = userRepository.save(User.builder()
				.email("approver-cyc-" + suffix.toLowerCase() + "@acme.test")
				.fullName("Approver").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());

		seedRateIfMissing(LocalDate.of(2020, 1, 1));
		seedRateIfMissing(LocalDate.of(2045, 6, 1));

		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), proposer.getId(), approver.getId());
	}

	private void seedRateIfMissing(LocalDate rateMonth) {
		fxRateRepository.findByBaseCurrencyAndQuoteCurrencyAndRateMonth("USD", "USD", rateMonth)
				.orElseGet(() -> fxRateRepository.save(FxRate.builder()
						.rateMonth(rateMonth).baseCurrency("USD").quoteCurrency("USD").rate(BigDecimal.ONE)
						.build()));
	}

}
