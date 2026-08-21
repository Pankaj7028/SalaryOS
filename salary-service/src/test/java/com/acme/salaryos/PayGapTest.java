package com.acme.salaryos;

import com.acme.salaryos.analytics.dto.PayGapCohortRow;
import com.acme.salaryos.analytics.dto.PayGapResponse;
import com.acme.salaryos.analytics.service.AnalyticsService;
import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.compensation.effective.ApplyCommand;
import com.acme.salaryos.compensation.effective.EffectiveDating;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.domain.EmployeeDemographics;
import com.acme.salaryos.employee.repository.EmployeeDemographicsRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P7.4's own Verify clause (suppression inside the query, a suppressed-cohort count). Every
 * cohort here uses a freshly created {@code JobLevel} (a brand-new UUID each test run), so the
 * level-adjusted assertions are naturally isolated from the shared Testcontainers container —
 * unlike the org-wide {@code unadjustedGroups}, which this test only checks structurally (every
 * surviving group has at least five people), since other test classes
 * ({@code EmployeeEntitiesRoundTripTest}) also seed demographic rows into the same container.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class PayGapTest {

	@Autowired
	private AnalyticsService analyticsService;
	@Autowired
	private EmployeeRepository employeeRepository;
	@Autowired
	private EmployeeDemographicsRepository employeeDemographicsRepository;
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

	@Test
	void aCohortWithTwoGroupsOfAtLeastFiveEachReportsTheExactMedianGap() {
		Fixtures fx = seedFixtures("GAP");

		// Male: 100000,102000,104000,106000,108000,110000 -> median (104000+106000)/2 = 105000.
		BigDecimal[] male = { new BigDecimal("100000"), new BigDecimal("102000"), new BigDecimal("104000"),
				new BigDecimal("106000"), new BigDecimal("108000"), new BigDecimal("110000") };
		for (int i = 0; i < male.length; i++) {
			hireWithGender(fx, "E-GAP-M" + i, male[i], "Male");
		}
		// Female: 90000,92000,94000,96000,98000 -> median (middle of 5) = 94000.
		BigDecimal[] female = { new BigDecimal("90000"), new BigDecimal("92000"), new BigDecimal("94000"),
				new BigDecimal("96000"), new BigDecimal("98000") };
		for (int i = 0; i < female.length; i++) {
			hireWithGender(fx, "E-GAP-F" + i, female[i], "Female");
		}
		// A third, too-small group: never appears anywhere, not even as a lone row.
		hireWithGender(fx, "E-GAP-NB0", new BigDecimal("80000"), "Non-binary");
		hireWithGender(fx, "E-GAP-NB1", new BigDecimal("82000"), "Non-binary");

		PayGapResponse response = analyticsService.payGap();

		PayGapCohortRow cohort = response.levelAdjustedCohorts().stream()
				.filter(c -> fx.jobLevelId().equals(c.jobLevelId()))
				.findFirst().orElseThrow();

		assertThat(cohort.groups()).hasSize(2); // exactly Male and Female -- Non-binary (n=2) never appears
		assertThat(cohort.groups()).noneMatch(g -> "Non-binary".equals(g.group()));

		var maleGroup = cohort.groups().stream().filter(g -> "Male".equals(g.group())).findFirst().orElseThrow();
		var femaleGroup = cohort.groups().stream().filter(g -> "Female".equals(g.group())).findFirst().orElseThrow();
		assertThat(maleGroup.count()).isEqualTo(6);
		assertThat(maleGroup.median().amount()).isEqualByComparingTo("105000.00");
		assertThat(femaleGroup.count()).isEqualTo(5);
		assertThat(femaleGroup.median().amount()).isEqualByComparingTo("94000.00");

		assertThat(cohort.gapAmount().amount()).isEqualByComparingTo("11000.00");
		assertThat(cohort.gapPercent()).isEqualByComparingTo("0.104762"); // 11000 / 105000

		// Structural check on the org-wide figure only -- other tests also seed demographic rows
		// into the same shared container, so exact medians there aren't assertable.
		assertThat(response.unadjustedGroups()).allSatisfy(g -> assertThat(g.count()).isGreaterThanOrEqualTo(5));
		assertThat(response.baseCurrency()).isEqualTo("USD");
	}

	@Test
	void aCohortWhereEveryoneSharesOneGroupNeverAppearsAndCountsAsSuppressed() {
		Fixtures fx = seedFixtures("GAPSUPPRESSED");
		for (int i = 0; i < 3; i++) {
			hireWithGender(fx, "E-GAPSUPPRESSED-" + i, new BigDecimal("100000"), "Male");
		}

		PayGapResponse response = analyticsService.payGap();

		assertThat(response.levelAdjustedCohorts()).noneMatch(c -> fx.jobLevelId().equals(c.jobLevelId()));
		// Suppressed for two independent reasons in this one cohort: the single Male group has
		// only 3 people (under the threshold) and no second group exists to compare against either
		// way -- this test's own cohort contributes at least one to the global suppressed count.
		assertThat(response.suppressedCohorts()).isGreaterThanOrEqualTo(1);
	}

	private void hireWithGender(Fixtures fx, String employeeNumber, BigDecimal amount, String gender) {
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
		employeeDemographicsRepository.save(EmployeeDemographics.builder()
				.employeeId(employee.getId()).gender(gender).build());
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

		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), user.getId());
	}

}
