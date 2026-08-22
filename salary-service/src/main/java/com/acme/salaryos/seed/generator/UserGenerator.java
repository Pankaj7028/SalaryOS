package com.acme.salaryos.seed.generator;

import com.acme.salaryos.seed.SeedRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 6 users: one per role, plus two spare HR Managers so the "who approved this" fields in the
 * changes/audit generators have more than one plausible decider (backend doc §9). Passwords are
 * seed-random but real, hashed with the app's own {@link PasswordEncoder}, and printed to the
 * console exactly once — there is no email transport in v1 (CLAUDE.md §1.6) to send them by. */
@Slf4j
@Component
public class UserGenerator {

	private final JdbcTemplate jdbc;
	private final PasswordEncoder passwordEncoder;

	public UserGenerator(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
	}

	public record SeededUser(UUID id, String email, String fullName, String role) {
	}

	private static final String[] WORDS = {
			"harbor", "cedar", "quartz", "meadow", "signal", "compass", "orbit", "granite", "willow", "beacon" };

	public List<SeededUser> seedUsers(SeedRandom random) {
		record Spec(String email, String fullName, String role) {
		}
		List<Spec> specs = List.of(
				new Spec("admin@acme.test", "Ada Admin", "HR_ADMIN"),
				new Spec("manager@acme.test", "Marcus Manager", "HR_MANAGER"),
				new Spec("analyst@acme.test", "Ana Analyst", "COMP_ANALYST"),
				new Spec("auditor@acme.test", "Aiden Auditor", "AUDITOR"),
				new Spec("jordan.manager@acme.test", "Jordan Blake", "HR_MANAGER"),
				new Spec("priya.manager@acme.test", "Priya Shah", "HR_MANAGER"));

		List<SeededUser> users = new ArrayList<>();
		List<Object[]> rows = new ArrayList<>();
		StringBuilder printed = new StringBuilder("\n\n==== SEEDED LOGIN CREDENTIALS (shown once) ====\n");

		for (Spec spec : specs) {
			UUID id = random.uuid();
			String password = random.pick(WORDS) + "-" + random.pick(WORDS) + "-" + random.nextInt(1000, 9999);
			// DelegatingPasswordEncoder (SecurityConfig, keyed "argon2") already returns the
			// "{argon2}$argon2id$..." form — no manual prefixing needed.
			String hash = passwordEncoder.encode(password);
			rows.add(new Object[] { id, spec.email(), spec.fullName(), hash, spec.role() });
			users.add(new SeededUser(id, spec.email(), spec.fullName(), spec.role()));
			printed.append(String.format("  %-28s %-22s role=%s%n", spec.email(), password, spec.role()));
		}
		printed.append("================================================\n");

		jdbc.batchUpdate(
				"insert into salary_schema.users (id, email, full_name, password_hash, role) values (?, ?, ?, ?, ?)",
				rows);

		log.info(printed.toString());
		return users;
	}

}
