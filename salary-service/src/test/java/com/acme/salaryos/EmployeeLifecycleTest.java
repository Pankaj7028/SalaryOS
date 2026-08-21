package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.compensation.domain.CompensationRecord;
import com.acme.salaryos.compensation.repository.CompensationRecordRepository;
import com.acme.salaryos.common.money.Money;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P4.2 — create, edit (band-mismatch flag on level/location change, FR-2.5), terminate (closes
 * the open comp period on the termination date, FR-2.6 — this step's specific Verify).
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class EmployeeLifecycleTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
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
	private FxRateRepository fxRateRepository;
	@Autowired
	private CompensationRecordRepository compensationRecordRepository;

	@Test
	@WithMockUser(roles = "HR_ADMIN")
	void createThenEditWithoutLevelOrLocationChangeLeavesBandMismatchedFalse() throws Exception {
		Fixtures fx = seedFixtures("NOCHANGE");

		UUID employeeId = createEmployee(fx, "E-LIFECYCLE-1");

		String updateBody = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
			put("firstName", "Renamed");
			put("lastName", "Person");
			put("workEmail", "renamed-nochange@acme.test");
			put("departmentId", fx.departmentId.toString());
			put("locationId", fx.locationId.toString());
			put("jobFamilyId", fx.jobFamilyId.toString());
			put("jobLevelId", fx.jobLevelId.toString());
			put("employmentType", "FULL_TIME");
			put("fte", "1.00");
		}});

		mockMvc.perform(patch("/api/employees/" + employeeId)
						.cookie(new jakarta.servlet.http.Cookie("sos_csrf", "test-csrf-token")).header("X-CSRF-Token", "test-csrf-token")
						.contentType("application/json").content(updateBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value("Renamed"))
				.andExpect(jsonPath("$.bandMismatched").value(false));
	}

	@Test
	@WithMockUser(roles = "HR_ADMIN")
	void editingJobLevelSetsBandMismatched() throws Exception {
		Fixtures fx = seedFixtures("LEVELCHG");
		UUID otherLevelId = seedAlternateJobLevel(fx.jobFamilyId, "LEVELCHG");

		UUID employeeId = createEmployee(fx, "E-LIFECYCLE-2");

		String updateBody = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
			put("firstName", "Same");
			put("lastName", "Person");
			put("workEmail", "level-change@acme.test");
			put("departmentId", fx.departmentId.toString());
			put("locationId", fx.locationId.toString());
			put("jobFamilyId", fx.jobFamilyId.toString());
			put("jobLevelId", otherLevelId.toString());
			put("employmentType", "FULL_TIME");
			put("fte", "1.00");
		}});

		mockMvc.perform(patch("/api/employees/" + employeeId)
						.cookie(new jakarta.servlet.http.Cookie("sos_csrf", "test-csrf-token")).header("X-CSRF-Token", "test-csrf-token")
						.contentType("application/json").content(updateBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bandMismatched").value(true));
	}

	@Test
	@WithMockUser(roles = "HR_ADMIN")
	void terminatingClosesTheOpenCompensationPeriodOnTheTerminationDate() throws Exception {
		Fixtures fx = seedFixtures("TERMWITHCOMP");
		UUID employeeId = createEmployee(fx, "E-LIFECYCLE-3");

		FxRate fxRate = fxRateRepository.save(FxRate.builder()
				.rateMonth(LocalDate.of(2024, 7, 1)).baseCurrency("USD").quoteCurrency("USD").rate(BigDecimal.ONE)
				.build());
		CompensationRecord openRecord = compensationRecordRepository.save(CompensationRecord.builder()
				.employeeId(employeeId)
				.effectiveFrom(LocalDate.of(2024, 7, 1))
				.base(new Money(new BigDecimal("100000"), "USD"))
				.payFrequency("ANNUAL")
				.annualBaseAmount(new BigDecimal("100000.00"))
				.normalizedAnnualBase(new Money(new BigDecimal("100000"), "USD"))
				.fxRateId(fxRate.getId())
				.changeReason("INITIAL")
				.createdBy(fx.userId)
				.build());

		LocalDate terminationDate = LocalDate.of(2024, 9, 15);
		MvcResult result = mockMvc.perform(post("/api/employees/" + employeeId + "/terminate")
						.cookie(new jakarta.servlet.http.Cookie("sos_csrf", "test-csrf-token")).header("X-CSRF-Token", "test-csrf-token")
						.contentType("application/json")
						.content("{\"terminationDate\":\"" + terminationDate + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("TERMINATED"))
				.andReturn();

		JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(responseBody.get("terminationDate").asString()).isEqualTo(terminationDate.toString());

		CompensationRecord reloaded = compensationRecordRepository.findById(openRecord.getId()).orElseThrow();
		// Pay runs through and includes the termination date (user-confirmed, P5.4) — validity is a
		// `[)` range, so effective_to is terminationDate + 1, not terminationDate itself.
		assertThat(reloaded.getEffectiveTo()).isEqualTo(terminationDate.plusDays(1));
	}

	@Test
	@WithMockUser(roles = "HR_ADMIN")
	void terminatingAnEmployeeWithNoCompensationRecordStillSucceeds() throws Exception {
		Fixtures fx = seedFixtures("TERMNOCOMP");
		UUID employeeId = createEmployee(fx, "E-LIFECYCLE-4");

		mockMvc.perform(post("/api/employees/" + employeeId + "/terminate")
						.cookie(new jakarta.servlet.http.Cookie("sos_csrf", "test-csrf-token")).header("X-CSRF-Token", "test-csrf-token")
						.contentType("application/json")
						.content("{\"terminationDate\":\"2024-10-01\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("TERMINATED"));
	}

	private UUID createEmployee(Fixtures fx, String employeeNumber) throws Exception {
		String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
			put("employeeNumber", employeeNumber);
			put("firstName", "First");
			put("lastName", "Last");
			put("workEmail", employeeNumber.toLowerCase() + "@acme.test");
			put("departmentId", fx.departmentId.toString());
			put("locationId", fx.locationId.toString());
			put("jobFamilyId", fx.jobFamilyId.toString());
			put("jobLevelId", fx.jobLevelId.toString());
			put("hireDate", "2023-01-01");
			put("employmentType", "FULL_TIME");
			put("fte", "1.00");
		}});

		MvcResult result = mockMvc.perform(post("/api/employees")
						.cookie(new jakarta.servlet.http.Cookie("sos_csrf", "test-csrf-token")).header("X-CSRF-Token", "test-csrf-token")
						.contentType("application/json").content(body))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(response.get("id").asString());
	}

	private record Fixtures(UUID departmentId, UUID locationId, UUID jobFamilyId, UUID jobLevelId, UUID userId) {
	}

	private Fixtures seedFixtures(String suffix) {
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ " + suffix).build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering").code("DEPT-" + suffix).build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-" + suffix).build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer").sortOrder(4).build());
		User user = userRepository.save(User.builder()
				.email("hr-admin-" + suffix.toLowerCase() + "@acme.test")
				.fullName("HR Admin").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());
		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), user.getId());
	}

	private UUID seedAlternateJobLevel(UUID jobFamilyId, String suffix) {
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(jobFamilyId).levelCode("L5").title("Staff Engineer " + suffix).sortOrder(5).build());
		return level.getId();
	}

}
