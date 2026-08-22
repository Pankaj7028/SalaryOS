package com.acme.salaryos.seed;

import com.acme.salaryos.employee.repository.EmployeeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code --spring.profiles.active=seed} (backend doc §9). Refuses to run if {@code employees} is
 * non-empty unless {@code --app.seed.force=true} — reseeding on top of real (or already-seeded)
 * data would double every count and, worse, could collide on {@code employee_number}/{@code
 * work_email} uniqueness mid-run and leave the database half-written.
 *
 * <p>The actual orchestration lives in {@link Seeder}, which carries no profile restriction and
 * no CLI-specific guard — P9.2's reproducibility test calls it directly against a Testcontainers
 * Postgres, twice, without needing this class or the {@code seed} profile at all.
 */
@Slf4j
@Component
@Profile("seed")
public class SeedRunner implements CommandLineRunner {

	private final EmployeeRepository employeeRepository;
	private final Seeder seeder;
	private final long randomSeed;
	private final boolean force;
	private final String baseCurrency;

	public SeedRunner(
			EmployeeRepository employeeRepository,
			Seeder seeder,
			@Value("${app.seed.random-seed}") long randomSeed,
			@Value("${app.seed.force}") boolean force,
			@Value("${app.base-currency}") String baseCurrency) {
		this.employeeRepository = employeeRepository;
		this.seeder = seeder;
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

		Seeder.SeedSummary summary = seeder.seedAll(randomSeed, baseCurrency);
		if (summary.totalMillis() / 1000 > 90) {
			log.warn("Seeding took {}s, over the 90s target (backend doc §9) — see the per-stage timings above for which stage to look at first.",
					summary.totalMillis() / 1000);
		}
	}

}
