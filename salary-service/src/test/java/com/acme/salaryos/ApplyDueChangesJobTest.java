package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.change.ApplyDueChangesJob;
import com.acme.salaryos.change.dto.ApplyDueChangesResult;
import com.acme.salaryos.change.dto.ChangeResponse;
import com.acme.salaryos.change.dto.ProposeChangeRequest;
import com.acme.salaryos.change.service.ChangeNotDueException;
import com.acme.salaryos.change.service.ChangeService;
import com.acme.salaryos.compensation.effective.ApplyCommand;
import com.acme.salaryos.compensation.effective.EffectiveDating;
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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P6.2's own Verify clause: running the job twice writes one record, and a change dated tomorrow
 * is not applied today. Runs against the real injected {@link java.time.Clock} (system UTC, per
 * {@code ClockConfig}) rather than a mocked one — the job only ever compares an effective date
 * against "today", so seeding dates relative to {@code LocalDate.now(ZoneOffset.UTC)} exercises
 * the real boundary without needing a test-only Clock bean. UTC specifically, not the JVM's
 * default zone — a genuine failure was caught here when the dev machine (IST, UTC+5:30) crossed
 * midnight into a new day 5.5 hours before UTC did, so {@code LocalDate.now()} and the job's own
 * UTC "today" briefly disagreed by a full day.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class ApplyDueChangesJobTest {

	@Autowired
	private ChangeService changeService;
	@Autowired
	private ApplyDueChangesJob applyDueChangesJob;
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
	void runningTheJobTwiceAppliesADueChangeExactlyOnce() {
		Fixtures fx = seedFixtures("TWICE");
		UUID employeeId = hireWithComp(fx, "TWICE", new BigDecimal("100000"));
		LocalDate today = LocalDate.now(ZoneOffset.UTC);

		ChangeResponse change = changeService.propose(new ProposeChangeRequest(
				employeeId, today, new BigDecimal("110000"), "USD", "MERIT", null, null), fx.proposerId());
		changeService.submit(change.id());
		changeService.approve(change.id(), fx.approverId(), "Approved.");

		ApplyDueChangesResult first = applyDueChangesJob.run();
		assertThat(first.due()).isEqualTo(1);
		assertThat(first.applied()).isEqualTo(1);
		assertThat(first.failures()).isEmpty();

		ApplyDueChangesResult second = applyDueChangesJob.run();
		assertThat(second.due()).isEqualTo(0);
		assertThat(second.applied()).isEqualTo(0);

		List<ChangeResponse> reloaded = changeService.list(employeeId, "APPLIED", null, null);
		assertThat(reloaded).hasSize(1);
		assertThat(reloaded.get(0).appliedRecordId()).isNotNull();

		assertThat(compensationRecordRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId)).hasSize(2);
	}

	@Test
	void aChangeDatedTomorrowIsNotAppliedToday() {
		Fixtures fx = seedFixtures("TOMORROW");
		UUID employeeId = hireWithComp(fx, "TOMORROW", new BigDecimal("100000"));
		LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);

		ChangeResponse change = changeService.propose(new ProposeChangeRequest(
				employeeId, tomorrow, new BigDecimal("110000"), "USD", "MERIT", null, null), fx.proposerId());
		changeService.submit(change.id());
		changeService.approve(change.id(), fx.approverId(), "Approved.");

		ApplyDueChangesResult result = applyDueChangesJob.run();
		assertThat(result.due()).isEqualTo(0);
		assertThat(result.applied()).isEqualTo(0);

		List<ChangeResponse> stillApproved = changeService.list(employeeId, "APPROVED", null, null);
		assertThat(stillApproved).extracting(ChangeResponse::id).contains(change.id());

		assertThatThrownBy(() -> changeService.applyDueChange(change.id(), LocalDate.now(ZoneOffset.UTC)))
				.isInstanceOf(ChangeNotDueException.class);
	}

	private UUID hireWithComp(Fixtures fx, String suffix, BigDecimal amount) {
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-ADJ-" + suffix)
				.firstName("First").lastName("Last")
				.workEmail("adj-" + suffix.toLowerCase() + "@acme.test")
				.departmentId(fx.departmentId()).locationId(fx.locationId())
				.jobFamilyId(fx.jobFamilyId()).jobLevelId(fx.jobLevelId())
				.hireDate(LocalDate.of(2020, 1, 1)).employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());
		effectiveDating.apply(new ApplyCommand(
				employee.getId(), LocalDate.of(2020, 1, 1), amount, "USD", "ANNUAL", "INITIAL", null, fx.proposerId()));
		return employee.getId();
	}

	private record Fixtures(UUID departmentId, UUID locationId, UUID jobFamilyId, UUID jobLevelId, UUID proposerId, UUID approverId) {
	}

	private Fixtures seedFixtures(String suffix) {
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ ADJ-" + suffix).build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering").code("DEPT-ADJ-" + suffix).build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-ADJ-" + suffix).build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer ADJ-" + suffix).sortOrder(4).build());
		User proposer = userRepository.save(User.builder()
				.email("proposer-adj-" + suffix.toLowerCase() + "@acme.test")
				.fullName("Proposer").passwordHash("{argon2}stub").role("HR_MANAGER")
				.build());
		User approver = userRepository.save(User.builder()
				.email("approver-adj-" + suffix.toLowerCase() + "@acme.test")
				.fullName("Approver").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());

		seedRateIfMissing(LocalDate.of(2020, 1, 1));
		// The applied change's effective date is "today" or "tomorrow" (real clock), so both
		// months' rates must exist — a run near a month boundary would otherwise span two.
		seedRateIfMissing(YearMonth.now(ZoneOffset.UTC).atDay(1));
		seedRateIfMissing(YearMonth.now(ZoneOffset.UTC).plusMonths(1).atDay(1));

		salaryBandRepository.save(SalaryBand.builder()
				.jobLevelId(level.getId()).countryCode("US").currency("USD")
				.minAmount(new BigDecimal("90000")).midAmount(new BigDecimal("110000")).maxAmount(new BigDecimal("130000"))
				.effectiveFrom(LocalDate.of(2020, 1, 1)).createdBy(approver.getId())
				.build());

		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), proposer.getId(), approver.getId());
	}

	private void seedRateIfMissing(LocalDate rateMonth) {
		fxRateRepository.findByBaseCurrencyAndQuoteCurrencyAndRateMonth("USD", "USD", rateMonth)
				.orElseGet(() -> fxRateRepository.save(FxRate.builder()
						.rateMonth(rateMonth).baseCurrency("USD").quoteCurrency("USD").rate(BigDecimal.ONE)
						.build()));
	}

}
