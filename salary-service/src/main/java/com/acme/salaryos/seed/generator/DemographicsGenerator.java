package com.acme.salaryos.seed.generator;

import com.acme.salaryos.seed.SeedRandom;
import com.acme.salaryos.seed.generator.EmployeeGenerator.SeededEmployee;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code employee_demographics} — CLAUDE.md §6.6: a separate table, seeded here only so the
 * equity/pay-gap analytics (FR-6.4) have a real cohort dimension to group on. Never read by
 * anything in {@code seed/} after this point; {@link CompensationGenerator} takes the gender map
 * this returns purely to place the deliberate equity-gap anomaly, the same arm's-length distance
 * every other part of the app is required to keep from this table.
 */
@Component
public class DemographicsGenerator {

	private static final String[] ETHNICITY_CODES = { "A", "B", "H", "W", "O" };

	private final JdbcTemplate jdbc;

	public DemographicsGenerator(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** employeeId -> "M"/"F" — the only thing {@link CompensationGenerator} is handed back. */
	public Map<UUID, String> seedDemographics(SeedRandom random, List<SeededEmployee> employees) {
		Map<UUID, String> genderByEmployee = new HashMap<>();
		List<Object[]> rows = new ArrayList<>(employees.size());

		for (SeededEmployee employee : employees) {
			String gender = random.chance(0.5) ? "F" : "M";
			LocalDate dateOfBirth = SeedRandom.SEED_AS_AT.minusYears(random.nextInt(24, 62))
					.minusDays(random.nextInt(0, 365));
			String ethnicity = random.pick(ETHNICITY_CODES);

			genderByEmployee.put(employee.id(), gender);
			rows.add(new Object[] { employee.id(), gender, dateOfBirth, ethnicity });
		}

		jdbc.batchUpdate(
				"insert into salary_schema.employee_demographics (employee_id, gender, date_of_birth, ethnicity_code) "
						+ "values (?, ?, ?, ?)",
				rows);
		return genderByEmployee;
	}

}
