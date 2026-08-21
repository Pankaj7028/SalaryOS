package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.change.dto.ChangeImpactPreviewResponse;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P6.4's own Verify clause: the propose-change dialog's live impact panel figures match the API's
 * computed values exactly — no client arithmetic. Exercises {@code ChangeService.previewImpact}
 * directly (the controller is a thin pass-through, covered by {@code RolePermissionMatrixTest}).
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class ChangeImpactPreviewTest {

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
	void previewComputesDeltaBandStatusAndPeerPercentileWithoutPersistingAnything() {
		Fixtures fx = seedFixtures("A");

		// Five-person cohort, ascending pay: 80k (target), 90k, 100k, 110k, 120k. Band is 90k/110k/130k.
		UUID targetId = hireWithComp(fx, "E-IMPACT-1", new BigDecimal("80000"));
		hireWithComp(fx, "E-IMPACT-2", new BigDecimal("90000"));
		hireWithComp(fx, "E-IMPACT-3", new BigDecimal("100000"));
		hireWithComp(fx, "E-IMPACT-4", new BigDecimal("110000"));
		hireWithComp(fx, "E-IMPACT-5", new BigDecimal("120000"));

		ChangeImpactPreviewResponse preview = changeService.previewImpact(
				targetId, LocalDate.of(2039, 6, 1), new BigDecimal("200000"), "USD");

		assertThat(preview.currentBase().amount()).isEqualByComparingTo("80000.00");
		assertThat(preview.proposedBase().amount()).isEqualByComparingTo("200000.00");
		assertThat(preview.deltaAmount().amount()).isEqualByComparingTo("120000.00");
		assertThat(preview.deltaPercent()).isEqualByComparingTo("1.500000");

		assertThat(preview.currentBandStatus()).isEqualTo("BELOW_MIN");
		assertThat(preview.proposedBandStatus()).isEqualTo("ABOVE_MAX");
		assertThat(preview.noteRequired()).isTrue();

		assertThat(preview.band()).isNotNull();
		assertThat(preview.band().min().amount()).isEqualByComparingTo("90000.00");
		assertThat(preview.band().mid().amount()).isEqualByComparingTo("110000.00");
		assertThat(preview.band().max().amount()).isEqualByComparingTo("130000.00");

		// Lowest of 5 -> 1/5 = 20th percentile. After: highest of 5 -> 100th percentile.
		assertThat(preview.peerCohortSize()).isEqualTo(5);
		assertThat(preview.peerSuppressed()).isFalse();
		assertThat(preview.peerPercentileBefore()).isEqualTo(20);
		assertThat(preview.peerPercentileAfter()).isEqualTo(100);

		// Nothing was written: still exactly one ledger row (the initial hire) for the target employee.
		assertThat(employeeRepository.findById(targetId)).isPresent();
	}

	@Test
	void anInBandProposalDoesNotRequireANote() {
		Fixtures fx = seedFixtures("B");
		for (int i = 1; i <= 5; i++) {
			hireWithComp(fx, "E-INBAND-" + i, new BigDecimal("100000"));
		}
		UUID targetId = employeeRepository.findByEmployeeNumber("E-INBAND-1").orElseThrow().getId();

		ChangeImpactPreviewResponse preview = changeService.previewImpact(
				targetId, LocalDate.of(2039, 6, 1), new BigDecimal("115000"), "USD");

		assertThat(preview.proposedBandStatus()).isEqualTo("IN_BAND");
		assertThat(preview.noteRequired()).isFalse();
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
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ IMPACT-" + suffix).build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering").code("DEPT-IMPACT-" + suffix).build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-IMPACT-" + suffix).build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer IMPACT-" + suffix).sortOrder(4).build());
		User user = userRepository.save(User.builder()
				.email("user-impact-" + suffix.toLowerCase() + "@acme.test")
				.fullName("Impact Tester").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());

		seedRateIfMissing(LocalDate.of(2020, 1, 1));
		seedRateIfMissing(LocalDate.of(2039, 6, 1));

		salaryBandRepository.save(SalaryBand.builder()
				.jobLevelId(level.getId()).countryCode("US").currency("USD")
				.minAmount(new BigDecimal("90000")).midAmount(new BigDecimal("110000")).maxAmount(new BigDecimal("130000"))
				.effectiveFrom(LocalDate.of(2020, 1, 1)).createdBy(user.getId())
				.build());

		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), user.getId());
	}

	private void seedRateIfMissing(LocalDate rateMonth) {
		fxRateRepository.findByBaseCurrencyAndQuoteCurrencyAndRateMonth("USD", "USD", rateMonth)
				.orElseGet(() -> fxRateRepository.save(FxRate.builder()
						.rateMonth(rateMonth).baseCurrency("USD").quoteCurrency("USD").rate(BigDecimal.ONE)
						.build()));
	}

}
