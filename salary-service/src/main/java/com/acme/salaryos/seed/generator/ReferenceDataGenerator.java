package com.acme.salaryos.seed.generator;

import com.acme.salaryos.seed.SeedRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Countries, locations, departments, job families/levels (backend doc §9's shape table). Static
 * in content — the RANDOM part is only which id each row gets, via {@link SeedRandom#uuid()} — so
 * the org structure itself reads the same across every reseed, only the ids differ from a real
 * environment's.
 */
@Component
public class ReferenceDataGenerator {

	private final JdbcTemplate jdbc;

	public ReferenceDataGenerator(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Country(String code, String name, String currency) {
	}

	public record Location(UUID id, String countryCode, String city, String name) {
	}

	public record Department(UUID id, String name, String code, UUID parentId) {
	}

	public record JobFamily(UUID id, String name, String code) {
	}

	public record JobLevel(UUID id, UUID jobFamilyId, String levelCode, String title, int sortOrder) {
	}

	/** 8 countries, deliberately spread across currency magnitudes (backend doc §9) so
	 * formatting and normalisation get exercised — INR and BRL break naive column widths. */
	public static final List<Country> COUNTRIES = List.of(
			new Country("US", "United States", "USD"),
			new Country("GB", "United Kingdom", "GBP"),
			new Country("DE", "Germany", "EUR"),
			new Country("IN", "India", "INR"),
			new Country("SG", "Singapore", "SGD"),
			new Country("BR", "Brazil", "BRL"),
			new Country("PL", "Poland", "PLN"),
			new Country("IE", "Ireland", "EUR"));

	public List<Country> seedCountries() {
		jdbc.batchUpdate(
				"insert into salary_schema.countries (code, name, default_currency) values (?, ?, ?)",
				COUNTRIES.stream().map(c -> new Object[] { c.code(), c.name(), c.currency() }).toList());
		return COUNTRIES;
	}

	/** 18 locations, 1–4 cities per country. */
	public List<Location> seedLocations(SeedRandom random) {
		List<Object[]> cities = List.of(
				new Object[] { "US", "New York" }, new Object[] { "US", "San Francisco" },
				new Object[] { "US", "Austin" }, new Object[] { "US", "Seattle" },
				new Object[] { "GB", "London" }, new Object[] { "GB", "Manchester" },
				new Object[] { "DE", "Berlin" }, new Object[] { "DE", "Munich" },
				new Object[] { "IN", "Bengaluru" }, new Object[] { "IN", "Mumbai" }, new Object[] { "IN", "Delhi" },
				new Object[] { "SG", "Singapore" },
				new Object[] { "BR", "São Paulo" }, new Object[] { "BR", "Rio de Janeiro" },
				new Object[] { "PL", "Warsaw" }, new Object[] { "PL", "Kraków" },
				new Object[] { "IE", "Dublin" }, new Object[] { "IE", "Cork" });

		List<Location> locations = cities.stream()
				.map(row -> new Location(random.uuid(), (String) row[0], (String) row[1], (String) row[1]))
				.toList();

		jdbc.batchUpdate(
				"insert into salary_schema.locations (id, country_code, city, name, is_active) values (?, ?, ?, ?, true)",
				locations.stream().map(l -> new Object[] { l.id(), l.countryCode(), l.city(), l.name() }).toList());
		return locations;
	}

	/** 14 departments, two-level hierarchy: 8 top-level, 6 second-level under three of them. */
	public List<Department> seedDepartments(SeedRandom random) {
		List<Department> topLevel = List.of(
				new Department(random.uuid(), "Engineering", "ENG", null),
				new Department(random.uuid(), "Product", "PROD", null),
				new Department(random.uuid(), "Sales", "SALES", null),
				new Department(random.uuid(), "Marketing", "MKTG", null),
				new Department(random.uuid(), "People", "PEOPLE", null),
				new Department(random.uuid(), "Finance", "FIN", null),
				new Department(random.uuid(), "Legal", "LEGAL", null),
				new Department(random.uuid(), "Operations", "OPS", null));

		UUID engineeringId = topLevel.get(0).id();
		UUID salesId = topLevel.get(2).id();
		UUID peopleId = topLevel.get(4).id();

		List<Department> secondLevel = List.of(
				new Department(random.uuid(), "Platform Engineering", "ENG-PLATFORM", engineeringId),
				new Department(random.uuid(), "Product Engineering", "ENG-PRODUCT", engineeringId),
				new Department(random.uuid(), "Enterprise Sales", "SALES-ENT", salesId),
				new Department(random.uuid(), "SMB Sales", "SALES-SMB", salesId),
				new Department(random.uuid(), "Talent Acquisition", "PEOPLE-TA", peopleId),
				new Department(random.uuid(), "Learning & Development", "PEOPLE-LD", peopleId));

		List<Department> all = new ArrayList<>(topLevel);
		all.addAll(secondLevel);

		jdbc.batchUpdate(
				"insert into salary_schema.departments (id, name, code, parent_id) values (?, ?, ?, ?)",
				all.stream().map(d -> new Object[] { d.id(), d.name(), d.code(), d.parentId() }).toList());
		return all;
	}

	/** 9 job families, 7 levels each (L1–L7, backend doc §9). */
	public record JobFamilySeed(JobFamily family, List<JobLevel> levels) {
	}

	private static final String[] LEVEL_CODES = { "L1", "L2", "L3", "L4", "L5", "L6", "L7" };

	public List<JobFamilySeed> seedJobFamiliesAndLevels(SeedRandom random) {
		record FamilySpec(String name, String code, String[] titles) {
		}
		List<FamilySpec> specs = List.of(
				new FamilySpec("Engineering", "ENG", new String[] {
						"Associate Engineer", "Engineer II", "Senior Engineer", "Staff Engineer",
						"Principal Engineer", "Director of Engineering", "VP of Engineering" }),
				new FamilySpec("Product", "PRODUCT", new String[] {
						"Associate Product Manager", "Product Manager", "Senior Product Manager",
						"Group Product Manager", "Principal Product Manager", "Director of Product", "VP of Product" }),
				new FamilySpec("Design", "DESIGN", new String[] {
						"Associate Designer", "Product Designer", "Senior Product Designer", "Staff Designer",
						"Principal Designer", "Design Director", "VP of Design" }),
				new FamilySpec("Sales", "SALES", new String[] {
						"Sales Development Rep", "Account Executive", "Senior Account Executive",
						"Enterprise Account Executive", "Strategic Account Director", "Director of Sales", "VP of Sales" }),
				new FamilySpec("Marketing", "MKTG", new String[] {
						"Marketing Associate", "Marketing Manager", "Senior Marketing Manager", "Marketing Lead",
						"Principal Marketing Manager", "Director of Marketing", "VP of Marketing" }),
				new FamilySpec("People", "PEOPLE", new String[] {
						"People Coordinator", "People Partner", "Senior People Partner", "People Programs Lead",
						"Principal People Partner", "Director of People", "VP of People" }),
				new FamilySpec("Finance", "FIN", new String[] {
						"Financial Analyst", "Senior Financial Analyst", "Finance Manager", "Senior Finance Manager",
						"Principal Financial Analyst", "Director of Finance", "VP of Finance" }),
				new FamilySpec("Legal", "LEGAL", new String[] {
						"Legal Coordinator", "Corporate Counsel", "Senior Counsel", "Lead Counsel",
						"Principal Counsel", "Director of Legal", "General Counsel" }),
				new FamilySpec("Operations", "OPS", new String[] {
						"Operations Associate", "Operations Manager", "Senior Operations Manager", "Operations Lead",
						"Principal Operations Manager", "Director of Operations", "VP of Operations" }));

		List<JobFamily> families = specs.stream()
				.map(s -> new JobFamily(random.uuid(), s.name(), s.code()))
				.toList();

		jdbc.batchUpdate(
				"insert into salary_schema.job_families (id, name, code) values (?, ?, ?)",
				families.stream().map(f -> new Object[] { f.id(), f.name(), f.code() }).toList());

		List<JobFamilySeed> result = new ArrayList<>();
		List<JobLevel> allLevels = new ArrayList<>();
		for (int i = 0; i < specs.size(); i++) {
			JobFamily family = families.get(i);
			FamilySpec spec = specs.get(i);
			List<JobLevel> levels = new ArrayList<>();
			for (int levelIndex = 0; levelIndex < LEVEL_CODES.length; levelIndex++) {
				JobLevel level = new JobLevel(
						random.uuid(), family.id(), LEVEL_CODES[levelIndex], spec.titles()[levelIndex], levelIndex + 1);
				levels.add(level);
				allLevels.add(level);
			}
			result.add(new JobFamilySeed(family, levels));
		}

		jdbc.batchUpdate(
				"insert into salary_schema.job_levels (id, job_family_id, level_code, title, sort_order) values (?, ?, ?, ?, ?)",
				allLevels.stream()
						.map(l -> new Object[] { l.id(), l.jobFamilyId(), l.levelCode(), l.title(), l.sortOrder() })
						.toList());
		return result;
	}

}
