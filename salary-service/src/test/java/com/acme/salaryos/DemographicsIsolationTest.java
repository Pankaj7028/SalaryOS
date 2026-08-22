package com.acme.salaryos;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P9.3 / CLAUDE.md §6.6: {@code employee_demographics} never reaches an individual DTO. Outside
 * the {@code analytics} package, no DTO record component may be a demographic attribute (gender,
 * ethnicity, date of birth, age). {@code analytics} is exempt by design — its DTOs (e.g. {@code
 * PayGapGroupMedian}) carry a demographic VALUE under a generic {@code group} label for a cohort
 * of five or more, never a named field attached to one person; see their own javadoc.
 *
 * <p>Scans DTO record headers straight out of {@code src/main/java} — same style as {@code
 * NativeQuerySchemaQualificationTest} — rather than reflection over compiled classes. Every DTO in
 * this codebase is a {@code record}, and a record's components ARE its entire public shape, so
 * there's no getter/field split a source scan could miss.
 */
class DemographicsIsolationTest {

	private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java/com/acme/salaryos");

	/** Exact, normalized (lowercase letters only) component names that would leak a demographic
	 * attribute — an exact-match set, not substring matching: "age" as a substring would
	 * false-positive on names like "wage" or "pageSize" that have nothing to do with demographics. */
	private static final Set<String> FORBIDDEN_NAMES = Set.of(
			"gender", "sex", "ethnicity", "ethnicitycode", "race", "age",
			"dateofbirth", "dob", "birthdate", "demographic", "demographics");

	private static final Pattern RECORD_HEADER = Pattern.compile("\\brecord\\s+\\w+\\s*\\(");

	@Test
	void noDtoOutsideAnalyticsExposesADemographicAttribute() throws IOException {
		List<String> violations = new ArrayList<>();

		try (Stream<Path> files = Files.walk(MAIN_SOURCE_ROOT)) {
			files.filter(p -> p.toString().endsWith(".java"))
					.filter(p -> p.toString().contains("/dto/"))
					.filter(p -> !p.toString().contains("/analytics/"))
					.forEach(path -> {
						try {
							String source = Files.readString(path);
							for (String component : recordComponentNames(source)) {
								String normalized = component.toLowerCase().replaceAll("[^a-z]", "");
								if (FORBIDDEN_NAMES.contains(normalized)) {
									violations.add(path + " -> " + component);
								}
							}
						}
						catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					});
		}

		assertThat(violations)
				.as("DTOs outside analytics must never carry a demographic attribute (CLAUDE.md §6.6)")
				.isEmpty();
	}

	/** Every {@code record Name(...)} header in the file, including nested records — the balanced
	 * paren scan doesn't care how deep the declaration sits. */
	private List<String> recordComponentNames(String source) {
		List<String> names = new ArrayList<>();
		Matcher matcher = RECORD_HEADER.matcher(source);
		while (matcher.find()) {
			int parenStart = matcher.end() - 1;
			int depth = 0;
			int i = parenStart;
			for (; i < source.length(); i++) {
				char c = source.charAt(i);
				if (c == '(') {
					depth++;
				}
				else if (c == ')') {
					depth--;
					if (depth == 0) {
						break;
					}
				}
			}
			names.addAll(splitTopLevelParams(source.substring(parenStart + 1, i)));
		}
		return names;
	}

	/** Splits a record's parameter list on top-level commas only — a comma inside {@code List<A,
	 * B>} or an annotation's {@code (...)} args must not split a parameter in two. */
	private List<String> splitTopLevelParams(String paramList) {
		List<String> params = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < paramList.length(); i++) {
			char c = paramList.charAt(i);
			if (c == '(' || c == '<') {
				depth++;
			}
			else if (c == ')' || c == '>') {
				depth--;
			}
			else if (c == ',' && depth == 0) {
				params.add(paramList.substring(start, i));
				start = i + 1;
			}
		}
		if (start < paramList.length()) {
			params.add(paramList.substring(start));
		}

		List<String> names = new ArrayList<>();
		for (String param : params) {
			String trimmed = param.trim().replaceAll("@\\w+(\\([^)]*\\))?\\s*", "").trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			String[] tokens = trimmed.split("\\s+");
			names.add(tokens[tokens.length - 1]);
		}
		return names;
	}

}
