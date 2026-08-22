package com.acme.salaryos.fx.service;

import com.acme.salaryos.audit.AuditService;
import com.acme.salaryos.fx.FxRate;
import com.acme.salaryos.fx.FxRateRepository;
import com.acme.salaryos.fx.dto.CreateFxRateRequest;
import com.acme.salaryos.fx.dto.FxRateResponse;
import com.acme.salaryos.fx.dto.MissingFxRateMonth;
import com.acme.salaryos.reference.domain.Country;
import com.acme.salaryos.reference.repository.CountryRepository;
import org.springframework.beans.factory.annotation.Value;
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

	private final FxRateRepository fxRateRepository;
	private final CountryRepository countryRepository;
	private final AuditService auditService;
	private final Clock clock;
	private final String baseCurrency;

	public FxRateService(
			FxRateRepository fxRateRepository, CountryRepository countryRepository, AuditService auditService,
			Clock clock, @Value("${app.base-currency}") String baseCurrency) {
		this.fxRateRepository = fxRateRepository;
		this.countryRepository = countryRepository;
		this.auditService = auditService;
		this.clock = clock;
		this.baseCurrency = baseCurrency;
	}

	public List<FxRateResponse> list() {
		return fxRateRepository.findAll().stream()
				.sorted(Comparator.comparing(FxRate::getRateMonth).reversed()
						.thenComparing(FxRate::getBaseCurrency))
				.map(this::toResponse)
				.toList();
	}

	/** FR-6.4/P8.3 Verify: every (currency, month) in the trailing window with no pinned rate yet. */
	public List<MissingFxRateMonth> missingMonths() {
		Set<String> currencies = countryRepository.findAll().stream()
				.map(Country::getDefaultCurrency)
				.filter(currency -> !currency.equals(baseCurrency))
				.collect(Collectors.toCollection(TreeSet::new));

		Set<String> existing = fxRateRepository.findAll().stream()
				.filter(rate -> rate.getQuoteCurrency().equals(baseCurrency))
				.map(rate -> rate.getBaseCurrency() + "|" + rate.getRateMonth())
				.collect(Collectors.toSet());

		YearMonth current = YearMonth.now(clock);
		List<MissingFxRateMonth> missing = new ArrayList<>();
		for (String currency : currencies) {
			for (int i = 0; i < TRAILING_MONTHS; i++) {
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
