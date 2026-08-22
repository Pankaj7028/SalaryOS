package com.acme.salaryos.seed;

import com.acme.salaryos.seed.generator.BandGenerator;
import com.acme.salaryos.seed.generator.ChangeGenerator;
import com.acme.salaryos.seed.generator.CompensationGenerator;
import com.acme.salaryos.seed.generator.DemographicsGenerator;
import com.acme.salaryos.seed.generator.EmployeeGenerator;
import com.acme.salaryos.seed.generator.FxRateGenerator;
import com.acme.salaryos.seed.generator.ReferenceDataGenerator;
import com.acme.salaryos.seed.generator.UserGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The seeding orchestration itself, split out from {@link SeedRunner} so a test can drive two
 * runs against an empty database directly (P9.2) without activating the {@code seed} profile or
 * going through {@link org.springframework.boot.CommandLineRunner} — this bean carries no
 * profile restriction and no "refuse if non-empty" guard; that safety check is {@code
 * SeedRunner}'s job when run from the CLI, not this class's.
 */
@Slf4j
@Component
public class Seeder {

	private final ReferenceDataGenerator referenceDataGenerator;
	private final UserGenerator userGenerator;
	private final BandGenerator bandGenerator;
	private final FxRateGenerator fxRateGenerator;
	private final EmployeeGenerator employeeGenerator;
	private final DemographicsGenerator demographicsGenerator;
	private final CompensationGenerator compensationGenerator;
	private final ChangeGenerator changeGenerator;

	public Seeder(
			ReferenceDataGenerator referenceDataGenerator,
			UserGenerator userGenerator,
			BandGenerator bandGenerator,
			FxRateGenerator fxRateGenerator,
			EmployeeGenerator employeeGenerator,
			DemographicsGenerator demographicsGenerator,
			CompensationGenerator compensationGenerator,
			ChangeGenerator changeGenerator) {
		this.referenceDataGenerator = referenceDataGenerator;
		this.userGenerator = userGenerator;
		this.bandGenerator = bandGenerator;
		this.fxRateGenerator = fxRateGenerator;
		this.employeeGenerator = employeeGenerator;
		this.demographicsGenerator = demographicsGenerator;
		this.compensationGenerator = compensationGenerator;
		this.changeGenerator = changeGenerator;
	}

	public record SeedSummary(
			long totalMillis, int countries, int locations, int departments, int jobFamilies, int jobLevels,
			int bandRows, int fxRates, int users, int employees, CompensationGenerator.Anomalies anomalies) {
	}

	public SeedSummary seedAll(long randomSeed, String baseCurrency) {
		long overallStart = System.currentTimeMillis();
		SeedRandom random = new SeedRandom(randomSeed);
		log.info("Seeding started. seed={} asAt={} baseCurrency={}", randomSeed, SeedRandom.SEED_AS_AT, baseCurrency);

		List<ReferenceDataGenerator.Country> countries = stage("countries", () -> referenceDataGenerator.seedCountries());
		List<ReferenceDataGenerator.Location> locations = stage("locations", () -> referenceDataGenerator.seedLocations(random));
		List<ReferenceDataGenerator.Department> departments = stage("departments", () -> referenceDataGenerator.seedDepartments(random));
		List<ReferenceDataGenerator.JobFamilySeed> families = stage("job families/levels", () -> referenceDataGenerator.seedJobFamiliesAndLevels(random));
		List<UserGenerator.SeededUser> users = stage("users", () -> userGenerator.seedUsers(random));

		Map<String, UUID> departmentIdByCode = new java.util.HashMap<>();
		for (ReferenceDataGenerator.Department d : departments) {
			departmentIdByCode.put(d.code(), d.id());
		}
		UUID hrAdminId = users.stream().filter(u -> "HR_ADMIN".equals(u.role())).findFirst().orElseThrow().id();
		List<UUID> managerUserIds = users.stream()
				.filter(u -> "HR_ADMIN".equals(u.role()) || "HR_MANAGER".equals(u.role())).map(UserGenerator.SeededUser::id).toList();
		List<UUID> proposerUserIds = users.stream().map(UserGenerator.SeededUser::id).toList();

		Map<String, BandGenerator.Band> openBands = stage("salary bands",
				() -> bandGenerator.seedBands(random, countries, families, hrAdminId));
		Map<String, FxRateGenerator.FxRate> fxRates = stage("fx rates",
				() -> fxRateGenerator.seedFxRates(random, baseCurrency));
		List<EmployeeGenerator.SeededEmployee> employees = stage("employees",
				() -> employeeGenerator.seedEmployees(random, countries, locations, departments, families));
		Map<UUID, String> genderByEmployee = stage("demographics",
				() -> demographicsGenerator.seedDemographics(random, employees));
		CompensationGenerator.CompensationResult compensation = stage("compensation records + components",
				() -> compensationGenerator.seedCompensation(
						random, employees, openBands, fxRates, departmentIdByCode, genderByEmployee, baseCurrency, managerUserIds));
		stage("changes", () -> {
			changeGenerator.seedChanges(
					random, employees, compensation.currentAnnualBase(), compensation.currentCurrency(), proposerUserIds, managerUserIds);
			return null;
		});

		long totalMillis = System.currentTimeMillis() - overallStart;
		SeedSummary summary = new SeedSummary(
				totalMillis, countries.size(), locations.size(), departments.size(), families.size(),
				families.stream().mapToInt(f -> f.levels().size()).sum(), openBands.size() * 2, fxRates.size(),
				users.size(), employees.size(), compensation.anomalies());

		log.info("""
				Seeding complete in {}s.
				  countries={} locations={} departments={} jobFamilies={} jobLevels={} bands(rows)={} fxRates={} users={}
				  employees={}
				  anomalies: belowMin={} aboveMax={} noBand={} currentCompaRatioRange={}..{}
				""",
				totalMillis / 1000, summary.countries(), summary.locations(), summary.departments(), summary.jobFamilies(),
				summary.jobLevels(), summary.bandRows(), summary.fxRates(), summary.users(), summary.employees(),
				summary.anomalies().belowMin(), summary.anomalies().aboveMax(), summary.anomalies().noBand(),
				String.format("%.2f", summary.anomalies().compaRatioMin()), String.format("%.2f", summary.anomalies().compaRatioMax()));

		return summary;
	}

	private interface Stage<T> {
		T run();
	}

	private <T> T stage(String name, Stage<T> stage) {
		long start = System.currentTimeMillis();
		T result = stage.run();
		long elapsedMs = System.currentTimeMillis() - start;
		log.info("  [{}] {} ms", name, elapsedMs);
		return result;
	}

}
