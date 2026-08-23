package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.change.dto.BulkProposeRequest;
import com.acme.salaryos.change.dto.BulkProposeResult;
import com.acme.salaryos.change.dto.ProposeChangeRequest;
import com.acme.salaryos.change.service.ChangeService;
import com.acme.salaryos.compensation.effective.ApplyCommand;
import com.acme.salaryos.compensation.effective.EffectiveDating;
import com.acme.salaryos.compensation.repository.EmployeeCurrentCompRepository;
import com.acme.salaryos.change.repository.CompensationChangeRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P10.5's bulk select → propose. The point of the endpoint is that the new base for each person is
 * computed here, in {@code BigDecimal}, from what they are actually paid — so these tests are about
 * the arithmetic and the partial-success contract, not about the transport.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class BulkProposeTest {

	@Autowired private ChangeService changeService;
	@Autowired private EffectiveDating effectiveDating;
	@Autowired private EmployeeRepository employeeRepository;
	@Autowired private EmployeeCurrentCompRepository employeeCurrentCompRepository;
	@Autowired private CompensationChangeRepository changeRepository;
	@Autowired private SalaryBandRepository salaryBandRepository;
	@Autowired private FxRateRepository fxRateRepository;
	@Autowired private CountryRepository countryRepository;
	@Autowired private LocationRepository locationRepository;
	@Autowired private DepartmentRepository departmentRepository;
	@Autowired private JobFamilyRepository jobFamilyRepository;
	@Autowired private JobLevelRepository jobLevelRepository;
	@Autowired private UserRepository userRepository;

	private static final LocalDate EFFECTIVE = LocalDate.of(2039, 4, 1);
	/** Months this class seeds FX for must be ones no other test class seeds unconditionally --
	 * the Testcontainers Postgres is shared across classes and (month, base, quote) is unique. */
	private static final LocalDate HIRED = LocalDate.of(2031, 2, 1);

	/**
	 * Each person's new base is their own base plus the percentage — not one shared figure. Three
	 * different starting salaries prove the server is doing per-row arithmetic rather than
	 * applying an amount the caller worked out.
	 */
	@Test
	void oneUpliftBecomesADifferentAmountForEachSalary() {
		Fixtures fx = seed("PCT");
		UUID a = hire(fx, "E-PCT-A", new BigDecimal("100000"));
		UUID b = hire(fx, "E-PCT-B", new BigDecimal("123456"));
		UUID c = hire(fx, "E-PCT-C", new BigDecimal("98765.43"));

		BulkProposeResult result = changeService.bulkPropose(new BulkProposeRequest(
				List.of(a, b, c), EFFECTIVE, new BigDecimal("3.5"), "MERIT", "FY39 merit"), fx.proposerId());

		assertThat(result.proposed()).isEqualTo(3);
		assertThat(result.errors()).isZero();
		assertThat(amountFor(result, "E-PCT-A")).isEqualByComparingTo("103500.00");
		assertThat(amountFor(result, "E-PCT-B")).isEqualByComparingTo("127776.96");
		assertThat(amountFor(result, "E-PCT-C")).isEqualByComparingTo("102222.22");
	}

	/** Every proposal lands as a DRAFT. A bulk operation must not be a bulk approval. */
	@Test
	void everyRowLandsAsADraftAndNothingIsApproved() {
		Fixtures fx = seed("DRF");
		UUID a = hire(fx, "E-DRF-A", new BigDecimal("100000"));
		UUID b = hire(fx, "E-DRF-B", new BigDecimal("100000"));

		BulkProposeResult result = changeService.bulkPropose(new BulkProposeRequest(
				List.of(a, b), EFFECTIVE, new BigDecimal("2"), "MERIT", null), fx.proposerId());

		assertThat(result.proposed()).isEqualTo(2);
		assertThat(result.rows()).allSatisfy(row ->
				assertThat(changeRepository.findById(row.changeId()).orElseThrow().getStatus()).isEqualTo("DRAFT"));
	}

	/**
	 * The partial-success contract. One employee with an open change and one with no pay on record
	 * must not cost the rest of the selection their proposals — on a real cohort at least one row
	 * is always in one of those states, and an all-or-nothing batch would be unusable there.
	 */
	@Test
	void oneUnproposableRowDoesNotCostTheOthersTheirProposals() {
		Fixtures fx = seed("PRT");
		UUID ok1 = hire(fx, "E-PRT-1", new BigDecimal("100000"));
		UUID ok2 = hire(fx, "E-PRT-2", new BigDecimal("100000"));
		UUID alreadyOpen = hire(fx, "E-PRT-3", new BigDecimal("100000"));
		UUID noPay = hireWithoutPay(fx, "E-PRT-4");

		changeService.propose(new ProposeChangeRequest(
				alreadyOpen, EFFECTIVE, new BigDecimal("105000"), "USD", "MERIT", null, null), fx.proposerId());

		BulkProposeResult result = changeService.bulkPropose(new BulkProposeRequest(
				List.of(ok1, alreadyOpen, ok2, noPay), EFFECTIVE, new BigDecimal("4"), "MERIT", null), fx.proposerId());

		assertThat(result.totalRows()).isEqualTo(4);
		assertThat(result.proposed()).isEqualTo(2);
		assertThat(result.errors()).isEqualTo(2);
		assertThat(result.rows()).filteredOn(r -> "ERROR".equals(r.action()))
				.allSatisfy(r -> assertThat(r.error()).isNotBlank())
				.extracting(r -> r.error())
				.anySatisfy(e -> assertThat(e).contains("open change"))
				.anySatisfy(e -> assertThat(e).contains("nothing to increase"));
	}

	/** A cut is a legitimate bulk operation and must produce a lower figure, not an absolute one. */
	@Test
	void aNegativePercentageProposesACut() {
		Fixtures fx = seed("CUT");
		UUID a = hire(fx, "E-CUT-A", new BigDecimal("100000"));

		BulkProposeResult result = changeService.bulkPropose(new BulkProposeRequest(
				List.of(a), EFFECTIVE, new BigDecimal("-10"), "CORRECTION", "Overpaid on import"), fx.proposerId());

		assertThat(amountFor(result, "E-CUT-A")).isEqualByComparingTo("90000.00");
	}

	/**
	 * The proposal is denominated in the currency the employee is actually paid in. A bulk
	 * operation across countries must never convert — proposing a EUR-paid employee's rise in USD
	 * is how a 15% error reaches a ledger that cannot be edited.
	 */
	@Test
	void eachProposalKeepsTheEmployeesOwnCurrency() {
		Fixtures fx = seed("CUR");
		UUID usd = hire(fx, "E-CUR-USD", new BigDecimal("100000"));
		UUID eur = hireIn(fx, "E-CUR-EUR", new BigDecimal("90000"), "EUR");

		BulkProposeResult result = changeService.bulkPropose(new BulkProposeRequest(
				List.of(usd, eur), EFFECTIVE, new BigDecimal("5"), "MERIT", null), fx.proposerId());

		assertThat(result.proposed()).isEqualTo(2);
		assertThat(currencyOfChange(result, "E-CUR-USD")).isEqualTo("USD");
		assertThat(currencyOfChange(result, "E-CUR-EUR")).isEqualTo("EUR");
		assertThat(amountFor(result, "E-CUR-EUR")).isEqualByComparingTo("94500.00");
	}

	// --- helpers ---------------------------------------------------------------------------

	private BigDecimal amountFor(BulkProposeResult result, String employeeNumber) {
		return result.rows().stream()
				.filter(r -> employeeNumber.equals(r.employeeNumber()))
				.findFirst().orElseThrow().newAmount();
	}

	private String currencyOfChange(BulkProposeResult result, String employeeNumber) {
		UUID changeId = result.rows().stream()
				.filter(r -> employeeNumber.equals(r.employeeNumber()))
				.findFirst().orElseThrow().changeId();
		return changeRepository.findById(changeId).orElseThrow().getCurrency();
	}

	private UUID hire(Fixtures fx, String employeeNumber, BigDecimal amount) {
		return hireIn(fx, employeeNumber, amount, "USD");
	}

	private UUID hireIn(Fixtures fx, String employeeNumber, BigDecimal amount, String currency) {
		UUID id = hireWithoutPay(fx, employeeNumber);
		effectiveDating.apply(new ApplyCommand(
				id, HIRED, amount, currency, "ANNUAL", "INITIAL", null, fx.proposerId()));
		return id;
	}

	private UUID hireWithoutPay(Fixtures fx, String employeeNumber) {
		return employeeRepository.save(Employee.builder()
				.employeeNumber(employeeNumber)
				.firstName("First").lastName("Last")
				.workEmail(employeeNumber.toLowerCase() + "@acme.test")
				.departmentId(fx.departmentId()).locationId(fx.locationId())
				.jobFamilyId(fx.jobFamilyId()).jobLevelId(fx.jobLevelId())
				.hireDate(HIRED).employmentType("FULL_TIME").fte(BigDecimal.ONE)
				.build()).getId();
	}

	private record Fixtures(UUID departmentId, UUID locationId, UUID jobFamilyId, UUID jobLevelId, UUID proposerId) {
	}

	private Fixtures seed(String tag) {
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("HQ BP " + tag).build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering").code("DEPT-BP-" + tag).build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-BP-" + tag).build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer BP " + tag).sortOrder(4).build());
		User proposer = userRepository.save(User.builder()
				.email("proposer-bp-" + tag.toLowerCase() + "@acme.test")
				.fullName("Proposer").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());

		for (LocalDate month : List.of(HIRED, EFFECTIVE)) {
			for (String payCurrency : List.of("USD", "EUR")) {
				saveRateIfAbsent(month, payCurrency);
			}
		}

		salaryBandRepository.save(SalaryBand.builder()
				.jobLevelId(level.getId()).countryCode("US").currency("USD")
				.minAmount(new BigDecimal("90000")).midAmount(new BigDecimal("110000")).maxAmount(new BigDecimal("130000"))
				.effectiveFrom(HIRED).createdBy(proposer.getId())
				.build());

		return new Fixtures(department.getId(), location.getId(), family.getId(), level.getId(), proposer.getId());
	}

	/** Normalisation converts the pay currency to USD, so the rate needed is (pay currency -> USD).
	 * Rates are shared across this class's fixtures; the unique key is (month, base, quote). */
	private void saveRateIfAbsent(LocalDate month, String payCurrency) {
		boolean exists = fxRateRepository.findAll().stream()
				.anyMatch(r -> r.getRateMonth().equals(month) && payCurrency.equals(r.getBaseCurrency())
						&& "USD".equals(r.getQuoteCurrency()));
		if (!exists) {
			fxRateRepository.save(FxRate.builder()
					.rateMonth(month).baseCurrency(payCurrency).quoteCurrency("USD")
					.rate(payCurrency.equals("USD") ? BigDecimal.ONE : new BigDecimal("1.08")).build());
		}
	}

}
