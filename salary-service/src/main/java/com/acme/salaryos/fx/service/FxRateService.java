package com.acme.salaryos.fx.service;

import com.acme.salaryos.audit.AuditService;
import com.acme.salaryos.fx.FxRate;
import com.acme.salaryos.fx.FxRateRepository;
import com.acme.salaryos.fx.dto.CreateFxRateRequest;
import com.acme.salaryos.fx.dto.FxCoverageCell;
import com.acme.salaryos.fx.dto.FxCoverageResponse;
import com.acme.salaryos.fx.dto.FxCoverageRow;
import com.acme.salaryos.fx.dto.FxRateResponse;
import com.acme.salaryos.fx.dto.MissingFxRateMonth;
import com.acme.salaryos.reference.domain.Country;
import com.acme.salaryos.reference.repository.CountryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FX rates are normalisation reference data (CLAUDE.md §6.4): every comp record pins the rate it
 * used at write time, so this admin surface exists to keep the trailing window of months covered
 * going forward — it never touches, and cannot touch, a rate a past record already pinned.
 */
@Service
public class FxRateService {

	/** How far back "missing" looks — a year of history plus the current month. */
	private static final int TRAILING_MONTHS = 13;
	/** How far ahead "missing" looks — a proposal is routinely dated a cycle or two out
	 * (P8's QA pass: proposing a change for next month 422'd on a genuinely missing rate that
	 * this screen never surfaced, because it only looked backward). */
	private static final int LOOKAHEAD_MONTHS = 3;

	private final FxRateRepository fxRateRepository;
	private final CountryRepository countryRepository;
	private final AuditService auditService;
	private final Clock clock;
	private final String baseCurrency;
	private final JdbcTemplate jdbcTemplate;

	public FxRateService(
			FxRateRepository fxRateRepository, CountryRepository countryRepository, AuditService auditService,
			Clock clock, @Value("${app.base-currency}") String baseCurrency, JdbcTemplate jdbcTemplate) {
		this.fxRateRepository = fxRateRepository;
		this.countryRepository = countryRepository;
		this.auditService = auditService;
		this.clock = clock;
		this.baseCurrency = baseCurrency;
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<FxRateResponse> list() {
		return fxRateRepository.findAll().stream()
				.sorted(Comparator.comparing(FxRate::getRateMonth).reversed()
						.thenComparing(FxRate::getBaseCurrency))
				.map(this::toResponse)
				.toList();
	}

	/**
	 * FR-6.4/P8.3 Verify: every (currency, month) with no pinned rate yet, trailing AND leading —
	 * {@code EffectiveDating.findRate} looks up a real row for EVERY currency including the base
	 * currency itself (a USD-paid employee's record still pins a USD→USD rate, CLAUDE.md §6.4's
	 * "every comp record" is literal), and a proposal is routinely dated a month or two ahead, so
	 * both the base currency and the forward months matter here, not only "foreign currency,
	 * trailing history."
	 */
	public List<MissingFxRateMonth> missingMonths() {
		Set<String> currencies = countryRepository.findAll().stream()
				.map(Country::getDefaultCurrency)
				.collect(Collectors.toCollection(TreeSet::new));
		currencies.add(baseCurrency);

		Set<String> existing = pinnedRateKeys();

		YearMonth current = YearMonth.now(clock);
		List<MissingFxRateMonth> missing = new ArrayList<>();
		for (String currency : currencies) {
			for (int i = -LOOKAHEAD_MONTHS; i < TRAILING_MONTHS; i++) {
				LocalDate monthStart = current.minusMonths(i).atDay(1);
				if (!existing.contains(currency + "|" + monthStart)) {
					missing.add(new MissingFxRateMonth(currency, baseCurrency, monthStart));
				}
			}
		}
		missing.sort(Comparator.comparing(MissingFxRateMonth::rateMonth).reversed()
				.thenComparing(MissingFxRateMonth::baseCurrency));
		return missing;
	}

	/**
	 * P10.2: the coverage matrix — currency × month over the currencies actually in use by
	 * {@code employee_current_comp}, same window as {@link #missingMonths()}. Where the chip list
	 * answers "what could ever need a rate", this answers "which months can today's payroll
	 * population not be written for". A currency nobody is paid in cannot produce a gap that
	 * matters, so it does not get a row.
	 */
	public FxCoverageResponse coverage() {
		YearMonth current = YearMonth.now(clock);
		List<LocalDate> months = new ArrayList<>();
		for (int back = TRAILING_MONTHS - 1; back >= -LOOKAHEAD_MONTHS; back--) {
			months.add(current.minusMonths(back).atDay(1));
		}

		Set<String> existing = pinnedRateKeys();

		List<FxCoverageRow> rows = jdbcTemplate.query(
				"SELECT currency, count(*) AS employee_count "
						+ "FROM salary_schema.employee_current_comp "
						+ "GROUP BY currency ORDER BY currency",
				(rs, rowNum) -> {
					String currency = rs.getString("currency").trim();
					List<FxCoverageCell> cells = months.stream()
							.map(month -> new FxCoverageCell(month, existing.contains(currency + "|" + month)))
							.toList();
					return new FxCoverageRow(currency, rs.getLong("employee_count"), cells);
				});
		return new FxCoverageResponse(months, baseCurrency, rows);
	}

	/** Keys of every pinned rate that converts into the base currency, as {@code ccy|monthStart}. */
	private Set<String> pinnedRateKeys() {
		return fxRateRepository.findAll().stream()
				.filter(rate -> rate.getQuoteCurrency().equals(baseCurrency))
				.map(rate -> rate.getBaseCurrency() + "|" + rate.getRateMonth())
				.collect(Collectors.toSet());
	}

	@Transactional
	public FxRateResponse add(CreateFxRateRequest request, UUID createdBy) {
		LocalDate monthStart = request.rateMonth().withDayOfMonth(1);
		if (fxRateRepository.findByBaseCurrencyAndQuoteCurrencyAndRateMonth(
				request.baseCurrency(), request.quoteCurrency(), monthStart).isPresent()) {
			throw new FxRateAlreadyExistsException(request.baseCurrency(), request.quoteCurrency(), monthStart);
		}

		FxRate saved = fxRateRepository.save(FxRate.builder()
				.rateMonth(monthStart)
				.baseCurrency(request.baseCurrency())
				.quoteCurrency(request.quoteCurrency())
				.rate(request.rate())
				.build());
		auditService.recordWrite(createdBy, "ADD_FX_RATE", "FX_RATE", saved.getId(), null, saved);
		return toResponse(saved);
	}

	private FxRateResponse toResponse(FxRate rate) {
		return new FxRateResponse(rate.getId(), rate.getRateMonth(), rate.getBaseCurrency(), rate.getQuoteCurrency(), rate.getRate());
	}

}
