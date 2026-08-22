package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.dto.EmployeeImportResult;
import com.acme.salaryos.employee.repository.EmployeeRepository;
import com.acme.salaryos.employee.service.EmployeeService;
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
 * P8.4's own Verify clause: the dry run reports counts and writes nothing. Same
 * create-vs-version shape {@code BandService.importCsv} (P5.3) established, here create-vs-update
 * keyed by {@code employeeNumber}.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class EmployeeImportTest {

	@Autowired
	private EmployeeService employeeService;
	@Autowired
	private EmployeeRepository employeeRepository;
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
	void dryRunReportsCountsAndWritesNothingThenTheRealRunCreatesAndAFollowUpImportUpdates() throws Exception {
		Fixtures fx = seedFixtures("IMPORT");
		String employeeNumber = "E-IMPORT-" + UUID.randomUUID().toString().substring(0, 8);

		String header = "employeeNumber,firstName,lastName,workEmail,departmentId,locationId,jobFamilyId,jobLevelId,managerId,hireDate,employmentType,fte\n";
		String goodRow = employeeNumber + ",First,Last,import-" + employeeNumber.toLowerCase() + "@acme.test," + fx.departmentId() + "," + fx.locationId() + ","
				+ fx.jobFamilyId() + "," + fx.jobLevelId() + ",,2024-01-15,FULL_TIME,1.00\n";
		String badRow = "E-IMPORT-BAD,NoSuch,Department,bad@acme.test,00000000-0000-0000-0000-000000000000," + fx.locationId() + ","
				+ fx.jobFamilyId() + "," + fx.jobLevelId() + ",,2024-01-15,FULL_TIME,1.00\n";
		String csv = header + goodRow + badRow;
		MockMultipartFile file = new MockMultipartFile("file", "employees.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		// Dry run: reports 1 create, 1 error, and writes nothing.
		EmployeeImportResult dryRun = employeeService.importCsv(file, true, fx.userId());
		assertThat(dryRun.dryRun()).isTrue();
		assertThat(dryRun.totalRows()).isEqualTo(2);
		assertThat(dryRun.created()).isEqualTo(1);
		assertThat(dryRun.updated()).isZero();
		assertThat(dryRun.errors()).isEqualTo(1);
		assertThat(dryRun.rowsApplied()).isZero();
		assertThat(employeeRepository.findByEmployeeNumber(employeeNumber)).isEmpty();
		assertThat(dryRun.rows().get(1).action()).isEqualTo("ERROR");
		assertThat(dryRun.rows().get(1).error()).contains("No department");

		// The real run applies exactly the create; the bad row still errors, still writes nothing for itself.
		MockMultipartFile file2 = new MockMultipartFile("file", "employees.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
		EmployeeImportResult realRun = employeeService.importCsv(file2, false, fx.userId());
		assertThat(realRun.dryRun()).isFalse();
		assertThat(realRun.created()).isEqualTo(1);
		assertThat(realRun.rowsApplied()).isEqualTo(1);
		Employee created = employeeRepository.findByEmployeeNumber(employeeNumber).orElseThrow();
		assertThat(created.getFirstName()).isEqualTo("First");

		// A follow-up import of the same employeeNumber updates the existing row instead of erroring
		// as a duplicate -- and never touches pay.
		String updateRow = employeeNumber + ",FirstRenamed,Last,import-" + employeeNumber.toLowerCase() + "@acme.test," + fx.departmentId() + "," + fx.locationId() + ","
				+ fx.jobFamilyId() + "," + fx.jobLevelId() + ",,2024-01-15,FULL_TIME,1.00\n";
		MockMultipartFile file3 = new MockMultipartFile("file", "employees.csv", "text/csv", (header + updateRow).getBytes(StandardCharsets.UTF_8));
		EmployeeImportResult updateRun = employeeService.importCsv(file3, false, fx.userId());
		assertThat(updateRun.updated()).isEqualTo(1);
		assertThat(updateRun.created()).isZero();
		Employee updated = employeeRepository.findByEmployeeNumber(employeeNumber).orElseThrow();
		assertThat(updated.getFirstName()).isEqualTo("FirstRenamed");
		assertThat(updated.getId()).isEqualTo(created.getId());
	}

	private record Fixtures(UUID departmentId, UUID locationId, UUID jobFamilyId, UUID jobLevelId, UUID userId) {
	}

	private Fixtures seedFixtures(String suffix) {
		countryRepository.findById("US").orElseGet(() -> countryRepository.save(
				Country.builder().code("US").name("United States").defaultCurrency("USD").build()));
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ IMPORT-" + suffix).build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering IMPORT-" + suffix).code("DEPT-IMPORT-" + suffix).build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-IMPORT-" + suffix).build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer IMPORT-" + suffix).sortOrder(4).build());
		// A real row, not a random UUID -- AuditService.recordWrite's FK to users(id) needs one.
		User importer = userRepository.save(User.builder()
				.email("importer-" + suffix.toLowerCase() + "@acme.test").fullName("Importer")
				.passwordHash("{argon2}stub").role("HR_ADMIN").build());

		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), importer.getId());
	}

}
