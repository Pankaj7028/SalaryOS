package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.change.dto.ChangeBulkUploadResult;
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
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P6.3's own Verify clause: a file with 100 rows and 12 bad ones creates 88 proposals and one
 * report (FR-5.8). The "report" is the full {@code rows()} list itself — same shape as {@code
 * BandImportResult}, downloadable by whatever UI eventually renders it.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class ChangeBulkUploadTest {

	@Autowired
	private ChangeService changeService;
	@Autowired
	private EffectiveDating effectiveDating;
	@Autowired
	private EmployeeRepository employeeRepository;
	@Autowired
	private SalaryBandRepository salaryBandRepository;
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
	void aFileWith100RowsAnd12BadOnesCreates88ProposalsAndOneReport() throws Exception {
		Fixtures fx = seedFixtures();
		LocalDate effectiveDate = LocalDate.of(2038, 6, 1);

		StringBuilder csv = new StringBuilder("employeeNumber,newAmount,changeReason,note\n");
		for (int i = 1; i <= 88; i++) {
			String number = "E-BULK-" + i;
			hireWithComp(fx, number, new BigDecimal("100000"));
			csv.append(number).append(",110000,MERIT,\n");
		}
		for (int i = 1; i <= 4; i++) {
			csv.append("E-BULK-UNKNOWN-").append(i).append(",110000,MERIT,\n");
		}
		for (int i = 1; i <= 4; i++) {
			csv.append("E-BULK-BADAMOUNT-").append(i).append(",not-a-number,MERIT,\n");
		}
		for (int i = 1; i <= 4; i++) {
			csv.append("SHORTROW-").append(i).append(",100000\n");
		}

		MockMultipartFile file = new MockMultipartFile(
				"file", "merit.csv", "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8));

		ChangeBulkUploadResult result = changeService.bulkUpload(file, effectiveDate, fx.proposerId());

		assertThat(result.totalRows()).isEqualTo(100);
		assertThat(result.proposed()).isEqualTo(88);
		assertThat(result.errors()).isEqualTo(12);
		assertThat(result.rows()).hasSize(100);

		assertThat(result.rows()).filteredOn(r -> "PROPOSED".equals(r.action())).hasSize(88)
				.allSatisfy(r -> assertThat(r.changeId()).isNotNull());
		assertThat(result.rows()).filteredOn(r -> "ERROR".equals(r.action())).hasSize(12)
				.allSatisfy(r -> assertThat(r.error()).isNotBlank());
	}

	private void hireWithComp(Fixtures fx, String employeeNumber, BigDecimal amount) {
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
	}

	private record Fixtures(UUID departmentId, UUID locationId, UUID jobFamilyId, UUID jobLevelId, UUID proposerId) {
	}

	private Fixtures seedFixtures() {
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ BULK").build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering").code("DEPT-BULK").build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-BULK").build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer BULK").sortOrder(4).build());
		User proposer = userRepository.save(User.builder()
				.email("proposer-bulk@acme.test")
				.fullName("Proposer").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());

		LocalDate hireMonth = LocalDate.of(2020, 1, 1);
		LocalDate cycleMonth = LocalDate.of(2038, 6, 1);
		fxRateRepository.save(FxRate.builder()
				.rateMonth(hireMonth).baseCurrency("USD").quoteCurrency("USD").rate(BigDecimal.ONE).build());
		fxRateRepository.save(FxRate.builder()
				.rateMonth(cycleMonth).baseCurrency("USD").quoteCurrency("USD").rate(BigDecimal.ONE).build());

		salaryBandRepository.save(SalaryBand.builder()
				.jobLevelId(level.getId()).countryCode("US").currency("USD")
				.minAmount(new BigDecimal("90000")).midAmount(new BigDecimal("110000")).maxAmount(new BigDecimal("130000"))
				.effectiveFrom(LocalDate.of(2020, 1, 1)).createdBy(proposer.getId())
				.build());

		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), proposer.getId());
	}

}
