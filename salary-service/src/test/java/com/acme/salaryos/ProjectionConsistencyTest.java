package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.compensation.domain.CompensationRecord;
import com.acme.salaryos.compensation.domain.EmployeeCurrentComp;
import com.acme.salaryos.compensation.effective.ApplyCommand;
import com.acme.salaryos.compensation.effective.EffectiveDating;
import com.acme.salaryos.compensation.projection.EmployeeCurrentCompProjector;
import com.acme.salaryos.compensation.repository.CompensationRecordRepository;
import com.acme.salaryos.compensation.repository.EmployeeCurrentCompRepository;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.repository.EmployeeRepository;
import com.acme.salaryos.employee.service.EmployeeService;
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
 * P5.2 — Technical-Requirements.md §4.4: "the ledger is the truth... {@code ProjectionConsistencyTest}
 * re-derives the whole projection from the ledger and asserts equality." BuildPlan's Verify names
 * "10k rows"; there is no 10k-row seed fixture yet (P9 hasn't run), so this exercises the same
 * logic — every {@code BandBar} state (in-band, below-min, above-max, no-band) plus a raise and a
 * termination — at a scale this test can seed by hand. The equality check itself doesn't care about
 * row count: it holds because {@link EmployeeCurrentCompProjector#rebuildAll} and the transactional
 * {@link EmployeeCurrentCompProjector#refresh} both derive from the exact same {@code toProjection}
 * logic, so this test would catch the two paths drifting apart at any scale.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class ProjectionConsistencyTest {

	@Autowired
	private EffectiveDating effectiveDating;
	@Autowired
	private EmployeeCurrentCompProjector projector;
	@Autowired
	private EmployeeService employeeService;
	@Autowired
	private EmployeeRepository employeeRepository;
	@Autowired
	private EmployeeCurrentCompRepository employeeCurrentCompRepository;
	@Autowired
	private CompensationRecordRepository compensationRecordRepository;
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
	void rebuildingFromTheLedgerReproducesWhatTheTransactionalRefreshAlreadyWrote() {
		Fixtures fx = seedFixtures("PROJCONSIST");
		saveUsdRate(2032, 1);
		saveUsdRate(2032, 6);

		SalaryBand band = salaryBandRepository.save(SalaryBand.builder()
				.jobLevelId(fx.jobLevelId()).countryCode("US").currency("USD")
				.minAmount(new BigDecimal("90000")).midAmount(new BigDecimal("110000")).maxAmount(new BigDecimal("130000"))
				.effectiveFrom(LocalDate.of(2020, 1, 1)).createdBy(fx.userId())
				.build());

		UUID inBand = hireWithComp(fx, "INBAND", new BigDecimal("105000"));
		UUID belowMin = hireWithComp(fx, "BELOWMIN", new BigDecimal("80000"));
		UUID aboveMax = hireWithComp(fx, "ABOVEMAX", new BigDecimal("190000"));
		UUID noBandLevelEmployee = hireWithCompNoBand(fx, "NOBAND", new BigDecimal("70000"));

		// A raise: the projection must reflect the NEW record, not the one it replaced.
		effectiveDating.apply(new ApplyCommand(
				inBand, LocalDate.of(2032, 6, 1), new BigDecimal("112000"), "USD", "ANNUAL",
				"MERIT", null, fx.userId()));

		// A termination closes the open period with nothing to replace it — no current pay at all.
		UUID terminated = hireWithComp(fx, "TERMPROJ", new BigDecimal("95000"));
		employeeService.terminate(terminated, LocalDate.of(2032, 3, 1));

		List<EmployeeCurrentComp> beforeRebuild = employeeCurrentCompRepository.findAllById(
				List.of(inBand, belowMin, aboveMax, noBandLevelEmployee));
		assertThat(employeeCurrentCompRepository.findById(terminated)).isEmpty();

		// The actual test: wipe the whole table and re-derive it purely from the ledger.
		projector.rebuildAll();

		assertThat(employeeCurrentCompRepository.findById(terminated))
				.as("a terminated employee's closed period has no successor, so rebuildAll must not resurrect a row for them")
				.isEmpty();

		EmployeeCurrentComp rebuiltInBand = employeeCurrentCompRepository.findById(inBand).orElseThrow();
		assertThat(rebuiltInBand.getBase().amount()).isEqualByComparingTo("112000.00");
		assertThat(rebuiltInBand.getBandStatus()).isEqualTo("IN_BAND");
		assertThat(rebuiltInBand.getBandId()).isEqualTo(band.getId());

		EmployeeCurrentComp rebuiltBelowMin = employeeCurrentCompRepository.findById(belowMin).orElseThrow();
		assertThat(rebuiltBelowMin.getBandStatus()).isEqualTo("BELOW_MIN");

		EmployeeCurrentComp rebuiltAboveMax = employeeCurrentCompRepository.findById(aboveMax).orElseThrow();
		assertThat(rebuiltAboveMax.getBandStatus()).isEqualTo("ABOVE_MAX");

		EmployeeCurrentComp rebuiltNoBand = employeeCurrentCompRepository.findById(noBandLevelEmployee).orElseThrow();
		assertThat(rebuiltNoBand.getBandStatus()).isEqualTo("NO_BAND");
		assertThat(rebuiltNoBand.getBandId()).isNull();
		assertThat(rebuiltNoBand.getCompaRatio()).isNull();

		// Full equality against what the transactional path had already written, field by field —
		// this is the "re-derive equals stored" assertion Technical-Requirements.md §4.4 asks for.
		for (EmployeeCurrentComp before : beforeRebuild) {
			EmployeeCurrentComp after = employeeCurrentCompRepository.findById(before.getEmployeeId()).orElseThrow();
			assertThat(after.getCompensationRecordId()).isEqualTo(before.getCompensationRecordId());
			assertThat(after.getBase().amount()).isEqualByComparingTo(before.getBase().amount());
			assertThat(after.getAnnualBaseAmount()).isEqualByComparingTo(before.getAnnualBaseAmount());
			assertThat(after.getNormalizedAnnualBase()).isEqualByComparingTo(before.getNormalizedAnnualBase());
			assertThat(after.getBandId()).isEqualTo(before.getBandId());
			assertThat(after.getBandStatus()).isEqualTo(before.getBandStatus());
			if (before.getCompaRatio() == null) {
				assertThat(after.getCompaRatio()).isNull();
			}
			else {
				assertThat(after.getCompaRatio()).isEqualByComparingTo(before.getCompaRatio());
			}
		}
	}

	private UUID hireWithComp(Fixtures fx, String suffix, BigDecimal amount) {
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-PROJ-" + suffix)
				.firstName("First").lastName("Last")
				.workEmail("proj-" + suffix.toLowerCase() + "@acme.test")
				.departmentId(fx.departmentId()).locationId(fx.locationId())
				.jobFamilyId(fx.jobFamilyId()).jobLevelId(fx.jobLevelId())
				.hireDate(LocalDate.of(2020, 1, 1)).employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());
		effectiveDating.apply(new ApplyCommand(
				employee.getId(), LocalDate.of(2032, 1, 1), amount, "USD", "ANNUAL",
				"INITIAL", null, fx.userId()));
		return employee.getId();
	}

	private UUID hireWithCompNoBand(Fixtures fx, String suffix, BigDecimal amount) {
		JobLevel unbandedLevel = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(fx.jobFamilyId()).levelCode("L1").title("Associate " + suffix).sortOrder(1).build());
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-PROJ-" + suffix)
				.firstName("First").lastName("Last")
				.workEmail("proj-" + suffix.toLowerCase() + "@acme.test")
				.departmentId(fx.departmentId()).locationId(fx.locationId())
				.jobFamilyId(fx.jobFamilyId()).jobLevelId(unbandedLevel.getId())
				.hireDate(LocalDate.of(2020, 1, 1)).employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());
		effectiveDating.apply(new ApplyCommand(
				employee.getId(), LocalDate.of(2032, 1, 1), amount, "USD", "ANNUAL",
				"INITIAL", null, fx.userId()));
		return employee.getId();
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
				.name("Engineering").code("DEPT-" + suffix).build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-" + suffix).build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer " + suffix).sortOrder(4).build());
		User user = userRepository.save(User.builder()
				.email("hr-admin-" + suffix.toLowerCase() + "@acme.test")
				.fullName("HR Admin").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());
		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), user.getId());
	}

}
