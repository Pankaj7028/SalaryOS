package com.acme.salaryos;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLAUDE.md §6.5 / salary-management-backend.md §2.1: {@code hibernate.default_schema} rewrites
 * entity-mapped SQL only. An unqualified table name in {@code @Query(nativeQuery = true)} or a
 * {@code JdbcTemplate} literal resolves against the connection's {@code search_path} — {@code
 * public} on Neon — and fails only in production. This scans every native SQL literal in {@code
 * src/main/java} for a bare mention of a known table name not preceded by {@code salary_schema.}.
 *
 * <p>Heuristic, not a SQL parser: it reconstructs the concatenated string literal(s) passed as the
 * query text, then regex-searches for {@code \btable\b} without a {@code salary_schema.} prefix
 * immediately before it. Good enough for the failure mode this guards against — a copy-pasted
 * table name with the schema prefix dropped — without needing a real SQL grammar.
 */
class NativeQuerySchemaQualificationTest {

	private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");

	/** Every table created by V1-V11 (see db/migration). */
	private static final List<String> TABLE_NAMES = List.of(
			"users", "user_sessions", "password_reset_tokens",
			"countries", "locations", "departments", "job_families", "job_levels",
			"currencies", "reason_codes",
			"employees", "employee_demographics",
			"salary_bands", "fx_rates",
			"compensation_records", "compensation_components", "compensation_changes",
			"audit_events", "employee_current_comp"
	);

	// @Query's "value" attribute can appear first (implicit) or after other named attributes
	// (e.g. nativeQuery = true, value = "...") — try the implicit form, then the named one.
	private static final Pattern QUERY_ANNOTATION_LITERALS_IMPLICIT = Pattern.compile(
			"@Query\\s*\\(\\s*((?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+?\\s*)+)");

	private static final Pattern QUERY_ANNOTATION_LITERALS_NAMED = Pattern.compile(
			"@Query\\s*\\([^)]*?value\\s*=\\s*((?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+?\\s*)+)");

	private static final Pattern JDBC_TEMPLATE_CALL_LITERALS = Pattern.compile(
			"jdbcTemplate\\s*\\.\\s*\\w+\\s*\\(\\s*((?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+?\\s*)+)");

	/**
	 * salary-management-backend.md §6's own analytics convention: a query lives in a {@code private
	 * static final String ...SQL} field, passed to {@code jdbcTemplate} by variable name — which
	 * {@link #JDBC_TEMPLATE_CALL_LITERALS} cannot see, since the literal isn't inline at the call
	 * site. Scans the DECLARATION instead, for both a concatenated-literal constant and a Java text
	 * block (the doc's own example uses a text block). Restricted to names containing {@code SQL}
	 * (matching the doc's naming) so this doesn't false-positive on an unrelated string constant
	 * that happens to contain a word like "employees" in prose.
	 */
	private static final Pattern SQL_CONSTANT_LITERAL_DECLARATION = Pattern.compile(
			"static\\s+final\\s+String\\s+\\w*SQL\\w*\\s*=\\s*((?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+?\\s*)+);");

	private static final Pattern SQL_CONSTANT_TEXT_BLOCK_DECLARATION = Pattern.compile(
			"static\\s+final\\s+String\\s+\\w*SQL\\w*\\s*=\\s*\"\"\"([\\s\\S]*?)\"\"\"\\s*;");

	private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

	// --- unit-level proof, both directions, against crafted fixtures (no real files needed) ---

	@Test
	void flagsAnUnqualifiedTableInAQueryAnnotation() {
		String source = "@Query(nativeQuery = true, value = \"select * from employees where id = ?1\")\n"
				+ "List<Object> find();";
		assertThat(findViolations(source)).contains("employees");
	}

	@Test
	void allowsTheSameQueryOnceSchemaQualified() {
		String source = "@Query(nativeQuery = true, value = \"select * from salary_schema.employees where id = ?1\")\n"
				+ "List<Object> find();";
		assertThat(findViolations(source)).isEmpty();
	}

	@Test
	void flagsAnUnqualifiedTableInAConcatenatedJdbcTemplateLiteral() {
		String source = "jdbcTemplate.queryForList(\n"
				+ "    \"select id from \"\n"
				+ "        + \"employees\",\n"
				+ "    UUID.class);";
		assertThat(findViolations(source)).contains("employees");
	}

	@Test
	void allowsTheSameJdbcTemplateLiteralOnceSchemaQualified() {
		String source = "jdbcTemplate.queryForList(\n"
				+ "    \"select id from \"\n"
				+ "        + \"salary_schema.employees\",\n"
				+ "    UUID.class);";
		assertThat(findViolations(source)).isEmpty();
	}

	@Test
	void flagsAnUnqualifiedTableInASqlConstantTextBlock() {
		String source = "private static final String OVERALL_SQL = \"\"\"\n"
				+ "    SELECT count(*) FROM employee_current_comp\n"
				+ "    \"\"\";\n"
				+ "jdbcTemplate.queryForObject(OVERALL_SQL, Integer.class);";
		assertThat(findViolations(source)).contains("employee_current_comp");
	}

	@Test
	void allowsTheSameSqlConstantTextBlockOnceSchemaQualified() {
		String source = "private static final String OVERALL_SQL = \"\"\"\n"
				+ "    SELECT count(*) FROM salary_schema.employee_current_comp\n"
				+ "    \"\"\";\n"
				+ "jdbcTemplate.queryForObject(OVERALL_SQL, Integer.class);";
		assertThat(findViolations(source)).isEmpty();
	}

	// --- the actual guard: the real source tree must have zero violations ---

	@Test
	void realSourceTreeQualifiesEveryNativeQuery() throws IOException {
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.walk(MAIN_SOURCE_ROOT)) {
			List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).toList();
			for (Path file : javaFiles) {
				for (String table : findViolations(Files.readString(file))) {
					violations.add(file + ": unqualified reference to \"" + table + "\"");
				}
			}
		}
		assertThat(violations)
				.as("every table in a native query must be salary_schema.<table>")
				.isEmpty();
	}

	private static List<String> findViolations(String javaSource) {
		List<String> violations = new ArrayList<>();
		for (String sql : extractNativeSqlLiterals(javaSource)) {
			for (String table : TABLE_NAMES) {
				Pattern unqualified = Pattern.compile("(?<!salary_schema\\.)\\b" + Pattern.quote(table) + "\\b");
				if (unqualified.matcher(sql).find()) {
					violations.add(table);
				}
			}
		}
		return violations;
	}

	private static List<String> extractNativeSqlLiterals(String javaSource) {
		List<String> literalBlocks = new ArrayList<>();
		collectReconstructedLiterals(QUERY_ANNOTATION_LITERALS_IMPLICIT, javaSource, literalBlocks);
		collectReconstructedLiterals(QUERY_ANNOTATION_LITERALS_NAMED, javaSource, literalBlocks);
		collectReconstructedLiterals(JDBC_TEMPLATE_CALL_LITERALS, javaSource, literalBlocks);
		collectReconstructedLiterals(SQL_CONSTANT_LITERAL_DECLARATION, javaSource, literalBlocks);
		collectRawTextBlocks(SQL_CONSTANT_TEXT_BLOCK_DECLARATION, javaSource, literalBlocks);
		return literalBlocks;
	}

	private static void collectRawTextBlocks(Pattern blockPattern, String javaSource, List<String> out) {
		Matcher blockMatcher = blockPattern.matcher(javaSource);
		while (blockMatcher.find()) {
			out.add(blockMatcher.group(1));
		}
	}

	private static void collectReconstructedLiterals(Pattern blockPattern, String javaSource, List<String> out) {
		Matcher blockMatcher = blockPattern.matcher(javaSource);
		while (blockMatcher.find()) {
			String rawBlock = blockMatcher.group(1);
			StringBuilder reconstructed = new StringBuilder();
			Matcher literalMatcher = STRING_LITERAL.matcher(rawBlock);
			while (literalMatcher.find()) {
				reconstructed.append(literalMatcher.group(1).replace("\\\"", "\""));
			}
			out.add(reconstructed.toString());
		}
	}

}
