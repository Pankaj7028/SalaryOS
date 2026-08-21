package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.band.dto.BandImportResult;
import com.acme.salaryos.band.dto.BandResponse;
import com.acme.salaryos.band.dto.CreateBandRequest;
import com.acme.salaryos.band.dto.UpdateBandRequest;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.band.service.BandAlreadyExistsException;
import com.acme.salaryos.band.service.BandBackdatedException;
import com.acme.salaryos.band.service.BandNotOpenException;
import com.acme.salaryos.band.service.BandService;
import com.acme.salaryos.reference.domain.Country;
import com.acme.salaryos.reference.domain.JobFamily;
import com.acme.salaryos.reference.domain.JobLevel;
import com.acme.salaryos.reference.repository.CountryRepository;
import com.acme.salaryos.reference.repository.JobFamilyRepository;
import com.acme.salaryos.reference.repository.JobLevelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P5.3 — FR-4.5 (never mutated in place; editing an in-force band closes it and opens a successor)
 * and FR-4.6 (CSV import with a dry-run diff that changes nothing).
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class BandVersioningTest {

	@Autowired
	private BandService bandService;
	@Autowired
	private SalaryBandRepository salaryBandRepository;
	@Autowired
	private CountryRepository countryRepository;
	@Autowired
	private JobFamilyRepository jobFamilyRepository;
	@Autowired
	private JobLevelRepository jobLevelRepository;
	@Autowired
	private UserRepository userRepository;

	@Test
	void editingAnInForceBandClosesItAndOpensASuccessor() {
		Fixtures fx = seedFixtures("VERSION");

		BandResponse original = bandService.create(new CreateBandRequest(
				fx.jobLevelId(), "US", "USD", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"),
				LocalDate.of(2033, 1, 1), null), fx.userId());

		BandResponse successor = bandService.update(original.id(), new UpdateBandRequest(
				"USD", new BigDecimal("95000"), new BigDecimal("115000"), new BigDecimal("135000"),
				LocalDate.of(2033, 6, 1), "Annual market adjustment."), fx.userId());

		var reloadedOriginal = salaryBandRepository.findById(original.id()).orElseThrow();
		assertThat(reloadedOriginal.getEffectiveTo()).isEqualTo(LocalDate.of(2033, 5, 31));
		assertThat(successor.id()).isNotEqualTo(original.id());
		assertThat(successor.effectiveTo()).isNull();
		assertThat(successor.mid().amount()).isEqualByComparingTo("115000.00");
		assertThat(successor.jobLevelId()).isEqualTo(fx.jobLevelId());

		assertThat(salaryBandRepository.findByJobLevelIdAndCountryCodeAndEffectiveToIsNull(fx.jobLevelId(), "US"))
				.map(band -> band.getId()).contains(successor.id());
	}

	@Test
	void creatingASecondBandForAnAlreadyCoveredLevelAndCountryIsRejected() {
		Fixtures fx = seedFixtures("DUPLICATE");
		bandService.create(new CreateBandRequest(
				fx.jobLevelId(), "US", "USD", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"),
				LocalDate.of(2033, 1, 1), null), fx.userId());

		assertThatThrownBy(() -> bandService.create(new CreateBandRequest(
				fx.jobLevelId(), "US", "USD", new BigDecimal("91000"), new BigDecimal("111000"), new BigDecimal("131000"),
				LocalDate.of(2033, 2, 1), null), fx.userId()))
				.isInstanceOf(BandAlreadyExistsException.class);
	}

	@Test
	void versioningOnOrBeforeTheCurrentVersionsStartIsRejected() {
		Fixtures fx = seedFixtures("BACKDATE");
		BandResponse band = bandService.create(new CreateBandRequest(
				fx.jobLevelId(), "US", "USD", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"),
				LocalDate.of(2033, 3, 1), null), fx.userId());

		assertThatThrownBy(() -> bandService.update(band.id(), new UpdateBandRequest(
				"USD", new BigDecimal("91000"), new BigDecimal("111000"), new BigDecimal("131000"),
				LocalDate.of(2033, 3, 1), null), fx.userId()))
				.isInstanceOf(BandBackdatedException.class);
	}

	@Test
	void versioningAnAlreadySupersededBandIsRejected() {
		Fixtures fx = seedFixtures("NOTOPEN");
		BandResponse original = bandService.create(new CreateBandRequest(
				fx.jobLevelId(), "US", "USD", new BigDecimal("90000"), new BigDecimal("110000"), new BigDecimal("130000"),
				LocalDate.of(2033, 1, 1), null), fx.userId());
		bandService.update(original.id(), new UpdateBandRequest(
				"USD", new BigDecimal("95000"), new BigDecimal("115000"), new BigDecimal("135000"),
				LocalDate.of(2033, 6, 1), null), fx.userId());

		assertThatThrownBy(() -> bandService.update(original.id(), new UpdateBandRequest(
				"USD", new BigDecimal("99000"), new BigDecimal("119000"), new BigDecimal("139000"),
				LocalDate.of(2033, 7, 1), null), fx.userId()))
				.isInstanceOf(BandNotOpenException.class);
	}

	@Test
	void dryRunReportsTheDiffButChangesNothing() {
		Fixtures fx = seedFixtures("DRYRUN");
		long countBefore = salaryBandRepository.count();

		String csv = "jobLevelId,countryCode,currency,minAmount,midAmount,maxAmount,effectiveFrom,note\n"
				+ fx.jobLevelId() + ",US,USD,90000,110000,130000,2033-01-01,Initial import\n";
		MockMultipartFile file = new MockMultipartFile(
				"file", "bands.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		BandImportResult result = bandService.importCsv(file, true, fx.userId());

		assertThat(result.dryRun()).isTrue();
		assertThat(result.totalRows()).isEqualTo(1);
		assertThat(result.created()).isEqualTo(1);
		assertThat(result.rowsApplied()).isEqualTo(0);
		assertThat(result.rows().get(0).action()).isEqualTo("CREATE");
		assertThat(salaryBandRepository.count()).isEqualTo(countBefore);
		assertThat(salaryBandRepository.findByJobLevelIdAndCountryCodeAndEffectiveToIsNull(fx.jobLevelId(), "US")).isEmpty();
	}

	@Test
	void nonDryRunAppliesCreatesAndVersionsAndReportsRowErrorsWithoutBlockingValidRows() {
		Fixtures fx = seedFixtures("REALIMPORT");
		UUID secondLevel = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(fx.jobFamilyId()).levelCode("L5").title("Staff Engineer REALIMPORT").sortOrder(5).build())
				.getId();

		String csv = "jobLevelId,countryCode,currency,minAmount,midAmount,maxAmount,effectiveFrom,note\n"
				+ fx.jobLevelId() + ",US,USD,90000,110000,130000,2033-01-01,First version\n"
				+ "not-a-uuid,US,USD,90000,110000,130000,2033-01-01,Malformed row\n"
				+ secondLevel + ",US,USD,200000,150000,250000,2033-01-01,min greater than mid\n";
		MockMultipartFile file = new MockMultipartFile(
				"file", "bands.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		BandImportResult result = bandService.importCsv(file, false, fx.userId());

		assertThat(result.dryRun()).isFalse();
		assertThat(result.totalRows()).isEqualTo(3);
		assertThat(result.created()).isEqualTo(1);
		assertThat(result.errors()).isEqualTo(2);
		assertThat(result.rowsApplied()).isEqualTo(1);
		assertThat(salaryBandRepository.findByJobLevelIdAndCountryCodeAndEffectiveToIsNull(fx.jobLevelId(), "US")).isPresent();
		assertThat(salaryBandRepository.findByJobLevelIdAndCountryCodeAndEffectiveToIsNull(secondLevel, "US")).isEmpty();

		// Versioning: import again with a later date for the same level x country — should VERSION, not CREATE.
		String versionCsv = "jobLevelId,countryCode,currency,minAmount,midAmount,maxAmount,effectiveFrom,note\n"
				+ fx.jobLevelId() + ",US,USD,95000,115000,135000,2033-06-01,Second version\n";
		MockMultipartFile versionFile = new MockMultipartFile(
				"file", "bands2.csv", "text/csv", versionCsv.getBytes(StandardCharsets.UTF_8));
		BandImportResult versionResult = bandService.importCsv(versionFile, false, fx.userId());
		assertThat(versionResult.versioned()).isEqualTo(1);
	}

	private record Fixtures(UUID jobFamilyId, UUID jobLevelId, UUID userId) {
	}

	private Fixtures seedFixtures(String suffix) {
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("FAM-BAND-" + suffix).build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L4").title("Senior Engineer BAND-" + suffix).sortOrder(4).build());
		User user = userRepository.save(User.builder()
				.email("hr-admin-band-" + suffix.toLowerCase() + "@acme.test")
				.fullName("HR Admin").passwordHash("{argon2}stub").role("HR_ADMIN")
				.build());
		return new Fixtures(family.getId(), level.getId(), user.getId());
	}

}
