package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.compensation.domain.CompensationRecord;
import com.acme.salaryos.compensation.effective.ApplyCommand;
import com.acme.salaryos.compensation.effective.BackdatedBeforeOpenPeriodException;
import com.acme.salaryos.compensation.effective.CorrectCommand;
import com.acme.salaryos.compensation.effective.CorrectionOutsideOriginalPeriodException;
import com.acme.salaryos.compensation.effective.EffectiveDating;
import com.acme.salaryos.compensation.effective.MissingCorrectionNoteException;
import com.acme.salaryos.compensation.effective.MissingFxRateException;
import com.acme.salaryos.compensation.repository.CompensationRecordRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P5.1 — the highest-value test class in the build (backend doc §3). Covers every rule the class
 * enforces: day-boundary closing, backdating rejection, correction supersede, FTE annualisation
 * (including the HOURLY exception to it), a missing FX rate, and the NO_BAND-never-defaults-to-1.0
 * rule.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class EffectiveDatingTest {

	@Autowired
	private EffectiveDating effectiveDating;
	@Autowired
	private EmployeeRepository employeeRepository;
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
	void applyingTheFirstRecordOpensAPeriodWithNothingToClose() {
		Fixtures fx = seedFixtures("FIRSTHIRE", new BigDecimal("1.00"));
		saveUsdRate(2031, 1);

		CompensationRecord first = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 1, 15), new BigDecimal("100000"), "USD", "ANNUAL",
				"INITIAL", null, fx.userId()));

		assertThat(first.getEffectiveTo()).isNull();
		assertThat(first.getAnnualBaseAmount()).isEqualByComparingTo("100000.00");
		assertThat(compensationRecordRepository.findByEmployeeIdAndEffectiveToIsNull(fx.employeeId()))
				.map(CompensationRecord::getId).contains(first.getId());
	}

	@Test
	void applyingARaiseClosesTheOldPeriodExactlyWhereTheNewOneBegins() {
		Fixtures fx = seedFixtures("DAYBOUNDARY", new BigDecimal("1.00"));
		saveUsdRate(2031, 1);
		saveUsdRate(2031, 6);

		CompensationRecord original = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 1, 1), new BigDecimal("100000"), "USD", "ANNUAL",
				"INITIAL", null, fx.userId()));

		CompensationRecord raise = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 6, 1), new BigDecimal("110000"), "USD", "ANNUAL",
				"MERIT", null, fx.userId()));

		CompensationRecord reloadedOriginal = compensationRecordRepository.findById(original.getId()).orElseThrow();
		// effective_to = the new period's effective_from, NOT minusDays(1) — validity is a `[)`
		// range, so this is what makes [Jan 1, Jun 1) butt exactly against [Jun 1, ...) with no gap.
		assertThat(reloadedOriginal.getEffectiveTo()).isEqualTo(LocalDate.of(2031, 6, 1));
		assertThat(raise.getEffectiveFrom()).isEqualTo(LocalDate.of(2031, 6, 1));
		assertThat(raise.getEffectiveTo()).isNull();
	}

	@Test
	void applyingOnOrBeforeTheOpenPeriodsStartIsRejected() {
		Fixtures fx = seedFixtures("BACKDATE", new BigDecimal("1.00"));
		saveUsdRate(2031, 3);

		effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 3, 1), new BigDecimal("100000"), "USD", "ANNUAL",
				"INITIAL", null, fx.userId()));

		assertThatThrownBy(() -> effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 3, 1), new BigDecimal("105000"), "USD", "ANNUAL",
				"MERIT", null, fx.userId())))
				.isInstanceOf(BackdatedBeforeOpenPeriodException.class)
				.hasMessageContaining("2031-03-01");

		assertThatThrownBy(() -> effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 2, 1), new BigDecimal("105000"), "USD", "ANNUAL",
				"MERIT", null, fx.userId())))
				.isInstanceOf(BackdatedBeforeOpenPeriodException.class);
	}

	@Test
	void correctingInsertsInsideTheOriginalPeriodAndSupersedesIt() {
		Fixtures fx = seedFixtures("CORRECT", new BigDecimal("1.00"));
		saveUsdRate(2031, 4);

		CompensationRecord original = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 4, 1), new BigDecimal("95000"), "USD", "ANNUAL",
				"INITIAL", null, fx.userId()));

		CompensationRecord corrected = effectiveDating.correct(new CorrectCommand(
				original.getId(), LocalDate.of(2031, 4, 10), new BigDecimal("105000"), "USD", "ANNUAL",
				"Entered the wrong offer amount at hire.", fx.userId()));

		CompensationRecord reloadedOriginal = compensationRecordRepository.findById(original.getId()).orElseThrow();
		assertThat(reloadedOriginal.getSupersededBy()).isEqualTo(corrected.getId());
		assertThat(reloadedOriginal.getEffectiveTo()).isEqualTo(LocalDate.of(2031, 4, 10));
		assertThat(corrected.getChangeReason()).isEqualTo("CORRECTION");
		assertThat(corrected.getEffectiveTo()).isNull();
		// The original row still exists, untouched apart from close()/supersede() — insert-only.
		assertThat(reloadedOriginal.getBase().amount()).isEqualByComparingTo("95000.00");
	}

	@Test
	void correctionWithoutANoteIsRejected() {
		Fixtures fx = seedFixtures("NONOTE", new BigDecimal("1.00"));
		saveUsdRate(2031, 4);

		CompensationRecord original = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 4, 1), new BigDecimal("95000"), "USD", "ANNUAL",
				"INITIAL", null, fx.userId()));

		assertThatThrownBy(() -> effectiveDating.correct(new CorrectCommand(
				original.getId(), LocalDate.of(2031, 4, 10), new BigDecimal("105000"), "USD", "ANNUAL",
				"  ", fx.userId())))
				.isInstanceOf(MissingCorrectionNoteException.class);
	}

	@Test
	void correctionDatedAtOrBeforeTheOriginalPeriodsStartIsRejected() {
		Fixtures fx = seedFixtures("CORRECTBACKDATE", new BigDecimal("1.00"));
		saveUsdRate(2031, 4);

		CompensationRecord original = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 4, 1), new BigDecimal("95000"), "USD", "ANNUAL",
				"INITIAL", null, fx.userId()));

		assertThatThrownBy(() -> effectiveDating.correct(new CorrectCommand(
				original.getId(), LocalDate.of(2031, 4, 1), new BigDecimal("105000"), "USD", "ANNUAL",
				"Cannot correct from day one — no day left to close the original on.", fx.userId())))
				.isInstanceOf(BackdatedBeforeOpenPeriodException.class);
	}

	@Test
	void correctionDatedAtOrAfterAClosedOriginalPeriodsEndIsRejected() {
		Fixtures fx = seedFixtures("CORRECTAFTERCLOSE", new BigDecimal("1.00"));
		saveUsdRate(2031, 1);
		saveUsdRate(2031, 6);

		CompensationRecord original = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 1, 1), new BigDecimal("95000"), "USD", "ANNUAL",
				"INITIAL", null, fx.userId()));
		effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 6, 1), new BigDecimal("105000"), "USD", "ANNUAL",
				"MERIT", null, fx.userId()));

		CompensationRecord reloadedOriginal = compensationRecordRepository.findById(original.getId()).orElseThrow();
		assertThat(reloadedOriginal.getEffectiveTo()).isEqualTo(LocalDate.of(2031, 6, 1));

		assertThatThrownBy(() -> effectiveDating.correct(new CorrectCommand(
				original.getId(), LocalDate.of(2031, 6, 1), new BigDecimal("96000"), "USD", "ANNUAL",
				"Attempting to correct on the closing date itself.", fx.userId())))
				.isInstanceOf(CorrectionOutsideOriginalPeriodException.class);
	}

	@Test
	void annualBaseIsGrossedToItsFullTimeEquivalentForAnnualAndMonthlyFrequencies() {
		Fixtures halfTime = seedFixtures("FTEHALF", new BigDecimal("0.50"));
		saveUsdRate(2031, 1);

		CompensationRecord annual = effectiveDating.apply(new ApplyCommand(
				halfTime.employeeId(), LocalDate.of(2031, 1, 1), new BigDecimal("60000"), "USD", "ANNUAL",
				"INITIAL", null, halfTime.userId()));
		assertThat(annual.getAnnualBaseAmount()).isEqualByComparingTo("120000.00");

		Fixtures quarterTime = seedFixtures("FTEQUARTER", new BigDecimal("0.25"));
		CompensationRecord monthly = effectiveDating.apply(new ApplyCommand(
				quarterTime.employeeId(), LocalDate.of(2031, 1, 1), new BigDecimal("2000"), "USD", "MONTHLY",
				"INITIAL", null, quarterTime.userId()));
		// 2000 * 12 = 24000 actual annual; grossed to FTE=1.0: 24000 / 0.25 = 96000.
		assertThat(monthly.getAnnualBaseAmount()).isEqualByComparingTo("96000.00");
	}

	@Test
	void hourlyAnnualisationUsesTheStandardYearWithoutDividingByFteAgain() {
		Fixtures fx = seedFixtures("HOURLY", new BigDecimal("0.50"));
		saveUsdRate(2031, 1);

		CompensationRecord record = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 1, 1), new BigDecimal("50.00"), "USD", "HOURLY",
				"INITIAL", null, fx.userId()));

		// $50/hr * 2080 standard annual hours = $104,000 — already the FTE=1.0 equivalent, no
		// further division by this employee's 0.5 FTE (that would double-count).
		assertThat(record.getAnnualBaseAmount()).isEqualByComparingTo("104000.00");
	}

	@Test
	void missingFxRateIsRejectedRatherThanDefaultingToOne() {
		Fixtures fx = seedFixtures("NOFXRATE", new BigDecimal("1.00"));
		// Deliberately no fx_rates row saved for this month.

		assertThatThrownBy(() -> effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 8, 1), new BigDecimal("100000"), "USD", "ANNUAL",
				"INITIAL", null, fx.userId())))
				.isInstanceOf(MissingFxRateException.class)
				.hasMessageContaining("2031-08");
	}

	@Test
	void noBandLeavesCompaRatioNullRatherThanDefaultingToOne() {
		Fixtures fx = seedFixtures("NOBAND", new BigDecimal("1.00"));
		saveUsdRate(2031, 1);
		// Deliberately no salary_bands row for this job level × country.

		CompensationRecord record = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 1, 1), new BigDecimal("100000"), "USD", "ANNUAL",
				"INITIAL", null, fx.userId()));

		assertThat(record.getBandId()).isNull();
		assertThat(record.getCompaRatio()).isNull();
		assertThat(record.getRangePenetration()).isNull();
		assertThat(EffectiveDating.bandStatus(record.getAnnualBaseAmount(), null)).isEqualTo("NO_BAND");
	}

	@Test
	void aboveMaxRangePenetrationExceedsOneHundredWithoutOverflowing() {
		Fixtures fx = seedFixtures("ABOVEMAX", new BigDecimal("1.00"));
		saveUsdRate(2031, 1);
		SalaryBand band = salaryBandRepository.save(SalaryBand.builder()
				.jobLevelId(fx.jobLevelId()).countryCode("US").currency("USD")
				.minAmount(new BigDecimal("90000")).midAmount(new BigDecimal("110000")).maxAmount(new BigDecimal("130000"))
				.effectiveFrom(LocalDate.of(2020, 1, 1)).createdBy(fx.userId())
				.build());

		// (190000 - 90000) / (130000 - 90000) * 100 = 250% — a penetration figure this large is
		// exactly what V13's numeric(6,4) -> numeric(8,4) widening exists to store without a DB
		// overflow (numeric(6,4) tops out just under 100).
		CompensationRecord record = effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 1, 1), new BigDecimal("190000"), "USD", "ANNUAL",
				"MARKET_ADJUSTMENT", null, fx.userId()));

		assertThat(record.getBandId()).isEqualTo(band.getId());
		assertThat(record.getRangePenetration()).isEqualByComparingTo("250.0000");
		assertThat(EffectiveDating.bandStatus(record.getAnnualBaseAmount(), band)).isEqualTo("ABOVE_MAX");
	}

	@Test
	void applyingClearsAnExistingBandMismatchFlag() {
		Fixtures fx = seedFixtures("CLEARMISMATCH", new BigDecimal("1.00"));
		saveUsdRate(2031, 1);
		Employee employee = employeeRepository.findById(fx.employeeId()).orElseThrow();
		employee.updateProfile(
				employee.getFirstName(), employee.getLastName(), employee.getWorkEmail(), employee.getDepartmentId(),
				employee.getLocationId(), employee.getJobFamilyId(), employee.getJobLevelId(), employee.getManagerId(),
				employee.getEmploymentType(), employee.getFte());
		// updateProfile only flips bandMismatched when level/location actually change; force it
		// directly via a second profile update against a different level to exercise the real flag.
		JobLevel otherLevel = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(employee.getJobFamilyId()).levelCode("L6").title("Principal " + fx.employeeId()).sortOrder(6).build());
		employee.updateProfile(
				employee.getFirstName(), employee.getLastName(), employee.getWorkEmail(), employee.getDepartmentId(),
				employee.getLocationId(), employee.getJobFamilyId(), otherLevel.getId(), employee.getManagerId(),
				employee.getEmploymentType(), employee.getFte());
		employeeRepository.save(employee);
		assertThat(employeeRepository.findById(fx.employeeId()).orElseThrow().isBandMismatched()).isTrue();

		effectiveDating.apply(new ApplyCommand(
				fx.employeeId(), LocalDate.of(2031, 1, 1), new BigDecimal("100000"), "USD", "ANNUAL",
				"MARKET_ADJUSTMENT", null, fx.userId()));

		assertThat(employeeRepository.findById(fx.employeeId()).orElseThrow().isBandMismatched()).isFalse();
	}

	/** Idempotent: {@code fx_rates} has a real unique constraint per (month, base, quote), and several tests in this class need the same USD→USD identity rate for the same month. */
	private void saveUsdRate(int year, int month) {
		LocalDate rateMonth = LocalDate.of(year, month, 1);
		fxRateRepository.findByBaseCurrencyAndQuoteCurrencyAndRateMonth("USD", "USD", rateMonth)
				.orElseGet(() -> fxRateRepository.save(FxRate.builder()
						.rateMonth(rateMonth).baseCurrency("USD").quoteCurrency("USD").rate(BigDecimal.ONE)
						.build()));
	}

	private record Fixtures(UUID employeeId, UUID jobLevelId, UUID userId) {
	}

	private Fixtures seedFixtures(String suffix, BigDecimal fte) {
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
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-ED-" + suffix)
				.firstName("First").lastName("Last")
				.workEmail("ed-" + suffix.toLowerCase() + "@acme.test")
				.departmentId(department.getId()).locationId(location.getId())
				.jobFamilyId(family.getId()).jobLevelId(level.getId())
				.hireDate(LocalDate.of(2020, 1, 1)).employmentType("FULL_TIME").fte(fte)
				.build());
		return new Fixtures(employee.getId(), level.getId(), user.getId());
	}

}
