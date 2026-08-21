package com.acme.salaryos.analytics.service;

import com.acme.salaryos.analytics.dto.AnalyticsPopulation;
import com.acme.salaryos.analytics.dto.CompaRatioDistributionResponse;
import com.acme.salaryos.analytics.dto.HeadcountGroup;
import com.acme.salaryos.analytics.dto.HeadcountResponse;
import com.acme.salaryos.analytics.dto.IncreaseCycleResponse;
import com.acme.salaryos.analytics.dto.OutOfBandResponse;
import com.acme.salaryos.analytics.dto.OutOfBandRow;
import com.acme.salaryos.analytics.dto.PayGapCohortRow;
import com.acme.salaryos.analytics.dto.PayGapGroupMedian;
import com.acme.salaryos.analytics.dto.PayGapResponse;
import com.acme.salaryos.analytics.dto.PayrollCostResponse;
import com.acme.salaryos.analytics.query.CompaRatioDistributionQuery;
import com.acme.salaryos.analytics.query.HeadcountQuery;
import com.acme.salaryos.analytics.query.IncreaseCycleQuery;
import com.acme.salaryos.analytics.query.OutOfBandQuery;
import com.acme.salaryos.analytics.query.PayGapQuery;
import com.acme.salaryos.analytics.query.PayrollCostQuery;
import com.acme.salaryos.common.money.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** FR-6.1 / FR-6.2 / FR-6.3 / FR-6.4 / FR-6.8: payroll cost, headcount, out-of-band, compa-ratio
 * distribution, and pay gap, each carrying its own basis envelope. */
@Service
public class AnalyticsService {

	private final PayrollCostQuery payrollCostQuery;
	private final HeadcountQuery headcountQuery;
	private final OutOfBandQuery outOfBandQuery;
	private final CompaRatioDistributionQuery compaRatioDistributionQuery;
	private final PayGapQuery payGapQuery;
	private final IncreaseCycleQuery increaseCycleQuery;
	private final Clock clock;
	private final String baseCurrency;

	public AnalyticsService(
			PayrollCostQuery payrollCostQuery, HeadcountQuery headcountQuery, OutOfBandQuery outOfBandQuery,
			CompaRatioDistributionQuery compaRatioDistributionQuery, PayGapQuery payGapQuery,
			IncreaseCycleQuery increaseCycleQuery, Clock clock,
			@Value("${app.base-currency}") String baseCurrency) {
		this.payrollCostQuery = payrollCostQuery;
		this.headcountQuery = headcountQuery;
		this.outOfBandQuery = outOfBandQuery;
		this.compaRatioDistributionQuery = compaRatioDistributionQuery;
		this.payGapQuery = payGapQuery;
		this.increaseCycleQuery = increaseCycleQuery;
		this.clock = clock;
		this.baseCurrency = baseCurrency;
	}

	public PayrollCostResponse payrollCost() {
		var overall = payrollCostQuery.overall(baseCurrency);
		AnalyticsPopulation population = new AnalyticsPopulation(
				overall.headcount(), Map.of("terminated", payrollCostQuery.terminatedCount()));

		return new PayrollCostResponse(
				LocalDate.now(clock), baseCurrency, population, overall,
				payrollCostQuery.byCountry(baseCurrency),
				payrollCostQuery.byDepartment(baseCurrency),
				payrollCostQuery.byLevel(baseCurrency));
	}

	public HeadcountResponse headcount() {
		List<HeadcountGroup> byStatus = headcountQuery.byStatus();
		int terminatedCount = byStatus.stream()
				.filter(g -> "TERMINATED".equals(g.key()))
				.mapToInt(HeadcountGroup::headcount)
				.sum();

		AnalyticsPopulation population = new AnalyticsPopulation(headcountQuery.overall(), Map.of("terminated", terminatedCount));

		return new HeadcountResponse(
				LocalDate.now(clock), population,
				headcountQuery.byCountry(), headcountQuery.byDepartment(), headcountQuery.byLevel(), byStatus);
	}

	public OutOfBandResponse outOfBand() {
		List<OutOfBandRow> rows = outOfBandQuery.rows();
		int belowMinCount = outOfBandQuery.countByStatus("BELOW_MIN");
		int aboveMaxCount = outOfBandQuery.countByStatus("ABOVE_MAX");
		AnalyticsPopulation population = new AnalyticsPopulation(
				headcountQuery.overall(), Map.of("terminated", payrollCostQuery.terminatedCount()));

		return new OutOfBandResponse(
				LocalDate.now(clock), baseCurrency, population, belowMinCount, aboveMaxCount,
				new Money(outOfBandQuery.totalCostToMinimum(), baseCurrency), rows);
	}

	public CompaRatioDistributionResponse compaRatioDistribution(UUID departmentId, UUID jobLevelId, String countryCode) {
		var quartiles = compaRatioDistributionQuery.quartiles(departmentId, jobLevelId, countryCode);
		int noBand = compaRatioDistributionQuery.noBandCount(departmentId, jobLevelId, countryCode);
		AnalyticsPopulation population = new AnalyticsPopulation(quartiles.count(), Map.of("noBand", noBand));

		return new CompaRatioDistributionResponse(
				LocalDate.now(clock), population, quartiles.p25(), quartiles.median(), quartiles.p75(),
				compaRatioDistributionQuery.histogram(departmentId, jobLevelId, countryCode),
				compaRatioDistributionQuery.byDepartment(departmentId, jobLevelId, countryCode),
				compaRatioDistributionQuery.byLevel(departmentId, jobLevelId, countryCode),
				compaRatioDistributionQuery.byCountry(departmentId, jobLevelId, countryCode));
	}

	public PayGapResponse payGap() {
		List<PayGapGroupMedian> unadjustedGroups = payGapQuery.unadjustedGroups().stream()
				.map(g -> new PayGapGroupMedian(g.group(), g.count(), new Money(g.median(), baseCurrency)))
				.toList();
		BigDecimal[] unadjustedGap = gapAcrossGroups(unadjustedGroups);

		Map<String, List<PayGapQuery.CohortGroupRow>> byCohort = new LinkedHashMap<>();
		for (PayGapQuery.CohortGroupRow row : payGapQuery.cohortGroups()) {
			byCohort.computeIfAbsent(row.jobLevelId() + "|" + row.countryCode(), k -> new ArrayList<>()).add(row);
		}

		List<PayGapCohortRow> cohorts = new ArrayList<>();
		for (List<PayGapQuery.CohortGroupRow> rows : byCohort.values()) {
			if (rows.size() < 2) {
				continue; // only one demographic group survived suppression here -- nothing to compare
			}
			List<PayGapGroupMedian> groups = rows.stream()
					.map(g -> new PayGapGroupMedian(g.group(), g.count(), new Money(g.median(), baseCurrency)))
					.toList();
			BigDecimal[] gap = gapAcrossGroups(groups);
			PayGapQuery.CohortGroupRow first = rows.get(0);
			cohorts.add(new PayGapCohortRow(
					first.jobLevelId(), first.jobLevelLabel(), first.countryCode(), first.countryLabel(),
					groups, new Money(gap[0], baseCurrency), gap[1]));
		}

		int suppressedCohorts = payGapQuery.totalCohortsWithDemographicCoverage() - cohorts.size();
		AnalyticsPopulation population = new AnalyticsPopulation(
				unadjustedGroups.stream().mapToInt(PayGapGroupMedian::count).sum(), Map.of());

		return new PayGapResponse(
				LocalDate.now(clock), baseCurrency, population,
				unadjustedGroups, new Money(unadjustedGap[0], baseCurrency), unadjustedGap[1],
				cohorts, suppressedCohorts);
	}

	public IncreaseCycleResponse increaseCycle(LocalDate fromDate, LocalDate toDate, BigDecimal budget) {
		var overall = increaseCycleQuery.overall(fromDate, toDate);
		AnalyticsPopulation population = new AnalyticsPopulation(overall.count(), Map.of());

		BigDecimal burnPercent = (budget == null || budget.signum() == 0)
				? null
				: overall.total().divide(budget, 6, RoundingMode.HALF_UP);

		return new IncreaseCycleResponse(
				LocalDate.now(clock), fromDate, toDate, baseCurrency, population,
				new Money(overall.total(), baseCurrency), overall.avgPercent(), overall.medianPercent(),
				increaseCycleQuery.byReason(fromDate, toDate, baseCurrency),
				budget == null ? null : new Money(budget, baseCurrency), burnPercent);
	}

	/** {@code [gapAmount, gapPercent]} between the highest and lowest group median — the widest
	 * spread actually observed, since the schema has no fixed two-value enumeration to compare
	 * directionally against. {@code gapPercent} is relative to the highest median. Zero for fewer
	 * than two groups (nothing to compare). */
	private BigDecimal[] gapAcrossGroups(List<PayGapGroupMedian> groups) {
		if (groups.size() < 2) {
			return new BigDecimal[] { BigDecimal.ZERO.setScale(2), BigDecimal.ZERO };
		}
		BigDecimal highest = groups.stream().map(g -> g.median().amount()).max(Comparator.naturalOrder()).orElseThrow();
		BigDecimal lowest = groups.stream().map(g -> g.median().amount()).min(Comparator.naturalOrder()).orElseThrow();
		BigDecimal gapAmount = highest.subtract(lowest);
		BigDecimal gapPercent = highest.signum() == 0 ? BigDecimal.ZERO : gapAmount.divide(highest, 6, RoundingMode.HALF_UP);
		return new BigDecimal[] { gapAmount, gapPercent };
	}

}
