package com.acme.salaryos.analytics.service;

import com.acme.salaryos.analytics.dto.AnalyticsPopulation;
import com.acme.salaryos.analytics.dto.HeadcountGroup;
import com.acme.salaryos.analytics.dto.HeadcountResponse;
import com.acme.salaryos.analytics.dto.OutOfBandResponse;
import com.acme.salaryos.analytics.dto.OutOfBandRow;
import com.acme.salaryos.analytics.dto.PayrollCostResponse;
import com.acme.salaryos.analytics.query.HeadcountQuery;
import com.acme.salaryos.analytics.query.OutOfBandQuery;
import com.acme.salaryos.analytics.query.PayrollCostQuery;
import com.acme.salaryos.common.money.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** FR-6.1 / FR-6.2 / FR-6.8: payroll cost, headcount, and out-of-band, each carrying its own
 * basis envelope. */
@Service
public class AnalyticsService {

	private final PayrollCostQuery payrollCostQuery;
	private final HeadcountQuery headcountQuery;
	private final OutOfBandQuery outOfBandQuery;
	private final Clock clock;
	private final String baseCurrency;

	public AnalyticsService(
			PayrollCostQuery payrollCostQuery, HeadcountQuery headcountQuery, OutOfBandQuery outOfBandQuery, Clock clock,
			@Value("${app.base-currency}") String baseCurrency) {
		this.payrollCostQuery = payrollCostQuery;
		this.headcountQuery = headcountQuery;
		this.outOfBandQuery = outOfBandQuery;
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

}
