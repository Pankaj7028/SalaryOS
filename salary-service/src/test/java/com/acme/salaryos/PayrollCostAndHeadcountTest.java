package com.acme.salaryos;

import com.acme.salaryos.analytics.dto.HeadcountGroup;
import com.acme.salaryos.analytics.dto.HeadcountResponse;
import com.acme.salaryos.analytics.dto.PayrollCostGroup;
import com.acme.salaryos.analytics.dto.PayrollCostResponse;
import com.acme.salaryos.analytics.service.AnalyticsService;
import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.compensation.effective.ApplyCommand;
import com.acme.salaryos.compensation.effective.EffectiveDating;
import com.acme.salaryos.employee.domain.Employee;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P7.1's own Verify clause: totals reconcile against a direct SQL sum. The shared Testcontainers
 * container accumulates rows from every other test class, so — same discipline as
 * {@code EmployeeListPaginationTest}/{@code BandVersioningTest}'s scoped counts — this never
 * asserts an unscoped global total. Every assertion filters the response's own
 * {@code byDepartment}/{@code byLevel} breakdown down to this test's own seeded ids, then
 * reconciles against an independent SQL sum scoped the same way.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class PayrollCostAndHeadcountTest {

	@Autowired
	private AnalyticsService analyticsService;
	@Autowired
	private EmployeeRepository employeeRepository;
	@Autowired
	private EmployeeService employeeService;
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

	@Test
	void payrollCostByDepartmentAndByLevelReconcileAgainstADirectSqlSum() {
		Fixtures fx = seedFixtures("PAYCOST");

		hireWithComp(fx, "E-PAYCOST-1", new BigDecimal("100000"));
		hireWithComp(fx, "E-PAYCOST-2", new BigDecimal("120000"));
		UUID terminatedId = hireWithComp(fx, "E-PAYCOST-3", new BigDecimal("90000"));
		employeeService.terminate(terminatedId, LocalDate.of(2020, 6, 1));

		PayrollCostResponse response = analyticsService.payrollCost();

		PayrollCostGroup department = findByKey(response.byDepartment(), fx.departmentId().toString());
		BigDecimal directDeptSum = jdbcTemplate.queryForObject(
				"""
				SELECT sum(c.normalized_annual_base) FROM salary_schema.employee_current_comp c
				  JOIN salary_schema.employees e ON e.id = c.employee_id
				 WHERE e.department_id = ?
				""", BigDecimal.class, fx.departmentId());

		// The terminated employee's row was deleted (P5.2), so only the two active hires count:
		// 100000 + 120000 = 220000, not 310000.
		assertThat(department.headcount()).isEqualTo(2);
		assertThat(department.totalAnnualBase().amount()).isEqualByComparingTo("220000.00");
		assertThat(department.totalAnnualBase().amount()).isEqualByComparingTo(directDeptSum);
		assertThat(department.averageAnnualBase().amount()).isEqualByComparingTo("110000.00");

		PayrollCostGroup level = findByKey(response.byLevel(), fx.jobLevelId().toString());
		assertThat(level.headcount()).isEqualTo(2);
		assertThat(level.totalAnnualBase().amount()).isEqualByComparingTo("220000.00");

		// The terminated employee is excluded from the total but not hidden from the basis envelope.
		assertThat(response.population().excluded().get("terminated")).isGreaterThanOrEqualTo(1);
		assertThat(response.baseCurrency()).isEqualTo("USD");
		assertThat(response.asAtDate()).isNotNull();
	}

	@Test
	void headcountByDepartmentAndByStatusReconcileAgainstADirectSqlSum() {
		Fixtures fx = seedFixtures("HEADCOUNT");

		hireWithComp(fx, "E-HEADCOUNT-1", new BigDecimal("100000"));
		hireWithComp(fx, "E-HEADCOUNT-2", new BigDecimal("100000"));
		UUID terminatedId = hireWithComp(fx, "E-HEADCOUNT-3", new BigDecimal("100000"));
		employeeService.terminate(terminatedId, LocalDate.of(2020, 6, 1));

		HeadcountResponse response = analyticsService.headcount();

		HeadcountGroup department = findByKey(response.byDepartment(), fx.departmentId().toString());
		Integer directCount = jdbcTemplate.queryForObject(
				"""
				SELECT count(*) FROM salary_schema.employee_current_comp c
				  JOIN salary_schema.employees e ON e.id = c.employee_id
				 WHERE e.department_id = ?
				""", Integer.class, fx.departmentId());

		assertThat(department.headcount()).isEqualTo(2);
		assertThat(department.headcount()).isEqualTo(directCount);

		HeadcountGroup active = response.byStatus().stream().filter(g -> "ACTIVE".equals(g.key())).findFirst().orElseThrow();
		HeadcountGroup terminated = response.byStatus().stream().filter(g -> "TERMINATED".equals(g.key())).findFirst().orElseThrow();
		assertThat(active.headcount()).isGreaterThanOrEqualTo(2);
		assertThat(terminated.headcount()).isGreaterThanOrEqualTo(1);
	}

	private <T> T findByKey(List<T> groups, String key) {
		return groups.stream()
				.filter(g -> key.equals(keyOf(g)))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no group with key " + key));
	}

	private String keyOf(Object group) {
		if (group instanceof PayrollCostGroup g) return g.key();
		if (group instanceof HeadcountGroup g) return g.key();
		throw new IllegalArgumentException("unexpected group type " + group);
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

		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), user.getId());
	}

}
