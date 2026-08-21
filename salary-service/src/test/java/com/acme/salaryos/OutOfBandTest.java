package com.acme.salaryos;

import com.acme.salaryos.analytics.dto.OutOfBandResponse;
import com.acme.salaryos.analytics.dto.OutOfBandRow;
import com.acme.salaryos.analytics.service.AnalyticsService;
import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P7.2's own Verify clause: count matches the seeded anomaly count exactly. Same shared-container
 * scoping discipline as {@code PayrollCostAndHeadcountTest} — {@code /analytics/out-of-band} has
 * no per-department filter, so this filters the response's {@code rows} down to this test's own
 * employee numbers rather than asserting an unscoped global count or total.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class OutOfBandTest {

	@Autowired
	private AnalyticsService analyticsService;
	@Autowired
	private EmployeeRepository employeeRepository;
	@Autowired
	private EffectiveDating effectiveDating;
	@Autowired
	private SalaryBandRepository salaryBandRepository;
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

	@Test
	void listsExactlyTheSeededBelowMinAndAboveMaxEmployeesWithTheCorrectGap() {
		Fixtures fx = seedFixtures("OOB");

		// Band: 90000 / 110000 / 130000.
		hireWithComp(fx, "E-OOB-BELOW", new BigDecimal("75000"));   // gap 15000
		hireWithComp(fx, "E-OOB-ABOVE", new BigDecimal("150000"));  // gap 20000
		hireWithComp(fx, "E-OOB-INBAND", new BigDecimal("100000")); // not an anomaly

		OutOfBandResponse response = analyticsService.outOfBand();

		Set<String> myAnomalyNumbers = Set.of("E-OOB-BELOW", "E-OOB-ABOVE");
		List<OutOfBandRow> mine = response.rows().stream()
				.filter(r -> myAnomalyNumbers.contains(r.employeeNumber()))
				.toList();

		// Exactly the two anomalies I seeded appear -- not the in-band employee, and not duplicated.
		assertThat(mine).hasSize(2);

		OutOfBandRow below = mine.stream().filter(r -> "E-OOB-BELOW".equals(r.employeeNumber())).findFirst().orElseThrow();
		assertThat(below.bandStatus()).isEqualTo("BELOW_MIN");
		assertThat(below.gapAmount().amount()).isEqualByComparingTo("15000.00");
		assertThat(below.gapAmount().currency()).isEqualTo("USD");
		assertThat(below.currentBase().amount()).isEqualByComparingTo("75000.00");
		assertThat(below.bandMin().amount()).isEqualByComparingTo("90000.00");

		OutOfBandRow above = mine.stream().filter(r -> "E-OOB-ABOVE".equals(r.employeeNumber())).findFirst().orElseThrow();
		assertThat(above.bandStatus()).isEqualTo("ABOVE_MAX");
		assertThat(above.gapAmount().amount()).isEqualByComparingTo("20000.00");
		assertThat(above.bandMax().amount()).isEqualByComparingTo("130000.00");

		// The in-band employee never appears at all.
		assertThat(response.rows().stream().anyMatch(r -> "E-OOB-INBAND".equals(r.employeeNumber()))).isFalse();

		// totalCostToMinimum includes at least my one below-min employee's 15000 gap (others in the
		// shared container may contribute more, never less).
		assertThat(response.totalCostToMinimum().amount()).isGreaterThanOrEqualTo(new BigDecimal("15000.00"));
		assertThat(response.belowMinCount()).isGreaterThanOrEqualTo(1);
		assertThat(response.aboveMaxCount()).isGreaterThanOrEqualTo(1);
		assertThat(response.baseCurrency()).isEqualTo("USD");
	}

	@Test
	void anEmployeeWithNoBandNeverAppearsAsAnAnomaly() {
		Fixtures fx = seedFixtures("OOBNOBAND");
		// No band exists for this level/country at all -- NO_BAND, not an out-of-band anomaly.
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-OOBNOBAND-1")
				.firstName("First").lastName("Last")
				.workEmail("e-oobnoband-1@acme.test")
				.departmentId(fx.departmentId()).locationId(fx.locationId())
				.jobFamilyId(fx.jobFamilyId()).jobLevelId(fx.jobLevelId())
				.hireDate(LocalDate.of(2020, 1, 1)).employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());
		effectiveDating.apply(new ApplyCommand(
				employee.getId(), LocalDate.of(2020, 1, 1), new BigDecimal("100000"), "USD", "ANNUAL", "INITIAL", null, fx.userId()));

		OutOfBandResponse response = analyticsService.outOfBand();
		assertThat(response.rows().stream().anyMatch(r -> "E-OOBNOBAND-1".equals(r.employeeNumber()))).isFalse();
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
				employee.getId(), LocalDate.of(2020, 1, 1), amount, "USD", "ANNUAL", "INITIAL", null, fx.userId()));
		return employee.getId();
	}

	private record Fixtures(UUID departmentId, UUID locationId, UUID jobFamilyId, UUID jobLevelId, UUID userId) {
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
		User user = userRepository.save(User.builder()
				.email("user-analytics-" + suffix.toLowerCase() + "@acme.test")
				.fullName("Analytics Tester").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());

		LocalDate rateMonth = LocalDate.of(2020, 1, 1);
		fxRateRepository.findByBaseCurrencyAndQuoteCurrencyAndRateMonth("USD", "USD", rateMonth)
				.orElseGet(() -> fxRateRepository.save(FxRate.builder()
						.rateMonth(rateMonth).baseCurrency("USD").quoteCurrency("USD").rate(BigDecimal.ONE)
						.build()));

		if (!"OOBNOBAND".equals(suffix)) {
			salaryBandRepository.save(SalaryBand.builder()
					.jobLevelId(level.getId()).countryCode("US").currency("USD")
					.minAmount(new BigDecimal("90000")).midAmount(new BigDecimal("110000")).maxAmount(new BigDecimal("130000"))
					.effectiveFrom(LocalDate.of(2020, 1, 1)).createdBy(user.getId())
					.build());
		}

		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), user.getId());
	}

}
