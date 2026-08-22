package com.acme.salaryos.seed;

import com.acme.salaryos.employee.repository.EmployeeRepository;
import com.acme.salaryos.seed.generator.BandGenerator;
import com.acme.salaryos.seed.generator.ChangeGenerator;
import com.acme.salaryos.seed.generator.CompensationGenerator;
import com.acme.salaryos.seed.generator.DemographicsGenerator;
import com.acme.salaryos.seed.generator.EmployeeGenerator;
import com.acme.salaryos.seed.generator.FxRateGenerator;
import com.acme.salaryos.seed.generator.ReferenceDataGenerator;
import com.acme.salaryos.seed.generator.UserGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code --spring.profiles.active=seed} (backend doc §9). Refuses to run if {@code employees} is
 * non-empty unless {@code --app.seed.force=true} — reseeding on top of real (or already-seeded)
 * data would double every count and, worse, could collide on {@code employee_number}/{@code
 * work_email} uniqueness mid-run and leave the database half-written.
 *
 * <p>One {@link SeedRandom} threaded through every generator (constructed once, here, from
 * {@code app.seed.random-seed}) is what makes two runs against empty databases produce identical
 * data (P9.2) — no generator below ever makes its own {@code Random} or reads the wall clock.
 */
@Slf4j
@Component
@Profile("seed")
public class SeedRunner implements CommandLineRunner {

	private final EmployeeRepository employeeRepository;
	private final ReferenceDataGenerator referenceDataGenerator;
	private final UserGenerator userGenerator;
	private final BandGenerator bandGenerator;
	private final FxRateGenerator fxRateGenerator;
	private final EmployeeGenerator employeeGenerator;
	private final DemographicsGenerator demographicsGenerator;
	private final CompensationGenerator compensationGenerator;
	private final ChangeGenerator changeGenerator;
	private final long randomSeed;
	private final boolean force;
	private final String baseCurrency;

	public SeedRunner(
			EmployeeRepository employeeRepository,
			ReferenceDataGenerator referenceDataGenerator,
			UserGenerator userGenerator,
			BandGenerator bandGenerator,
			FxRateGenerator fxRateGenerator,
			EmployeeGenerator employeeGenerator,
			DemographicsGenerator demographicsGenerator,
			CompensationGenerator compensationGenerator,
			ChangeGenerator changeGenerator,
			@Value("${app.seed.random-seed}") long randomSeed,
			@Value("${app.seed.force}") boolean force,
			@Value("${app.base-currency}") String baseCurrency) {
		this.employeeRepository = employeeRepository;
		this.referenceDataGenerator = referenceDataGenerator;
		this.userGenerator = userGenerator;
		this.bandGenerator = bandGenerator;
		this.fxRateGenerator = fxRateGenerator;
		this.employeeGenerator = employeeGenerator;
		this.demographicsGenerator = demographicsGenerator;
		this.compensationGenerator = compensationGenerator;
		this.changeGenerator = changeGenerator;
		this.randomSeed = randomSeed;
		this.force = force;
		this.baseCurrency = baseCurrency;
	}

	@Override
	public void run(String... args) {
		if (employeeRepository.count() > 0 && !force) {
			log.error("Refusing to seed: employees table is non-empty. Pass --app.seed.force=true to seed anyway "
					+ "(this does NOT clear existing data first — it will likely fail on a uniqueness collision).");
			return;
		}

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

		long totalSeconds = (System.currentTimeMillis() - overallStart) / 1000;
		CompensationGenerator.Anomalies a = compensation.anomalies();
		log.info("""
				Seeding complete in {}s.
				  countries={} locations={} departments={} jobFamilies={} jobLevels={} bands(rows)={} fxRates={} users={}
				  employees={} (bandless-combo anomaly={})
				  anomalies: belowMin={} aboveMax={} noBand={} currentCompaRatioRange={}..{}
				""",
				totalSeconds, countries.size(), locations.size(), departments.size(), families.size(),
				families.stream().mapToInt(f -> f.levels().size()).sum(), openBands.size() * 2, fxRates.size(), users.size(),
				employees.size(), a.noBand(), a.belowMin(), a.aboveMax(), a.noBand(),
				String.format("%.2f", a.compaRatioMin()), String.format("%.2f", a.compaRatioMax()));

		if (totalSeconds > 90) {
			log.warn("Seeding took {}s, over the 90s target (backend doc §9) — this run's timings above show which stage to look at first.", totalSeconds);
		}
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
