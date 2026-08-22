package com.acme.salaryos.seed.generator;

import com.acme.salaryos.seed.SeedRandom;
import com.acme.salaryos.seed.generator.EmployeeGenerator.SeededEmployee;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ~1,200 {@code compensation_changes} across all five statuses, including 60 {@code PENDING} so
 * the approval queue isn't empty (backend doc §9) — seeded independently of {@link
 * CompensationGenerator}'s ledger, not derived from it. An {@code APPLIED} row here does NOT
 * point {@code applied_record_id} at one of that generator's rows: reproducing which specific
 * historical raise "was" a formally-approved change, for 10,000 people's ledgers, is a lot of
 * bookkeeping for a fact nothing in the product actually checks (no test or screen asks "does
 * every applied change have a real backing record" for seed data specifically) — {@code
 * applied_record_id} stays {@code NULL} for backfilled history, which the column already allows.
 * {@code DRAFT}/{@code PENDING}/{@code APPROVED} rows respect {@code one_open_change_per_employee}
 * by construction: each employee gets at most one such row.
 */
@Component
public class ChangeGenerator {

	private static final int PENDING_COUNT = 60;
	private static final int DRAFT_COUNT = 20;
	private static final int APPROVED_COUNT = 20;
	private static final int TOTAL_TARGET = 1_200;

	private static final String[] PROPOSABLE_REASONS = {
			"MERIT", "MERIT", "MERIT", "PROMOTION", "MARKET_ADJUSTMENT", "ROLE_CHANGE", "LOCATION_CHANGE" };

	private final JdbcTemplate jdbc;

	public ChangeGenerator(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void seedChanges(
			SeedRandom random, List<SeededEmployee> employees, Map<UUID, BigDecimal> currentAnnualBaseByEmployee,
			Map<UUID, String> currentCurrencyByEmployee, List<UUID> proposerUserIds, List<UUID> deciderUserIds) {

		List<SeededEmployee> eligible = employees.stream()
				.filter(e -> currentAnnualBaseByEmployee.containsKey(e.id()))
				.toList();

		List<Object[]> rows = new ArrayList<>();
		Set<UUID> nonTerminalUsed = new HashSet<>();

		int nonTerminalCount = PENDING_COUNT + DRAFT_COUNT + APPROVED_COUNT;
		List<SeededEmployee> nonTerminalPool = sample(random, eligible, nonTerminalCount);
		int idx = 0;
		for (int i = 0; i < PENDING_COUNT && idx < nonTerminalPool.size(); i++, idx++) {
			rows.add(nonTerminalRow(random, nonTerminalPool.get(idx), "PENDING",
					currentAnnualBaseByEmployee, currentCurrencyByEmployee, proposerUserIds, deciderUserIds));
			nonTerminalUsed.add(nonTerminalPool.get(idx).id());
		}
		for (int i = 0; i < DRAFT_COUNT && idx < nonTerminalPool.size(); i++, idx++) {
			rows.add(nonTerminalRow(random, nonTerminalPool.get(idx), "DRAFT",
					currentAnnualBaseByEmployee, currentCurrencyByEmployee, proposerUserIds, deciderUserIds));
			nonTerminalUsed.add(nonTerminalPool.get(idx).id());
		}
		for (int i = 0; i < APPROVED_COUNT && idx < nonTerminalPool.size(); i++, idx++) {
			rows.add(nonTerminalRow(random, nonTerminalPool.get(idx), "APPROVED",
					currentAnnualBaseByEmployee, currentCurrencyByEmployee, proposerUserIds, deciderUserIds));
			nonTerminalUsed.add(nonTerminalPool.get(idx).id());
		}

		int terminalTarget = TOTAL_TARGET - rows.size();
		for (int i = 0; i < terminalTarget; i++) {
			SeededEmployee employee = random.pick(eligible);
			String status = random.chance(0.82) ? "APPLIED" : "REJECTED";
			rows.add(terminalRow(random, employee, status, currentAnnualBaseByEmployee, currentCurrencyByEmployee, proposerUserIds, deciderUserIds));
		}

		batchInsert(rows);
	}

	private Object[] nonTerminalRow(
			SeedRandom random, SeededEmployee employee, String status,
			Map<UUID, BigDecimal> currentAnnualBaseByEmployee, Map<UUID, String> currentCurrencyByEmployee,
			List<UUID> proposerUserIds, List<UUID> deciderUserIds) {
		BigDecimal currentBase = currentAnnualBaseByEmployee.get(employee.id());
		String currency = currentCurrencyByEmployee.get(employee.id());
		BigDecimal newBase = currentBase.multiply(BigDecimal.valueOf(1.03 + random.nextDouble() * 0.10)).setScale(2, RoundingMode.HALF_UP);
		String reason = random.pick(PROPOSABLE_REASONS);
		LocalDate effectiveDate = SeedRandom.SEED_AS_AT.plusDays(random.nextInt(7, 60));
		UUID proposedBy = random.pick(proposerUserIds);
		java.sql.Timestamp proposedAt = instantOn(SeedRandom.SEED_AS_AT.minusDays(random.nextInt(1, 20)));

		UUID decidedBy = null;
		java.sql.Timestamp decidedAt = null;
		if ("APPROVED".equals(status)) {
			decidedBy = random.pick(deciderUserIds);
			decidedAt = instantOn(SeedRandom.SEED_AS_AT.minusDays(random.nextInt(0, 10)));
		}

		return new Object[] {
				random.uuid(), employee.id(), status, effectiveDate, currentBase, newBase, currency, reason,
				random.chance(0.4) ? performanceRating(random) : null, random.chance(0.3) ? "Seed-generated note." : null,
				proposedBy, proposedAt, decidedBy, decidedAt, "APPROVED".equals(status) ? "Looks good." : null,
				null, null };
	}

	private Object[] terminalRow(
			SeedRandom random, SeededEmployee employee, String status,
			Map<UUID, BigDecimal> currentAnnualBaseByEmployee, Map<UUID, String> currentCurrencyByEmployee,
			List<UUID> proposerUserIds, List<UUID> deciderUserIds) {
		BigDecimal referenceBase = currentAnnualBaseByEmployee.get(employee.id());
		// Terminal (historical) rows aren't the employee's literal current base -- they're a
		// plausible past proposal, scaled down from today's figure so REJECTED/old APPLIED rows
		// don't all coincidentally equal exactly what the person earns right now.
		BigDecimal currentBase = referenceBase.multiply(BigDecimal.valueOf(0.75 + random.nextDouble() * 0.20)).setScale(2, RoundingMode.HALF_UP);
		BigDecimal newBase = currentBase.multiply(BigDecimal.valueOf(1.03 + random.nextDouble() * 0.12)).setScale(2, RoundingMode.HALF_UP);
		String currency = currentCurrencyByEmployee.get(employee.id());
		String reason = random.pick(PROPOSABLE_REASONS);
		LocalDate effectiveDate = SeedRandom.SEED_AS_AT.minusMonths(random.nextInt(1, 60));
		UUID proposedBy = random.pick(proposerUserIds);
		java.sql.Timestamp proposedAt = instantOn(effectiveDate.minusDays(random.nextInt(5, 30)));
		UUID decidedBy = random.pick(deciderUserIds);
		java.sql.Timestamp decidedAt = instantOn(effectiveDate.minusDays(random.nextInt(0, 5)));
		java.sql.Timestamp appliedAt = "APPLIED".equals(status) ? instantOn(effectiveDate) : null;

		return new Object[] {
				random.uuid(), employee.id(), status, effectiveDate, currentBase, newBase, currency, reason,
				random.chance(0.5) ? performanceRating(random) : null, random.chance(0.3) ? "Seed-generated note." : null,
				proposedBy, proposedAt, decidedBy, decidedAt,
				"REJECTED".equals(status) ? "Budget constraints this cycle." : "Approved.",
				appliedAt, null };
	}

	private String performanceRating(SeedRandom random) {
		return random.pick(List.of("EXCEEDS", "MEETS", "BELOW"));
	}

	private java.sql.Timestamp instantOn(LocalDate date) {
		return java.sql.Timestamp.from(date.atStartOfDay(ZoneOffset.UTC).toInstant());
	}

	private List<SeededEmployee> sample(SeedRandom random, List<SeededEmployee> pool, int count) {
		List<SeededEmployee> shuffled = new ArrayList<>(pool);
		for (int i = shuffled.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			SeededEmployee tmp = shuffled.get(i);
			shuffled.set(i, shuffled.get(j));
			shuffled.set(j, tmp);
		}
		return shuffled.subList(0, Math.min(count, shuffled.size()));
	}

	private void batchInsert(List<Object[]> rows) {
		for (int i = 0; i < rows.size(); i += 1000) {
			jdbc.batchUpdate(
					"insert into salary_schema.compensation_changes (id, employee_id, status, effective_date, "
							+ "current_base_amount, new_base_amount, currency, change_reason, performance_rating, note, "
							+ "proposed_by, proposed_at, decided_by, decided_at, decision_note, applied_at, applied_record_id) "
							+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
					rows.subList(i, Math.min(i + 1000, rows.size())));
		}
	}

}
