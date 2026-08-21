package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.common.money.Money;
import com.acme.salaryos.compensation.domain.CompensationComponent;
import com.acme.salaryos.compensation.domain.CompensationRecord;
import com.acme.salaryos.compensation.domain.EmployeeCurrentComp;
import com.acme.salaryos.compensation.repository.CompensationComponentRepository;
import com.acme.salaryos.compensation.repository.CompensationRecordRepository;
import com.acme.salaryos.compensation.repository.EmployeeCurrentCompRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P1.9: JPA round-trip for the compensation and band/fx modules' entities. */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class CompensationEntitiesRoundTripTest {

	@Autowired
	private SalaryBandRepository salaryBandRepository;
	@Autowired
	private FxRateRepository fxRateRepository;
	@Autowired
	private CompensationRecordRepository compensationRecordRepository;
	@Autowired
	private CompensationComponentRepository compensationComponentRepository;
	@Autowired
	private EmployeeCurrentCompRepository employeeCurrentCompRepository;
	@Autowired
	private EmployeeRepository employeeRepository;
	@Autowired
	private UserRepository userRepository;
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

	private UUID jobLevelId;
	private UUID employeeId;
	private UUID userId;

	private void seedGraph(String suffix) {
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ " + suffix).build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering").code("DEPT-" + suffix).build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-" + suffix).build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer").sortOrder(4).build());
		jobLevelId = level.getId();

		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-" + suffix)
				.firstName("Ada").lastName("Lovelace")
				.workEmail("ada-" + suffix + "@acme.test")
				.departmentId(department.getId()).locationId(location.getId())
				.jobFamilyId(family.getId()).jobLevelId(level.getId())
				.hireDate(LocalDate.of(2022, 1, 1))
				.employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());
		employeeId = employee.getId();

		User user = userRepository.save(User.builder()
				// citext email uniqueness is case-insensitive; namespace clearly so this can't
				// collide with another test class's seed data sharing this cached container.
				.email("hr-admin-jpa-comp-" + suffix.toLowerCase() + "@acme.test")
				.fullName("HR Admin").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());
		userId = user.getId();
	}

	@Test
	void salaryBandRoundTrips() {
		seedGraph("BAND");
		SalaryBand saved = salaryBandRepository.save(SalaryBand.builder()
				.jobLevelId(jobLevelId).countryCode("US").currency("USD")
				.minAmount(new BigDecimal("100000.00"))
				.midAmount(new BigDecimal("150000.00"))
				.maxAmount(new BigDecimal("200000.00"))
				.effectiveFrom(LocalDate.of(2024, 1, 1))
				.createdBy(userId)
				.build());

		SalaryBand loaded = salaryBandRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getMidAmount()).isEqualByComparingTo("150000.00");
	}

	@Test
	void fxRateRoundTrips() {
		FxRate saved = fxRateRepository.save(FxRate.builder()
				.rateMonth(LocalDate.of(2024, 3, 1))
				.baseCurrency("EUR").quoteCurrency("USD")
				.rate(new BigDecimal("1.08123456"))
				.build());

		FxRate loaded = fxRateRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getRate()).isEqualByComparingTo("1.08123456");
	}

	@Test
	void compensationRecordRoundTripsWithTwoMoneyEmbeds() {
		seedGraph("REC");
		FxRate fxRate = fxRateRepository.save(FxRate.builder()
				.rateMonth(LocalDate.of(2024, 4, 1))
				.baseCurrency("USD").quoteCurrency("USD")
				.rate(BigDecimal.ONE)
				.build());

		CompensationRecord saved = compensationRecordRepository.save(CompensationRecord.builder()
				.employeeId(employeeId)
				.effectiveFrom(LocalDate.of(2024, 4, 1))
				.base(new Money(new BigDecimal("120000"), "USD"))
				.payFrequency("ANNUAL")
				.annualBaseAmount(new BigDecimal("120000.00"))
				.normalizedAnnualBase(new Money(new BigDecimal("120000"), "USD"))
				.fxRateId(fxRate.getId())
				.changeReason("INITIAL")
				.createdBy(userId)
				.build());

		CompensationRecord loaded = compensationRecordRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getBase().amount()).isEqualByComparingTo("120000.00");
		assertThat(loaded.getBase().currency()).isEqualTo("USD");
		assertThat(loaded.getNormalizedAnnualBase().amount()).isEqualByComparingTo("120000.00");
		assertThat(loaded.getCreatedAt()).isNotNull();
	}

	@Test
	void compensationComponentRoundTrips() {
		seedGraph("COMP");
		FxRate fxRate = fxRateRepository.save(FxRate.builder()
				.rateMonth(LocalDate.of(2024, 5, 1))
				.baseCurrency("USD").quoteCurrency("USD")
				.rate(BigDecimal.ONE)
				.build());
		CompensationRecord record = compensationRecordRepository.save(CompensationRecord.builder()
				.employeeId(employeeId)
				.effectiveFrom(LocalDate.of(2024, 5, 1))
				.base(new Money(new BigDecimal("120000"), "USD"))
				.payFrequency("ANNUAL")
				.annualBaseAmount(new BigDecimal("120000.00"))
				.normalizedAnnualBase(new Money(new BigDecimal("120000"), "USD"))
				.fxRateId(fxRate.getId())
				.changeReason("INITIAL")
				.createdBy(userId)
				.build());

		CompensationComponent saved = compensationComponentRepository.save(CompensationComponent.builder()
				.compensationRecordId(record.getId())
				.componentType("HOUSING")
				.amount(new Money(new BigDecimal("500"), "USD"))
				.build());

		CompensationComponent loaded = compensationComponentRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getAmount().amount()).isEqualByComparingTo("500.00");
		assertThat(loaded.isRecurring()).isTrue();
	}

	@Test
	void employeeCurrentCompRoundTrips() {
		seedGraph("PROJ");
		FxRate fxRate = fxRateRepository.save(FxRate.builder()
				.rateMonth(LocalDate.of(2024, 6, 1))
				.baseCurrency("USD").quoteCurrency("USD")
				.rate(BigDecimal.ONE)
				.build());
		CompensationRecord record = compensationRecordRepository.save(CompensationRecord.builder()
				.employeeId(employeeId)
				.effectiveFrom(LocalDate.of(2024, 6, 1))
				.base(new Money(new BigDecimal("130000"), "USD"))
				.payFrequency("ANNUAL")
				.annualBaseAmount(new BigDecimal("130000.00"))
				.normalizedAnnualBase(new Money(new BigDecimal("130000"), "USD"))
				.fxRateId(fxRate.getId())
				.changeReason("INITIAL")
				.createdBy(userId)
				.build());

		employeeCurrentCompRepository.save(EmployeeCurrentComp.builder()
				.employeeId(employeeId)
				.compensationRecordId(record.getId())
				.base(new Money(new BigDecimal("130000"), "USD"))
				.annualBaseAmount(new BigDecimal("130000.00"))
				.normalizedAnnualBase(new BigDecimal("130000.00"))
				.bandStatus("NO_BAND")
				.build());

		EmployeeCurrentComp loaded = employeeCurrentCompRepository.findById(employeeId).orElseThrow();
		assertThat(loaded.getBandStatus()).isEqualTo("NO_BAND");
		assertThat(loaded.getBase().amount()).isEqualByComparingTo("130000.00");
		assertThat(loaded.getRefreshedAt()).isNotNull();
	}

}
