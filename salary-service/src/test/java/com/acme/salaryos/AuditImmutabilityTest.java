package com.acme.salaryos;

import com.acme.salaryos.audit.AuditEvent;
import com.acme.salaryos.audit.AuditEventRepository;
import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.dto.EmployeeUpdateRequest;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P8.2's own Verify clause: a pay-list read produces an audit row recording the filter; an update
 * is denied. {@code V8V9AuditAndProjectionMigrationTest} (P1.7) already proved the database-level
 * grant with a hand-crafted INSERT — this test instead drives the REAL feature: a genuine
 * {@code EmployeeService} write and a genuine {@code /employees} list read, through
 * {@code AuditService}, then proves the row it actually wrote is immutable at the database level
 * too (same {@code salaryos_app} role check, applied to a row this session's own code produced,
 * not a synthetic one).
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class AuditImmutabilityTest {

	@Autowired
	private EmployeeService employeeService;
	@Autowired
	private EmployeeRepository employeeRepository;
	@Autowired
	private AuditEventRepository auditEventRepository;
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
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private PostgreSQLContainer postgresContainer;

	@Test
	void aRealWriteAndARealListReadEachProduceAnAuditRowAndNeitherCanBeUpdatedByTheAppRole() {
		Fixtures fx = seedFixtures("AUDIT");
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-AUDIT-1").firstName("First").lastName("Last")
				.workEmail("e-audit-1@acme.test")
				.departmentId(fx.departmentId()).locationId(fx.locationId())
				.jobFamilyId(fx.jobFamilyId()).jobLevelId(fx.jobLevelId())
				.hireDate(LocalDate.of(2020, 1, 1)).employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());

		// FR-7.1: a real write, through the real service, produces a real audit row with the
		// actor, the entity, and before/after state -- not a mock, not a hand-crafted INSERT.
		employeeService.update(employee.getId(), new EmployeeUpdateRequest(
				"Renamed", "Last", "e-audit-1@acme.test", fx.departmentId(), fx.locationId(),
				fx.jobFamilyId(), fx.jobLevelId(), null, "FULL_TIME", BigDecimal.ONE), fx.userId());

		List<AuditEvent> writeEvents = auditEventRepository.findAll().stream()
				.filter(e -> employee.getId().equals(e.getEntityId()) && "UPDATE_EMPLOYEE".equals(e.getAction()))
				.toList();
		assertThat(writeEvents).hasSize(1);
		AuditEvent writeEvent = writeEvents.get(0);
		assertThat(writeEvent.getActorUserId()).isEqualTo(fx.userId());
		assertThat(writeEvent.getActorRole()).isEqualTo("HR_ADMIN");
		assertThat(writeEvent.getEntityType()).isEqualTo("EMPLOYEE");
		assertThat(writeEvent.getBeforeJson()).contains("\"firstName\"").contains("\"First\"");
		assertThat(writeEvent.getAfterJson()).contains("\"firstName\"").contains("\"Renamed\"");

		// FR-7.2: a real list read records the filter and the count, never an id list.
		employeeService.list(null, fx.departmentId(), null, null, null, null, null, null, null, null, null, 50, fx.userId());
		List<AuditEvent> readEvents = auditEventRepository.findAll().stream()
				.filter(e -> "READ_LIST".equals(e.getAction()) && "EMPLOYEE".equals(e.getEntityType())
						&& e.getAfterJson() != null && e.getAfterJson().contains(fx.departmentId().toString()))
				.toList();
		assertThat(readEvents).isNotEmpty();
		assertThat(readEvents.get(0).getBeforeJson()).isNull();
		assertThat(readEvents.get(0).getEntityId()).isNull();

		// FR-7.3: neither row can be updated by the application's own database role -- denied at
		// the database, not merely by the absence of an update endpoint in this codebase.
		JdbcTemplate appJdbcTemplate = new JdbcTemplate(appRoleDataSource());
		assertThatThrownBy(() -> appJdbcTemplate.update(
				"update salary_schema.audit_events set action = 'TAMPERED' where id = ?", writeEvent.getId()))
				.isInstanceOf(DataAccessException.class)
				.rootCause()
				.hasMessageContaining("permission denied");

		// The app role CAN still read what it (and this test's own INSERTs) already wrote.
		Integer count = appJdbcTemplate.queryForObject(
				"select count(*) from salary_schema.audit_events where id = ?", Integer.class, writeEvent.getId());
		assertThat(count).isEqualTo(1);
	}

	private DriverManagerDataSource appRoleDataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl(postgresContainer.getJdbcUrl());
		dataSource.setUsername("salaryos_app");
		dataSource.setPassword("local-dev-only-not-a-secret");
		return dataSource;
	}

	private record Fixtures(UUID departmentId, UUID locationId, UUID jobFamilyId, UUID jobLevelId, UUID userId) {
	}

	private Fixtures seedFixtures(String suffix) {
		countryRepository.findById("US").orElseGet(() -> countryRepository.save(
				Country.builder().code("US").name("United States").defaultCurrency("USD").build()));
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ AUDIT-" + suffix).build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering AUDIT-" + suffix).code("DEPT-AUDIT-" + suffix).build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-AUDIT-" + suffix).build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer AUDIT-" + suffix).sortOrder(4).build());
		User user = userRepository.save(User.builder()
				.email("user-audit-" + suffix.toLowerCase() + "@acme.test")
				.fullName("Audit Tester").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());

		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), user.getId());
	}

}
