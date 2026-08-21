package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.change.dto.ChangeResponse;
import com.acme.salaryos.change.dto.ProposeChangeRequest;
import com.acme.salaryos.change.dto.UpdateDraftRequest;
import com.acme.salaryos.change.service.ChangeCurrencyMismatchException;
import com.acme.salaryos.change.service.ChangeNoteRequiredException;
import com.acme.salaryos.change.service.ChangeNotDraftException;
import com.acme.salaryos.change.service.ChangeNotPendingException;
import com.acme.salaryos.change.service.ChangeService;
import com.acme.salaryos.change.service.NoCurrentCompensationException;
import com.acme.salaryos.change.service.OpenChangeAlreadyExistsException;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P6.1 — FR-5: the proposal/approval lifecycle. Covers the step's own Verify clause
 * (self-approval 403, a second proposal 409 naming the open change) plus the rest of the
 * DRAFT → PENDING → APPROVED/REJECTED transitions and the note-required rules.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class ChangeLifecycleTest {

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
	void proposingSnapshotsCurrentPayAndStartsAsADraft() {
		Fixtures fx = seedFixtures("SNAPSHOT", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"));
		UUID employeeId = hireWithComp(fx, "SNAPSHOT", new BigDecimal("100000"));

		ChangeResponse change = changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("110000"), "USD", "MERIT", "EXCEEDS", null),
				fx.proposerId());

		assertThat(change.status()).isEqualTo("DRAFT");
		assertThat(change.currentBase().amount()).isEqualByComparingTo("100000.00");
		assertThat(change.newBase().amount()).isEqualByComparingTo("110000.00");
		assertThat(change.proposedBy()).isEqualTo(fx.proposerId());
	}

	@Test
	void aSecondProposalForTheSameEmployeeIsRejectedWithTheOpenChangeId() {
		Fixtures fx = seedFixtures("DUPLICATE", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"));
		UUID employeeId = hireWithComp(fx, "DUPLICATE", new BigDecimal("100000"));

		ChangeResponse first = changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("110000"), "USD", "MERIT", null, null), fx.proposerId());

		assertThatThrownBy(() -> changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 7, 1), new BigDecimal("115000"), "USD", "MERIT", null, null), fx.proposerId()))
				.isInstanceOf(OpenChangeAlreadyExistsException.class)
				.satisfies(e -> assertThat(((OpenChangeAlreadyExistsException) e).getOpenChangeId()).isEqualTo(first.id()));
	}

	@Test
	void theProposerCannotApproveTheirOwnProposal() {
		Fixtures fx = seedFixtures("SELFAPPROVE", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"));
		UUID employeeId = hireWithComp(fx, "SELFAPPROVE", new BigDecimal("100000"));

		ChangeResponse change = changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("110000"), "USD", "MERIT", null, null), fx.proposerId());
		changeService.submit(change.id());

		assertThatThrownBy(() -> changeService.approve(change.id(), fx.proposerId(), null))
				.isInstanceOf(SelfApprovalException.class);

		// A different approver succeeds.
		ChangeResponse approved = changeService.approve(change.id(), fx.approverId(), "Looks right.");
		assertThat(approved.status()).isEqualTo("APPROVED");
		assertThat(approved.decidedBy()).isEqualTo(fx.approverId());
	}

	@Test
	void rejectingSetsStatusAndDecisionFields() {
		Fixtures fx = seedFixtures("REJECT", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"));
		UUID employeeId = hireWithComp(fx, "REJECT", new BigDecimal("100000"));

		ChangeResponse change = changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("110000"), "USD", "MERIT", null, null), fx.proposerId());
		changeService.submit(change.id());

		ChangeResponse rejected = changeService.reject(change.id(), fx.approverId(), "Not this cycle.");
		assertThat(rejected.status()).isEqualTo("REJECTED");
		assertThat(rejected.decisionNote()).isEqualTo("Not this cycle.");

		// A rejected change is no longer "open" -- a new proposal for the same employee is fine.
		ChangeResponse next = changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 8, 1), new BigDecimal("108000"), "USD", "MERIT", null, null), fx.proposerId());
		assertThat(next.status()).isEqualTo("DRAFT");
	}

	@Test
	void onlyADraftCanBeEditedSubmittedOrDiscarded() {
		Fixtures fx = seedFixtures("DRAFTONLY", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"));
		UUID employeeId = hireWithComp(fx, "DRAFTONLY", new BigDecimal("100000"));

		ChangeResponse change = changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("110000"), "USD", "MERIT", null, null), fx.proposerId());

		ChangeResponse edited = changeService.updateDraft(change.id(), new UpdateDraftRequest(
				LocalDate.of(2036, 6, 15), new BigDecimal("112000"), "USD", "MERIT", null, null));
		assertThat(edited.newBase().amount()).isEqualByComparingTo("112000.00");

		changeService.submit(change.id());

		assertThatThrownBy(() -> changeService.updateDraft(change.id(), new UpdateDraftRequest(
				LocalDate.of(2036, 6, 20), new BigDecimal("113000"), "USD", "MERIT", null, null)))
				.isInstanceOf(ChangeNotDraftException.class);
		assertThatThrownBy(() -> changeService.discardDraft(change.id()))
				.isInstanceOf(ChangeNotDraftException.class);
	}

	@Test
	void onlyAPendingChangeCanBeApprovedOrRejected() {
		Fixtures fx = seedFixtures("PENDINGONLY", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"));
		UUID employeeId = hireWithComp(fx, "PENDINGONLY", new BigDecimal("100000"));

		ChangeResponse draft = changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("110000"), "USD", "MERIT", null, null), fx.proposerId());

		assertThatThrownBy(() -> changeService.approve(draft.id(), fx.approverId(), null))
				.isInstanceOf(ChangeNotPendingException.class);
		assertThatThrownBy(() -> changeService.reject(draft.id(), fx.approverId(), null))
				.isInstanceOf(ChangeNotPendingException.class);
	}

	@Test
	void discardingADraftRemovesItAndFreesTheEmployeeForANewProposal() {
		Fixtures fx = seedFixtures("DISCARD", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"));
		UUID employeeId = hireWithComp(fx, "DISCARD", new BigDecimal("100000"));

		ChangeResponse draft = changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("110000"), "USD", "MERIT", null, null), fx.proposerId());
		changeService.discardDraft(draft.id());

		ChangeResponse next = changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 7, 1), new BigDecimal("108000"), "USD", "MERIT", null, null), fx.proposerId());
		assertThat(next.status()).isEqualTo("DRAFT");
	}

	@Test
	void aCorrectionRequiresANote() {
		Fixtures fx = seedFixtures("CORRECTIONNOTE", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"));
		UUID employeeId = hireWithComp(fx, "CORRECTIONNOTE", new BigDecimal("100000"));

		assertThatThrownBy(() -> changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("102000"), "USD", "CORRECTION", null, null), fx.proposerId()))
				.isInstanceOf(ChangeNoteRequiredException.class);

		ChangeResponse withNote = changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("102000"), "USD", "CORRECTION", null,
				"Data entry error at hire."), fx.proposerId());
		assertThat(withNote.status()).isEqualTo("DRAFT");
	}

	@Test
	void aProposalLandingOutsideTheBandRequiresANote() {
		Fixtures fx = seedFixtures("OUTOFBAND", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"));
		UUID employeeId = hireWithComp(fx, "OUTOFBAND", new BigDecimal("100000"));

		// 200000 is well above the 130000 max.
		assertThatThrownBy(() -> changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("200000"), "USD", "MARKET_ADJUSTMENT", null, null), fx.proposerId()))
				.isInstanceOf(ChangeNoteRequiredException.class);

		ChangeResponse withNote = changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("200000"), "USD", "MARKET_ADJUSTMENT", null,
				"Competing offer, approved by VP."), fx.proposerId());
		assertThat(withNote.status()).isEqualTo("DRAFT");

		// Staying inside the band needs no note.
		changeService.discardDraft(withNote.id());
		ChangeResponse inBand = changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("115000"), "USD", "MERIT", null, null), fx.proposerId());
		assertThat(inBand.status()).isEqualTo("DRAFT");
	}

	@Test
	void proposingAgainstAnEmployeeWithNoCurrentCompensationIsRejected() {
		Fixtures fx = seedFixtures("NOCOMP", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"));
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-NOCOMP")
				.firstName("First").lastName("Last")
				.workEmail("nocomp@acme.test")
				.departmentId(fx.departmentId()).locationId(fx.locationId())
				.jobFamilyId(fx.jobFamilyId()).jobLevelId(fx.jobLevelId())
				.hireDate(LocalDate.of(2036, 1, 1)).employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());

		assertThatThrownBy(() -> changeService.propose(new ProposeChangeRequest(
				employee.getId(), LocalDate.of(2036, 6, 1), new BigDecimal("100000"), "USD", "MERIT", null, null), fx.proposerId()))
				.isInstanceOf(NoCurrentCompensationException.class);
	}

	@Test
	void proposingInADifferentCurrencyThanCurrentPayIsRejected() {
		Fixtures fx = seedFixtures("CURRENCYMISMATCH", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"));
		UUID employeeId = hireWithComp(fx, "CURRENCYMISMATCH", new BigDecimal("100000"));

		assertThatThrownBy(() -> changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("100000"), "EUR", "MERIT", null, null), fx.proposerId()))
				.isInstanceOf(ChangeCurrencyMismatchException.class);
	}

	@Test
	void listFiltersByEmployeeAndStatus() {
		Fixtures fx = seedFixtures("LISTFILTER", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"));
		UUID employeeId = hireWithComp(fx, "LISTFILTER", new BigDecimal("100000"));
		ChangeResponse change = changeService.propose(new ProposeChangeRequest(
				employeeId, LocalDate.of(2036, 6, 1), new BigDecimal("110000"), "USD", "MERIT", null, null), fx.proposerId());

		List<ChangeResponse> byEmployee = changeService.list(employeeId, null, null, null);
		assertThat(byEmployee).extracting(ChangeResponse::id).contains(change.id());

		List<ChangeResponse> byStatus = changeService.list(employeeId, "DRAFT", null, null);
		assertThat(byStatus).extracting(ChangeResponse::id).contains(change.id());

		List<ChangeResponse> wrongStatus = changeService.list(employeeId, "APPROVED", null, null);
		assertThat(wrongStatus).extracting(ChangeResponse::id).doesNotContain(change.id());
	}

	private UUID hireWithComp(Fixtures fx, String suffix, BigDecimal amount) {
		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-" + suffix)
				.firstName("First").lastName("Last")
				.workEmail(suffix.toLowerCase() + "@acme.test")
				.departmentId(fx.departmentId()).locationId(fx.locationId())
				.jobFamilyId(fx.jobFamilyId()).jobLevelId(fx.jobLevelId())
				.hireDate(LocalDate.of(2020, 1, 1)).employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build());
		effectiveDating.apply(new ApplyCommand(
				employee.getId(), LocalDate.of(2036, 1, 1), amount, "USD", "ANNUAL", "INITIAL", null, fx.proposerId()));
		return employee.getId();
	}

	private record Fixtures(UUID departmentId, UUID locationId, UUID jobFamilyId, UUID jobLevelId, UUID proposerId, UUID approverId) {
	}

	private Fixtures seedFixtures(String suffix, BigDecimal min, BigDecimal mid, BigDecimal max) {
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ " + suffix).build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering").code("DEPT-CHG-" + suffix).build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-CHG-" + suffix).build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer CHG-" + suffix).sortOrder(4).build());
		User proposer = userRepository.save(User.builder()
				.email("proposer-" + suffix.toLowerCase() + "@acme.test")
				.fullName("Proposer").passwordHash("{argon2}stub").role("HR_MANAGER")
				.build());
		User approver = userRepository.save(User.builder()
				.email("approver-" + suffix.toLowerCase() + "@acme.test")
				.fullName("Approver").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());

		LocalDate rateMonth = LocalDate.of(2036, 1, 1);
		fxRateRepository.findByBaseCurrencyAndQuoteCurrencyAndRateMonth("USD", "USD", rateMonth)
				.orElseGet(() -> fxRateRepository.save(FxRate.builder()
						.rateMonth(rateMonth).baseCurrency("USD").quoteCurrency("USD").rate(BigDecimal.ONE)
						.build()));

		salaryBandRepository.save(SalaryBand.builder()
				.jobLevelId(level.getId()).countryCode("US").currency("USD")
				.minAmount(min).midAmount(mid).maxAmount(max)
				.effectiveFrom(LocalDate.of(2020, 1, 1)).createdBy(approver.getId())
				.build());

		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), proposer.getId(), approver.getId());
	}

}
