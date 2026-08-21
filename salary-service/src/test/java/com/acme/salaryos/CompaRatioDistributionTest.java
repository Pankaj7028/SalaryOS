package com.acme.salaryos;

import com.acme.salaryos.analytics.dto.CompaRatioGroupMedian;
import com.acme.salaryos.analytics.dto.CompaRatioHistogramBucket;
import com.acme.salaryos.analytics.query.CompaRatioDistributionQuery;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P7.3's own Verify clause: quartiles match a SQL cross-check. Five employees under one unique
 * department/level pair, chosen so {@code percentile_cont} lands on exact values
 * (0.8/0.9/1.0/1.1/1.3 compa-ratio against a 90000/110000/130000 band) — filtering the response's
 * {@code byDepartment} row to this test's own department avoids the shared-container contamination
 * every other analytics test already works around.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class CompaRatioDistributionTest {

	@Autowired
	private CompaRatioDistributionQuery query;
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
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void quartilesAndHistogramMatchADirectSqlCrossCheckFilteredToOneDepartment() {
		Fixtures fx = seedFixtures("COMPA");

		hireWithComp(fx, "E-COMPA-1", new BigDecimal("88000"));  // 0.8000
		hireWithComp(fx, "E-COMPA-2", new BigDecimal("99000"));  // 0.9000
		hireWithComp(fx, "E-COMPA-3", new BigDecimal("110000")); // 1.0000
		hireWithComp(fx, "E-COMPA-4", new BigDecimal("121000")); // 1.1000
		hireWithComp(fx, "E-COMPA-5", new BigDecimal("143000")); // 1.3000

		var quartiles = query.quartiles(fx.departmentId(), null, null);
		assertThat(quartiles.count()).isEqualTo(5);
		assertThat(quartiles.p25()).isEqualByComparingTo("0.9000");
		assertThat(quartiles.median()).isEqualByComparingTo("1.0000");
		assertThat(quartiles.p75()).isEqualByComparingTo("1.1000");

		// Independent direct SQL cross-check, not a second call to the same query class.
		BigDecimal directMedian = jdbcTemplate.queryForObject(
				"""
				SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY c.compa_ratio)
				  FROM salary_schema.employee_current_comp c
				  JOIN salary_schema.employees e ON e.id = c.employee_id
				 WHERE e.department_id = ?
				""", BigDecimal.class, fx.departmentId());
		assertThat(quartiles.median()).isEqualByComparingTo(directMedian);

		List<CompaRatioHistogramBucket> histogram = query.histogram(fx.departmentId(), null, null);
		assertThat(histogram).hasSize(5); // each of the five buckets this population spans gets exactly one employee
		assertThat(histogram).allSatisfy(b -> assertThat(b.count()).isEqualTo(1));

		CompaRatioGroupMedian department = query.byDepartment(null, null, null).stream()
				.filter(g -> fx.departmentId().toString().equals(g.key()))
				.findFirst().orElseThrow();
		assertThat(department.count()).isEqualTo(5);
		assertThat(department.medianCompaRatio()).isEqualByComparingTo("1.0000");
	}

	@Test
	void anEmployeeWithNoBandIsExcludedFromQuartilesButCountedAsExcluded() {
		Fixtures fx = seedFixtures("COMPANOBAND");
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-COMPANOBAND-1")
				.firstName("First").lastName("Last")
				.workEmail("e-companoband-1@acme.test")
				.departmentId(fx.departmentId()).locationId(fx.locationId())
				.jobFamilyId(fx.jobFamilyId()).jobLevelId(fx.jobLevelId())
				.hireDate(LocalDate.of(2020, 1, 1)).employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());
		effectiveDating.apply(new ApplyCommand(
				employee.getId(), LocalDate.of(2020, 1, 1), new BigDecimal("100000"), "USD", "ANNUAL", "INITIAL", null, fx.userId()));

		var quartiles = query.quartiles(fx.departmentId(), null, null);
		assertThat(quartiles.count()).isZero();
		assertThat(query.noBandCount(fx.departmentId(), null, null)).isEqualTo(1);
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

		if (!"COMPANOBAND".equals(suffix)) {
			salaryBandRepository.save(SalaryBand.builder()
					.jobLevelId(level.getId()).countryCode("US").currency("USD")
					.minAmount(new BigDecimal("90000")).midAmount(new BigDecimal("110000")).maxAmount(new BigDecimal("130000"))
					.effectiveFrom(LocalDate.of(2020, 1, 1)).createdBy(user.getId())
					.build());
		}

		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), user.getId());
	}

}
