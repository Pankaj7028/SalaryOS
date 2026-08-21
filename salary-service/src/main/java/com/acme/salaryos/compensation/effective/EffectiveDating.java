package com.acme.salaryos.compensation.effective;

import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.common.money.Money;
import com.acme.salaryos.compensation.domain.CompensationRecord;
import com.acme.salaryos.compensation.projection.EmployeeCurrentCompProjector;
import com.acme.salaryos.compensation.repository.CompensationRecordRepository;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.repository.EmployeeRepository;
import com.acme.salaryos.fx.FxRate;
import com.acme.salaryos.fx.FxRateRepository;
import com.acme.salaryos.reference.domain.Location;
import com.acme.salaryos.reference.repository.LocationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * The one piece of logic the whole product rests on (backend doc §3). Everything here is
 * insert-only (CLAUDE.md §6.3): {@link #apply} and {@link #correct} both close or supersede an
 * existing row and insert a new one — neither ever edits history in place.
 *
 * <p>Both methods also refresh {@code employee_current_comp} via {@link EmployeeCurrentCompProjector}
 * in the same transaction (Technical-Requirements.md §4.4) — deliberately not a database trigger,
 * so the projection update is visible right here, in the code path that causes it.
 */
@Service
public class EffectiveDating {

	/** 40 hours × 52 weeks — the standard full-time year used to annualise an hourly rate. */
	private static final BigDecimal STANDARD_ANNUAL_HOURS = BigDecimal.valueOf(2080);

	private final EmployeeRepository employeeRepository;
	private final LocationRepository locationRepository;
	private final SalaryBandRepository salaryBandRepository;
	private final FxRateRepository fxRateRepository;
	private final CompensationRecordRepository compensationRecordRepository;
	private final EmployeeCurrentCompProjector projector;
	private final String baseCurrency;

	/**
	 * No {@link java.time.Clock} here: every rule this class enforces compares one date against
	 * another ({@code effectiveFrom} against the open period's own start), never against "today".
	 * The package convention from backend doc §3 rule 6 — inject {@code Clock}, never call {@code
	 * LocalDate.now()} inline — applies once something in this package needs "now", which is
	 * {@code ApplyDueChangesJob} at P6.3, not this class.
	 */
	public EffectiveDating(
			EmployeeRepository employeeRepository,
			LocationRepository locationRepository,
			SalaryBandRepository salaryBandRepository,
			FxRateRepository fxRateRepository,
			CompensationRecordRepository compensationRecordRepository,
			EmployeeCurrentCompProjector projector,
			@Value("${app.base-currency}") String baseCurrency) {
		this.employeeRepository = employeeRepository;
		this.locationRepository = locationRepository;
		this.salaryBandRepository = salaryBandRepository;
		this.fxRateRepository = fxRateRepository;
		this.compensationRecordRepository = compensationRecordRepository;
		this.projector = projector;
		this.baseCurrency = baseCurrency;
	}

	/**
	 * Opens a new period. If the employee has no open period yet, this is their first-ever record
	 * (typically {@code changeReason = INITIAL}) and nothing is closed. If one exists, {@code
	 * effectiveFrom} must be strictly after it — otherwise this would either backdate into, or
	 * silently shorten, pay that already happened, which {@link BackdatedBeforeOpenPeriodException}
	 * rejects instead (backend doc §8's exact copy).
	 */
	@Transactional
	public CompensationRecord apply(ApplyCommand cmd) {
		Employee employee = employeeRepository.findById(cmd.employeeId()).orElseThrow(NoSuchElementException::new);
		Optional<CompensationRecord> openPeriod = compensationRecordRepository.findByEmployeeIdAndEffectiveToIsNull(cmd.employeeId());

		if (openPeriod.isPresent() && !cmd.effectiveFrom().isAfter(openPeriod.get().getEffectiveFrom())) {
			throw new BackdatedBeforeOpenPeriodException(openPeriod.get().getEffectiveFrom());
		}

		// Close (and FLUSH) the old period before inserting the new one. Hibernate's default flush
		// ordering runs every pending INSERT before any UPDATE regardless of call order, so without
		// the explicit flush here the new row's insert would hit the database while the old row's
		// range is still open-ended — comp_no_overlap would reject it as a false conflict.
		openPeriod.ifPresent(open -> {
			open.close(cmd.effectiveFrom().minusDays(1));
			compensationRecordRepository.saveAndFlush(open);
		});

		CompensationRecord record = buildRecord(
				employee, cmd.effectiveFrom(), null, cmd.amount(), cmd.currency(), cmd.payFrequency(),
				cmd.changeReason(), cmd.changeId(), cmd.createdBy());

		CompensationRecord saved = compensationRecordRepository.save(record);

		employee.clearBandMismatch();
		employeeRepository.save(employee);

		projector.refresh(employee.getId());

		return saved;
	}

	/**
	 * Fixes a mistake inside an existing period: {@code effectiveFrom} must fall strictly after the
	 * original period's own start (rule 1's day-boundary closing math has no earlier date to close
	 * to) and, if that period is already closed, before its end. The original row is closed at
	 * {@code effectiveFrom − 1 day} and marked {@link CompensationRecord#supersede superseded} —
	 * never deleted, never edited beyond that.
	 */
	@Transactional
	public CompensationRecord correct(CorrectCommand cmd) {
		if (cmd.note() == null || cmd.note().isBlank()) {
			throw new MissingCorrectionNoteException();
		}

		CompensationRecord original = compensationRecordRepository.findById(cmd.originalRecordId())
				.orElseThrow(NoSuchElementException::new);
		Employee employee = employeeRepository.findById(original.getEmployeeId()).orElseThrow(NoSuchElementException::new);

		if (!cmd.effectiveFrom().isAfter(original.getEffectiveFrom())) {
			throw new BackdatedBeforeOpenPeriodException(original.getEffectiveFrom());
		}
		if (original.getEffectiveTo() != null && !cmd.effectiveFrom().isBefore(original.getEffectiveTo())) {
			throw new CorrectionOutsideOriginalPeriodException();
		}

		// Captured before close() mutates it — the corrected row inherits the ORIGINAL period's own
		// end date (null if it was open), not the just-closed value that close() is about to set.
		LocalDate originalEffectiveTo = original.getEffectiveTo();

		// Same ordering requirement as apply(): close and FLUSH the original before inserting the
		// corrected row, or comp_no_overlap sees two open-ended ranges at insert time.
		original.close(cmd.effectiveFrom().minusDays(1));
		compensationRecordRepository.saveAndFlush(original);

		CompensationRecord corrected = buildRecord(
				employee, cmd.effectiveFrom(), originalEffectiveTo, cmd.amount(), cmd.currency(),
				cmd.payFrequency(), "CORRECTION", null, cmd.createdBy());

		CompensationRecord saved = compensationRecordRepository.save(corrected);

		original.supersede(saved.getId());
		compensationRecordRepository.save(original);

		projector.refresh(employee.getId());

		return saved;
	}

	private CompensationRecord buildRecord(
			Employee employee, LocalDate effectiveFrom, LocalDate effectiveTo, BigDecimal amount, String currency,
			String payFrequency, String changeReason, UUID changeId, UUID createdBy) {

		Location location = locationRepository.findById(employee.getLocationId()).orElseThrow(NoSuchElementException::new);
		SalaryBand band = salaryBandRepository
				.findEffective(employee.getJobLevelId(), location.getCountryCode(), effectiveFrom)
				.orElse(null);

		BigDecimal annualBaseAmount = annualise(amount, payFrequency, employee.getFte());
		FxRate rate = findRate(currency, baseCurrency, YearMonth.from(effectiveFrom));
		BigDecimal normalizedAmount = annualBaseAmount.multiply(rate.getRate()).setScale(2, RoundingMode.HALF_UP);

		return CompensationRecord.builder()
				.employeeId(employee.getId())
				.effectiveFrom(effectiveFrom)
				.effectiveTo(effectiveTo)
				.base(new Money(amount, currency))
				.payFrequency(payFrequency)
				.annualBaseAmount(annualBaseAmount)
				.normalizedAnnualBase(new Money(normalizedAmount, baseCurrency))
				.fxRateId(rate.getId())
				.bandId(band == null ? null : band.getId())
				.compaRatio(compaRatio(annualBaseAmount, band))
				.rangePenetration(rangePenetration(annualBaseAmount, band))
				.changeId(changeId)
				.changeReason(changeReason)
				.createdBy(createdBy)
				.build();
	}

	/**
	 * Annualises {@code amount}, grossed to its FTE = 1.0 equivalent so compa-ratio and band
	 * comparisons stay meaningful regardless of how much of a full-time role this is (a 0.5-FTE
	 * employee paid $60,000/year shows as $120,000 annualised). {@code HOURLY} is the one exception:
	 * an hourly rate is already a per-hour wage independent of hours actually worked, so multiplying
	 * by a full standard year ({@link #STANDARD_ANNUAL_HOURS}) already yields the FTE = 1.0 figure —
	 * dividing by FTE again would double-count it.
	 */
	private BigDecimal annualise(BigDecimal amount, String payFrequency, BigDecimal fte) {
		BigDecimal periodAnnual = switch (payFrequency) {
			case "ANNUAL" -> amount;
			case "MONTHLY" -> amount.multiply(BigDecimal.valueOf(12));
			case "HOURLY" -> {
				yield amount.multiply(STANDARD_ANNUAL_HOURS).setScale(2, RoundingMode.HALF_UP);
			}
			default -> throw new IllegalArgumentException("Unknown pay frequency: " + payFrequency);
		};
		if ("HOURLY".equals(payFrequency)) {
			return periodAnnual;
		}
		return periodAnnual.divide(fte, 2, RoundingMode.HALF_UP);
	}

	private FxRate findRate(String fromCurrency, String toCurrency, YearMonth month) {
		return fxRateRepository.findByBaseCurrencyAndQuoteCurrencyAndRateMonth(fromCurrency, toCurrency, month.atDay(1))
				.orElseThrow(() -> new MissingFxRateException(fromCurrency, toCurrency, month));
	}

	/** {@code null}, never {@code 1.0}, when there is no band — a default would hide an unbanded employee (backend doc §3, rule 3). */
	private BigDecimal compaRatio(BigDecimal annualBaseAmount, SalaryBand band) {
		if (band == null) {
			return null;
		}
		return annualBaseAmount.divide(band.getMidAmount(), 4, RoundingMode.HALF_UP);
	}

	private BigDecimal rangePenetration(BigDecimal annualBaseAmount, SalaryBand band) {
		if (band == null) {
			return null;
		}
		BigDecimal range = band.getMaxAmount().subtract(band.getMinAmount());
		if (range.signum() == 0) {
			return BigDecimal.ZERO;
		}
		return annualBaseAmount.subtract(band.getMinAmount())
				.divide(range, 6, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.setScale(4, RoundingMode.HALF_UP);
	}

	/**
	 * {@code compensation_records} has no {@code band_status} column — only the
	 * {@code employee_current_comp} projection does. {@code static} (no instance state needed) so
	 * {@link com.acme.salaryos.compensation.projection.EmployeeCurrentCompProjector} can call it
	 * without injecting this bean — {@code EffectiveDating} already depends on the projector to
	 * refresh the projection in the same transaction (Technical-Requirements.md §4.4), and a
	 * bean-to-bean dependency the other way would cycle. One rule, one place either way.
	 */
	public static String bandStatus(BigDecimal annualBaseAmount, SalaryBand band) {
		if (band == null) {
			return "NO_BAND";
		}
		if (annualBaseAmount.compareTo(band.getMinAmount()) < 0) {
			return "BELOW_MIN";
		}
		if (annualBaseAmount.compareTo(band.getMaxAmount()) > 0) {
			return "ABOVE_MAX";
		}
		return "IN_BAND";
	}

}
