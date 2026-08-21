package com.acme.salaryos;

import com.acme.salaryos.audit.AuditEvent;
import com.acme.salaryos.audit.AuditEventRepository;
import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.change.domain.CompensationChange;
import com.acme.salaryos.change.repository.CompensationChangeRepository;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.repository.EmployeeRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/** P1.9: JPA round-trip for the change and audit modules' entities. */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class ChangeAndAuditEntitiesRoundTripTest {

	@Autowired
	private CompensationChangeRepository compensationChangeRepository;
	@Autowired
	private AuditEventRepository auditEventRepository;
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

	@Test
	void compensationChangeRoundTrips() {
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ CHANGE-JPA").build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering").code("DEPT-CHANGE-JPA").build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-CHANGE-JPA").build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer").sortOrder(4).build());
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-CHANGE-JPA")
				.firstName("Grace").lastName("Hopper")
				.workEmail("grace-change-jpa@acme.test")
				.departmentId(department.getId()).locationId(location.getId())
				.jobFamilyId(family.getId()).jobLevelId(level.getId())
				.hireDate(LocalDate.of(2022, 1, 1))
				.employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());
		User proposer = userRepository.save(User.builder()
				.email("hr-manager-change-jpa@acme.test")
				.fullName("HR Manager").passwordHash("{argon2}stub").role("HR_MANAGER")
				.build());

		CompensationChange saved = compensationChangeRepository.save(CompensationChange.builder()
				.employeeId(employee.getId())
				.status("PENDING")
				.effectiveDate(LocalDate.now().plusDays(30))
				.currentBaseAmount(new BigDecimal("100000.00"))
				.newBaseAmount(new BigDecimal("110000.00"))
				.currency("USD")
				.changeReason("MERIT")
				.proposedBy(proposer.getId())
				.build());

		CompensationChange loaded = compensationChangeRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getStatus()).isEqualTo("PENDING");
		assertThat(loaded.getNewBaseAmount()).isEqualByComparingTo("110000.00");
		assertThat(loaded.getProposedAt()).isNotNull();
	}

	@Test
	void auditEventRoundTripsIncludingJsonbAndInet() throws java.net.UnknownHostException {
		User actor = userRepository.save(User.builder()
				.email("auditor-jpa@acme.test")
				.fullName("Auditor").passwordHash("{argon2}stub").role("AUDITOR")
				.build());

		AuditEvent saved = auditEventRepository.save(AuditEvent.builder()
				.actorUserId(actor.getId())
				.actorRole("AUDITOR")
				.action("VIEW")
				.entityType("Employee")
				.beforeJson("{\"status\":\"ACTIVE\"}")
				.afterJson(null)
				.ip(java.net.InetAddress.getByName("198.51.100.23"))
				.build());

		AuditEvent loaded = auditEventRepository.findById(saved.getId()).orElseThrow();
		// jsonb re-serialises on storage (canonical spacing) — compare content, not exact text.
		assertThat(loaded.getBeforeJson()).contains("\"status\"").contains("\"ACTIVE\"");
		assertThat(loaded.getIp().getHostAddress()).isEqualTo("198.51.100.23");
		assertThat(loaded.getOccurredAt()).isNotNull();
	}

}
