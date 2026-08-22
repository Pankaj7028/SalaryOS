package com.acme.salaryos.seed.generator;

import com.acme.salaryos.seed.SeedRandom;
import com.acme.salaryos.seed.generator.ReferenceDataGenerator.Country;
import com.acme.salaryos.seed.generator.ReferenceDataGenerator.Department;
import com.acme.salaryos.seed.generator.ReferenceDataGenerator.JobFamilySeed;
import com.acme.salaryos.seed.generator.ReferenceDataGenerator.JobLevel;
import com.acme.salaryos.seed.generator.ReferenceDataGenerator.Location;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 10,000 employees (backend doc §9). Level distribution is pyramid-shaped (L1–L2 ≈ 45%,
 * L6–L7 ≈ 4%); managers are resolved in a second pass, always pointing at a strictly higher
 * {@code sortOrder} within the same department (falling back to any department, then to none) —
 * which makes the org chart acyclic by construction, not by a cycle check.
 *
 * <p>Stateless between calls on purpose (no instance fields carry data across a {@code
 * seedEmployees} invocation) — a reproducibility test (P9.2) that reseeds twice against separate
 * empty databases must not have this bean's leftover state from run 1 able to influence run 2.
 */
@Component
public class EmployeeGenerator {

	/** Index 0..6 = L1..L7. Sums to 1.0 — 45% at L1–L2, 4% at L6–L7 (backend doc §9). */
	private static final double[] LEVEL_WEIGHTS = { 0.22, 0.23, 0.20, 0.16, 0.15, 0.025, 0.015 };

	/** Rough relative headcount share per country — larger economies carry more staff. */
	private static final Map<String, Double> COUNTRY_WEIGHTS = Map.of(
			"US", 0.34, "GB", 0.13, "DE", 0.11, "IN", 0.20, "SG", 0.06, "BR", 0.07, "PL", 0.05, "IE", 0.04);

	private static final String[] FIRST_NAMES = {
			"Olivia", "Liam", "Emma", "Noah", "Ava", "Ethan", "Sophia", "Mason", "Isabella", "Lucas",
			"Mia", "Aiden", "Amelia", "Jackson", "Harper", "Logan", "Evelyn", "Elijah", "Abigail", "James",
			"Priya", "Arjun", "Ananya", "Rohan", "Diya", "Kabir", "Ishaan", "Meera", "Wei", "Yuki",
			"Hana", "Jorge", "Camila", "Mateus", "Larissa", "Piotr", "Zofia", "Klaus", "Freya", "Oisin" };
	private static final String[] LAST_NAMES = {
			"Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez",
			"Martinez", "Patel", "Sharma", "Kumar", "Singh", "Silva", "Santos", "Oliveira", "Nowak",
			"Kowalski", "Muller", "Schmidt", "Wagner", "Kelly", "Murphy", "Byrne", "Lim", "Tan", "Ng" };

	private static final int EMPLOYEE_COUNT = 10_000;

	private final JdbcTemplate jdbc;

	public EmployeeGenerator(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record SeededEmployee(
			UUID id, String employeeNumber, String firstName, String lastName, String workEmail,
			UUID departmentId, UUID locationId, String countryCode, UUID jobFamilyId, UUID jobLevelId,
			int levelSortOrder, UUID managerId, LocalDate hireDate, String employmentType, BigDecimal fte,
			String status, LocalDate terminationDate) {

		SeededEmployee withManager(UUID managerId) {
			return new SeededEmployee(
					id, employeeNumber, firstName, lastName, workEmail, departmentId, locationId, countryCode,
					jobFamilyId, jobLevelId, levelSortOrder, managerId, hireDate, employmentType, fte,
					status, terminationDate);
		}
	}

	public List<SeededEmployee> seedEmployees(
			SeedRandom random, List<Country> countries, List<Location> locations,
			List<Department> departments, List<JobFamilySeed> families) {

		Map<String, List<Location>> locationsByCountry = new HashMap<>();
		for (Location location : locations) {
			locationsByCountry.computeIfAbsent(location.countryCode(), k -> new ArrayList<>()).add(location);
		}
		Map<String, UUID> departmentIdByCode = new HashMap<>();
		for (Department d : departments) {
			departmentIdByCode.put(d.code(), d.id());
		}
		Map<String, List<UUID>> departmentsByFamilyCode = departmentPoolsByFamily(departmentIdByCode);
		double[] countryWeights = countries.stream().mapToDouble(c -> COUNTRY_WEIGHTS.get(c.code())).toArray();

		List<SeededEmployee> withoutManagers = new ArrayList<>(EMPLOYEE_COUNT);
		for (int i = 1; i <= EMPLOYEE_COUNT; i++) {
			JobFamilySeed family = random.pick(families);
			JobLevel level = random.pickWeighted(family.levels(), LEVEL_WEIGHTS);
			Country country = random.pickWeighted(countries, countryWeights);
			Location location = random.pick(locationsByCountry.get(country.code()));
			UUID departmentId = random.pick(departmentsByFamilyCode.get(family.family().code()));

			String firstName = random.pick(FIRST_NAMES);
			String lastName = random.pick(LAST_NAMES);
			String employeeNumber = String.format("E-%05d", i);
			String workEmail = (firstName + "." + lastName + "." + i + "@acme.test").toLowerCase();

			LocalDate hireDate = hireDateFor(random, level.sortOrder());
			String employmentType = random.chance(0.94) ? "FULL_TIME" : (random.chance(0.6) ? "PART_TIME" : "CONTRACT");
			BigDecimal fte = "FULL_TIME".equals(employmentType)
					? BigDecimal.ONE.setScale(2)
					: BigDecimal.valueOf(random.pick(List.of(0.5, 0.6, 0.8))).setScale(2);

			String status;
			LocalDate terminationDate = null;
			double statusRoll = random.nextDouble();
			if (statusRoll < 0.04) {
				status = "TERMINATED";
				terminationDate = random.dateBetween(hireDate.plusMonths(6), SeedRandom.SEED_AS_AT);
			}
			else if (statusRoll < 0.06) {
				status = "ON_LEAVE";
			}
			else {
				status = "ACTIVE";
			}

			withoutManagers.add(new SeededEmployee(
					random.uuid(), employeeNumber, firstName, lastName, workEmail,
					departmentId, location.id(), country.code(), family.family().id(), level.id(), level.sortOrder(),
					null, hireDate, employmentType, fte, status, terminationDate));
		}

		List<SeededEmployee> employees = resolveManagers(random, withoutManagers);

		// Two-phase: a manager_id in the same batch as its own row can point at a row that
		// hasn't been inserted yet (Postgres checks the FK per-row within a batch, not after the
		// whole batch commits) -- insert every row with manager_id NULL first, then a second
		// batched UPDATE fills it in once every employee id genuinely exists.
		List<Object[]> insertRows = new ArrayList<>(employees.size());
		for (SeededEmployee e : employees) {
			insertRows.add(new Object[] {
					e.id(), e.employeeNumber(), e.firstName(), e.lastName(), e.workEmail(),
					e.departmentId(), e.locationId(), e.jobFamilyId(), e.jobLevelId(),
					e.hireDate(), e.employmentType(), e.fte(), e.status(), e.terminationDate() });
		}
		com.acme.salaryos.seed.JdbcBatch.insert(jdbc,
				"insert into salary_schema.employees (id, employee_number, first_name, last_name, work_email, "
						+ "department_id, location_id, job_family_id, job_level_id, hire_date, "
						+ "employment_type, fte, status, termination_date) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				insertRows);

		List<Object[]> managerUpdateRows = employees.stream()
				.filter(e -> e.managerId() != null)
				.map(e -> new Object[] { e.managerId(), e.id() })
				.toList();
		com.acme.salaryos.seed.JdbcBatch.insert(jdbc,
				"update salary_schema.employees set manager_id = ? where id = ?", managerUpdateRows);
		return employees;
	}

	/** Strictly-higher-sortOrder-only, so the result is acyclic by construction — no cycle check
	 * is needed because a manager pointer can never reach back down to its own report. */
	private List<SeededEmployee> resolveManagers(SeedRandom random, List<SeededEmployee> employees) {
		Map<UUID, List<SeededEmployee>> byDepartment = new HashMap<>();
		for (SeededEmployee e : employees) {
			byDepartment.computeIfAbsent(e.departmentId(), k -> new ArrayList<>()).add(e);
		}

		List<SeededEmployee> resolved = new ArrayList<>(employees.size());
		for (SeededEmployee e : employees) {
			if (e.levelSortOrder() >= 6 || random.chance(0.03)) {
				resolved.add(e); // top of the house, or deliberately no manager on file
				continue;
			}
			List<SeededEmployee> sameDeptSeniors = byDepartment.getOrDefault(e.departmentId(), List.of()).stream()
					.filter(candidate -> candidate.levelSortOrder() > e.levelSortOrder())
					.toList();
			UUID managerId;
			if (!sameDeptSeniors.isEmpty()) {
				managerId = random.pick(sameDeptSeniors).id();
			}
			else {
				List<SeededEmployee> anySeniors = employees.stream()
						.filter(candidate -> candidate.levelSortOrder() > e.levelSortOrder())
						.toList();
				managerId = anySeniors.isEmpty() ? null : random.pick(anySeniors).id();
			}
			resolved.add(e.withManager(managerId));
		}
		return resolved;
	}

	private LocalDate hireDateFor(SeedRandom random, int levelSortOrder) {
		// Senior levels skew longer-tenured (backend doc §9's above-max anomaly leans on this).
		int maxYearsBack = levelSortOrder >= 5 ? 9 : 6;
		LocalDate earliest = SeedRandom.SEED_AS_AT.minusYears(maxYearsBack);
		return random.dateBetween(earliest, SeedRandom.SEED_AS_AT.minusMonths(1));
	}

	private Map<String, List<UUID>> departmentPoolsByFamily(Map<String, UUID> departmentIdByCode) {
		Map<String, List<UUID>> pools = new HashMap<>();
		pools.put("ENG", ids(departmentIdByCode, "ENG", "ENG-PLATFORM", "ENG-PRODUCT"));
		pools.put("PRODUCT", ids(departmentIdByCode, "PROD"));
		pools.put("DESIGN", ids(departmentIdByCode, "PROD"));
		pools.put("SALES", ids(departmentIdByCode, "SALES", "SALES-ENT", "SALES-SMB"));
		pools.put("MKTG", ids(departmentIdByCode, "MKTG"));
		pools.put("PEOPLE", ids(departmentIdByCode, "PEOPLE", "PEOPLE-TA", "PEOPLE-LD"));
		pools.put("FIN", ids(departmentIdByCode, "FIN"));
		pools.put("LEGAL", ids(departmentIdByCode, "LEGAL"));
		pools.put("OPS", ids(departmentIdByCode, "OPS"));
		return pools;
	}

	private List<UUID> ids(Map<String, UUID> departmentIdByCode, String... codes) {
		List<UUID> result = new ArrayList<>();
		for (String code : codes) {
			result.add(departmentIdByCode.get(code));
		}
		return result;
	}

}
