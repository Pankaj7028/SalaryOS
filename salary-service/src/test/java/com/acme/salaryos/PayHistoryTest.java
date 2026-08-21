package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.compensation.domain.CompensationRecord;
import com.acme.salaryos.compensation.effective.ApplyCommand;
import com.acme.salaryos.compensation.effective.EffectiveDating;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.dto.CompensationRecordResponse;
import com.acme.salaryos.employee.repository.EmployeeRepository;
import com.acme.salaryos.employee.service.EmployeeService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P5.4 — FR-3.6/FR-6.7: the full pay-history ledger, and "what was this person paid on a chosen
 * date" via {@code as-at}. BuildPlan's Verify names "a sample of 50 seeded employees"; `P9`'s
 * `SeedRunner` doesn't exist yet, so this proves the same `as-at` correctness — including the
 * exact day-boundary the whole ledger design rests on — over a small, hand-built history instead.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class PayHistoryTest {

	@Autowired
	private EffectiveDating effectiveDating;
	@Autowired
	private EmployeeService employeeService;
	@Autowired
	private EmployeeRepository employeeRepository;
	@Autowired
	private FxRateRepository fxRateRepository;
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
	private UserRepository userRepository;

	@Test
	void asAtReturnsTheOnePeriodInForceOnTheChosenDate() {
		Fixtures fx = seedFixtures("ASAT");
		saveUsdRate(2034, 1);
		saveUsdRate(2034, 7);

		CompensationRecord first = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2034, 1, 1), new BigDecimal("100000"), "USD", "ANNUAL",
				"INITIAL", null, fx.userId()));
		CompensationRecord raise = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2034, 7, 1), new BigDecimal("110000"), "USD", "ANNUAL",
				"MERIT", null, fx.userId()));

		// Before either period existed: no answer.
		assertThat(employeeService.compensationAsAt(fx.employeeId(), LocalDate.of(2033, 12, 31))).isEmpty();

		// The exact day the first period is in force, and the exact last day before the raise.
		assertThat(employeeService.compensationAsAt(fx.employeeId(), LocalDate.of(2034, 1, 1)))
				.map(CompensationRecordResponse::id).contains(first.getId());
		assertThat(employeeService.compensationAsAt(fx.employeeId(), LocalDate.of(2034, 6, 30)))
				.map(CompensationRecordResponse::id).contains(first.getId());

		// The exact day the raise takes over, and any day after.
		assertThat(employeeService.compensationAsAt(fx.employeeId(), LocalDate.of(2034, 7, 1)))
				.map(CompensationRecordResponse::id).contains(raise.getId());
		assertThat(employeeService.compensationAsAt(fx.employeeId(), LocalDate.of(2035, 1, 1)))
				.map(CompensationRecordResponse::id).contains(raise.getId());
	}

	@Test
	void historyReturnsEveryPeriodNewestFirstIncludingASupersededOne() {
		Fixtures fx = seedFixtures("HISTORY");
		saveUsdRate(2034, 1);
		saveUsdRate(2034, 6);

		CompensationRecord initial = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2034, 1, 1), new BigDecimal("90000"), "USD", "ANNUAL",
				"INITIAL", null, fx.userId()));
		CompensationRecord raise = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2034, 6, 1), new BigDecimal("100000"), "USD", "ANNUAL",
				"MERIT", null, fx.userId()));

		List<CompensationRecordResponse> history = employeeService.compensationHistory(fx.employeeId());

		assertThat(history).hasSize(2);
		assertThat(history.get(0).id()).isEqualTo(raise.getId());
		assertThat(history.get(0).effectiveTo()).isNull();
		assertThat(history.get(1).id()).isEqualTo(initial.getId());
		assertThat(history.get(1).effectiveTo()).isEqualTo(LocalDate.of(2034, 6, 1));
		assertThat(history.get(1).changeReason()).isEqualTo("INITIAL");
		assertThat(history.get(0).changeReason()).isEqualTo("MERIT");
	}

	@Test
	void compensationQueriesOnAnUnknownEmployeeAreNotFound() {
		UUID unknown = UUID.randomUUID();
		assertThatThrownBy(() -> employeeService.compensationHistory(unknown)).isInstanceOf(NoSuchElementException.class);
		assertThatThrownBy(() -> employeeService.compensationAsAt(unknown, LocalDate.of(2034, 1, 1)))
				.isInstanceOf(NoSuchElementException.class);
	}

	private void saveUsdRate(int year, int month) {
		LocalDate rateMonth = LocalDate.of(year, month, 1);
		fxRateRepository.findByBaseCurrencyAndQuoteCurrencyAndRateMonth("USD", "USD", rateMonth)
				.orElseGet(() -> fxRateRepository.save(FxRate.builder()
						.rateMonth(rateMonth).baseCurrency("USD").quoteCurrency("USD").rate(BigDecimal.ONE)
						.build()));
	}

	private record Fixtures(UUID employeeId, UUID userId) {
	}

	private Fixtures seedFixtures(String suffix) {
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ " + suffix).build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering").code("DEPT-PH-" + suffix).build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-PH-" + suffix).build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer PH-" + suffix).sortOrder(4).build());
		User user = userRepository.save(User.builder()
				.email("hr-admin-ph-" + suffix.toLowerCase() + "@acme.test")
				.fullName("HR Admin").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-PH-" + suffix)
				.firstName("First").lastName("Last")
				.workEmail("ph-" + suffix.toLowerCase() + "@acme.test")
				.departmentId(department.getId()).locationId(location.getId())
				.jobFamilyId(family.getId()).jobLevelId(level.getId())
				.hireDate(LocalDate.of(2020, 1, 1)).employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());
		return new Fixtures(employee.getId(), user.getId());
	}

}
