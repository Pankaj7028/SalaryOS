package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.change.dto.ChangeResponse;
import com.acme.salaryos.change.dto.ProposeChangeRequest;
import com.acme.salaryos.change.service.ChangeService;
import com.acme.salaryos.change.service.SelfApprovalException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P6.1's own Verify clause, named exactly as BuildPlan.md calls it out: FR-5.5, self-approval is 403. */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class ProposerIsNotApproverTest {

	@Autowired
	private ChangeService changeService;
	@Autowired
	private EffectiveDating effectiveDating;
	@Autowired
	private EmployeeRepository employeeRepository;
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
	void theSameUserWhoProposedCannotApproveTheirOwnChange() {
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ PROPAPPR").build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering").code("DEPT-PROPAPPR").build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-PROPAPPR").build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer PROPAPPR").sortOrder(4).build());
		User proposerAndWouldBeApprover = userRepository.save(User.builder()
				.email("propappr@acme.test")
				.fullName("HR Manager").passwordHash("{argon2}stub").role("HR_MANAGER")
				.build());
		LocalDate rateMonth = LocalDate.of(2036, 1, 1);
		fxRateRepository.findByBaseCurrencyAndQuoteCurrencyAndRateMonth("USD", "USD", rateMonth)
				.orElseGet(() -> fxRateRepository.save(FxRate.builder()
						.rateMonth(rateMonth).baseCurrency("USD").quoteCurrency("USD").rate(BigDecimal.ONE).build()));
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-PROPAPPR")
				.firstName("First").lastName("Last")
				.workEmail("propappr-employee@acme.test")
				.departmentId(department.getId()).locationId(location.getId())
				.jobFamilyId(family.getId()).jobLevelId(level.getId())
				.hireDate(LocalDate.of(2020, 1, 1)).employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());
		effectiveDating.apply(new ApplyCommand(
				employee.getId(), LocalDate.of(2036, 1, 1), new BigDecimal("100000"), "USD", "ANNUAL",
				"INITIAL", null, proposerAndWouldBeApprover.getId()));

		ChangeResponse change = changeService.propose(new ProposeChangeRequest(
				employee.getId(), LocalDate.of(2036, 6, 1), new BigDecimal("110000"), "USD", "MERIT", null, null),
				proposerAndWouldBeApprover.getId());
		changeService.submit(change.id());

		assertThatThrownBy(() -> changeService.approve(change.id(), proposerAndWouldBeApprover.getId(), null))
				.isInstanceOf(SelfApprovalException.class)
				.hasMessage("You proposed this change, so someone else has to approve it.");
	}

}
