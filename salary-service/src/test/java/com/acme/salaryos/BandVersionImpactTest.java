package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.dto.BandResponse;
import com.acme.salaryos.band.dto.BandVersionImpactResponse;
import com.acme.salaryos.band.dto.CreateBandRequest;
import com.acme.salaryos.band.dto.UpdateBandRequest;
import com.acme.salaryos.band.service.BandService;
import com.acme.salaryos.compensation.effective.ApplyCommand;
import com.acme.salaryos.compensation.effective.EffectiveDating;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P5.5 — ui doc §8.6: "Creating a new version shows how many employees change status as a result
 * before saving." The Verify clause is specifically that this preview count matches what re-deriving
 * status against the saved version would actually find — not just that some number is shown.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class BandVersionImpactTest {

	@Autowired
	private BandService bandService;
	@Autowired
	private EffectiveDating effectiveDating;
	@Autowired
	private EmployeeCurrentCompRepository employeeCurrentCompRepository;
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
	@Autowired
	private FxRateRepository fxRateRepository;

	@Test
	void previewCountsExactlyTheEmployeesWhoseStatusWouldChangeUnderTheProposedBoundaries() {
		Fixtures fx = seedFixtures("IMPACT");
		saveUsdRate(2035, 1);

		BandResponse band = bandService.create(new CreateBandRequest(
				fx.jobLevelId(), "US", "USD", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"),
				LocalDate.of(2035, 1, 1), null), fx.userId());

		// Three employees against the ORIGINAL 90k-130k band:
		// - inBandStaysInBand: 100000, comfortably inside both old and new bands.
		// - belowMinBecomesInBand: 92000, BELOW_MIN under the old 90k floor... actually above it;
		//   use 88000 so it starts BELOW_MIN, then the new 80k floor brings it IN_BAND.
		// - inBandBecomesAboveMax: 125000, IN_BAND under the old 130k ceiling, ABOVE_MAX under a new 120k ceiling.
		hireWithComp(fx, "STAYS", new BigDecimal("100000"));
		hireWithComp(fx, "CHANGES1", new BigDecimal("88000"));
		hireWithComp(fx, "CHANGES2", new BigDecimal("125000"));

		BigDecimal newMin = new BigDecimal("80000");
		BigDecimal newMid = new BigDecimal("100000");
		BigDecimal newMax = new BigDecimal("120000");

		BandVersionImpactResponse preview = bandService.previewVersionImpact(band.id(), newMin, newMid, newMax);
		assertThat(preview.cohortSize()).isEqualTo(3);
		assertThat(preview.changingStatus()).isEqualTo(2);

		// Now actually save that version and re-derive independently — the preview must have matched.
		bandService.update(band.id(), new UpdateBandRequest("USD", newMin, newMid, newMax, LocalDate.of(2035, 6, 1), "Market correction."), fx.userId());

		long actuallyChanged = employeeCurrentCompRepository.findByBandId(band.id()).stream()
				.filter(comp -> !EffectiveDating.bandStatus(
						comp.getAnnualBaseAmount(),
						SalaryBand.builder().minAmount(newMin).midAmount(newMid).maxAmount(newMax).build())
						.equals(comp.getBandStatus()))
				.count();
		assertThat(actuallyChanged).isEqualTo(preview.changingStatus());
	}

	@Test
	void headcountReflectsOnlyEmployeesProjectedAgainstTheInForceVersion() {
		Fixtures fx = seedFixtures("HEADCOUNT");
		saveUsdRate(2035, 1);

		BandResponse band = bandService.create(new CreateBandRequest(
				fx.jobLevelId(), "US", "USD", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"),
				LocalDate.of(2035, 1, 1), null), fx.userId());
		hireWithComp(fx, "HC1", new BigDecimal("100000"));
		hireWithComp(fx, "HC2", new BigDecimal("105000"));

		List<BandResponse> afterHire = bandService.list();
		BandResponse reloaded = afterHire.stream().filter(b -> b.id().equals(band.id())).findFirst().orElseThrow();
		assertThat(reloaded.headcount()).isEqualTo(2);
	}

	private void hireWithComp(Fixtures fx, String suffix, BigDecimal amount) {
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-IMPACT-" + suffix)
				.firstName("First").lastName("Last")
				.workEmail("impact-" + suffix.toLowerCase() + "@acme.test")
				.departmentId(fx.departmentId()).locationId(fx.locationId())
				.jobFamilyId(fx.jobFamilyId()).jobLevelId(fx.jobLevelId())
				.hireDate(LocalDate.of(2020, 1, 1)).employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());
		effectiveDating.apply(new ApplyCommand(
				employee.getId(), LocalDate.of(2035, 1, 1), amount, "USD", "ANNUAL",
				"INITIAL", null, fx.userId()));
	}

	private void saveUsdRate(int year, int month) {
		LocalDate rateMonth = LocalDate.of(year, month, 1);
		fxRateRepository.findByBaseCurrencyAndQuoteCurrencyAndRateMonth("USD", "USD", rateMonth)
				.orElseGet(() -> fxRateRepository.save(FxRate.builder()
						.rateMonth(rateMonth).baseCurrency("USD").quoteCurrency("USD").rate(BigDecimal.ONE)
						.build()));
	}

	private record Fixtures(UUID departmentId, UUID locationId, UUID jobFamilyId, UUID jobLevelId, UUID userId) {
	}

	private Fixtures seedFixtures(String suffix) {
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ " + suffix).build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering").code("DEPT-IMPACT-" + suffix).build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-IMPACT-" + suffix).build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer IMPACT-" + suffix).sortOrder(4).build());
		User user = userRepository.save(User.builder()
				.email("hr-admin-impact-" + suffix.toLowerCase() + "@acme.test")
				.fullName("HR Admin").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());
		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), user.getId());
	}

}
