package com.acme.salaryos.band.service;

import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.dto.BandImportResult;
import com.acme.salaryos.band.dto.BandImportRowResult;
import com.acme.salaryos.band.dto.BandResponse;
import com.acme.salaryos.band.dto.BandVersionImpactResponse;
import com.acme.salaryos.band.dto.CreateBandRequest;
import com.acme.salaryos.band.dto.UpdateBandRequest;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.common.money.Money;
import com.acme.salaryos.compensation.domain.EmployeeCurrentComp;
import com.acme.salaryos.compensation.effective.EffectiveDating;
import com.acme.salaryos.compensation.repository.EmployeeCurrentCompRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-4.5: a band is never mutated in place. {@link #create} is for a (job level × country)
 * combination that has never had a band; {@link #update} versions an existing in-force one — closes
 * it and opens a successor. Unlike {@code compensation_records}, {@code salary_bands} has no
 * database exclusion constraint backing this (V4 migration), so the service is the only thing
 * enforcing it — the checks here are the actual guarantee, not a backstop for one.
 */
@Service
public class BandService {

	private final SalaryBandRepository salaryBandRepository;
	private final EmployeeCurrentCompRepository employeeCurrentCompRepository;

	public BandService(SalaryBandRepository salaryBandRepository, EmployeeCurrentCompRepository employeeCurrentCompRepository) {
		this.salaryBandRepository = salaryBandRepository;
		this.employeeCurrentCompRepository = employeeCurrentCompRepository;
	}

	public List<BandResponse> list() {
		return salaryBandRepository.findAll().stream()
				.sorted(Comparator
						.comparing(SalaryBand::getJobLevelId)
						.thenComparing(SalaryBand::getCountryCode)
						.thenComparing(SalaryBand::getEffectiveFrom))
				.map(this::toResponse)
				.toList();
	}

	/**
	 * ui doc §8.6: "Creating a new version shows how many employees change status as a result
	 * before saving." Evaluates the cohort currently projected against {@code bandId} (necessarily
	 * its in-force version — {@code employee_current_comp.band_id} only ever points at that one)
	 * against the proposed new boundaries, using the exact same {@link EffectiveDating#bandStatus}
	 * rule the ledger itself uses — so this number is provably what a post-save re-derivation would
	 * also find, never a separate approximation.
	 */
	public BandVersionImpactResponse previewVersionImpact(UUID bandId, BigDecimal minAmount, BigDecimal midAmount, BigDecimal maxAmount) {
		SalaryBand proposed = SalaryBand.builder()
				.minAmount(minAmount).midAmount(midAmount).maxAmount(maxAmount)
				.build();

		List<EmployeeCurrentComp> cohort = employeeCurrentCompRepository.findByBandId(bandId);
		long changing = cohort.stream()
				.filter(comp -> !EffectiveDating.bandStatus(comp.getAnnualBaseAmount(), proposed).equals(comp.getBandStatus()))
				.count();

		return new BandVersionImpactResponse(cohort.size(), (int) changing);
	}

	@Transactional
	public BandResponse create(CreateBandRequest request, UUID createdBy) {
		requireOrdered(request.minAmount(), request.midAmount(), request.maxAmount());

		Optional<SalaryBand> existing = salaryBandRepository
				.findByJobLevelIdAndCountryCodeAndEffectiveToIsNull(request.jobLevelId(), request.countryCode());
		if (existing.isPresent()) {
			throw new BandAlreadyExistsException(existing.get().getEffectiveFrom());
		}

		SalaryBand band = SalaryBand.builder()
				.jobLevelId(request.jobLevelId())
				.countryCode(request.countryCode())
				.currency(request.currency())
				.minAmount(request.minAmount())
				.midAmount(request.midAmount())
				.maxAmount(request.maxAmount())
				.effectiveFrom(request.effectiveFrom())
				.createdBy(createdBy)
				.note(request.note())
				.build();
		return toResponse(salaryBandRepository.save(band));
	}

	@Transactional
	public BandResponse update(UUID id, UpdateBandRequest request, UUID createdBy) {
		requireOrdered(request.minAmount(), request.midAmount(), request.maxAmount());

		SalaryBand current = salaryBandRepository.findById(id).orElseThrow(NoSuchElementException::new);
		if (current.getEffectiveTo() != null) {
			throw new BandNotOpenException();
		}
		if (!request.effectiveFrom().isAfter(current.getEffectiveFrom())) {
			throw new BandBackdatedException(current.getEffectiveFrom());
		}

		SalaryBand successor = versionBand(current, request.currency(), request.minAmount(), request.midAmount(),
				request.maxAmount(), request.effectiveFrom(), request.note(), createdBy);
		return toResponse(successor);
	}

	/**
	 * FR-4.6: {@code dryRun} produces the same diff without writing anything. Rows are 1-indexed
	 * from the first data row (row 1 is the header: {@code jobLevelId,countryCode,currency,
	 * minAmount,midAmount,maxAmount,effectiveFrom,note} — {@code note} may be blank).
	 */
	@Transactional
	public BandImportResult importCsv(MultipartFile file, boolean dryRun, UUID createdBy) {
		List<BandImportRowResult> rows = new ArrayList<>();
		int created = 0;
		int versioned = 0;
		int errors = 0;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
			String header = reader.readLine();
			int rowNumber = 1;
			String line;
			while ((line = reader.readLine()) != null) {
				rowNumber++;
				if (line.isBlank()) {
					continue;
				}
				BandImportRowResult result = importRow(rowNumber, line, dryRun, createdBy);
				rows.add(result);
				switch (result.action()) {
					case "CREATE" -> created++;
					case "VERSION" -> versioned++;
					default -> errors++;
				}
			}
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Could not read the uploaded CSV file.", e);
		}

		int rowsApplied = dryRun ? 0 : created + versioned;
		return new BandImportResult(dryRun, rows.size(), created, versioned, errors, rowsApplied, rows);
	}

	private BandImportRowResult importRow(int rowNumber, String line, boolean dryRun, UUID createdBy) {
		String[] fields = line.split(",", -1);
		if (fields.length < 7) {
			return errorRow(rowNumber, line, "Expected 7 or 8 columns, found " + fields.length + ".");
		}

		UUID jobLevelId;
		String countryCode;
		String currency;
		BigDecimal minAmount;
		BigDecimal midAmount;
		BigDecimal maxAmount;
		LocalDate effectiveFrom;
		try {
			jobLevelId = UUID.fromString(fields[0].trim());
			countryCode = fields[1].trim();
			currency = fields[2].trim();
			minAmount = new BigDecimal(fields[3].trim());
			midAmount = new BigDecimal(fields[4].trim());
			maxAmount = new BigDecimal(fields[5].trim());
			effectiveFrom = LocalDate.parse(fields[6].trim());
		}
		catch (RuntimeException malformed) {
			return errorRow(rowNumber, line, "Could not parse row: " + malformed.getMessage());
		}
		String note = fields.length > 7 ? fields[7].trim() : null;

		if (minAmount.compareTo(midAmount) > 0 || midAmount.compareTo(maxAmount) > 0) {
			return new BandImportRowResult(rowNumber, "ERROR", jobLevelId, countryCode, currency,
					minAmount, midAmount, maxAmount, effectiveFrom, "min must be ≤ mid ≤ max.");
		}

		Optional<SalaryBand> open = salaryBandRepository.findByJobLevelIdAndCountryCodeAndEffectiveToIsNull(jobLevelId, countryCode);
		if (open.isEmpty()) {
			if (!dryRun) {
				salaryBandRepository.save(SalaryBand.builder()
						.jobLevelId(jobLevelId).countryCode(countryCode).currency(currency)
						.minAmount(minAmount).midAmount(midAmount).maxAmount(maxAmount)
						.effectiveFrom(effectiveFrom).createdBy(createdBy).note(note)
						.build());
			}
			return new BandImportRowResult(rowNumber, "CREATE", jobLevelId, countryCode, currency,
					minAmount, midAmount, maxAmount, effectiveFrom, null);
		}

		SalaryBand current = open.get();
		if (!effectiveFrom.isAfter(current.getEffectiveFrom())) {
			return new BandImportRowResult(rowNumber, "ERROR", jobLevelId, countryCode, currency,
					minAmount, midAmount, maxAmount, effectiveFrom,
					"Current version already starts " + current.getEffectiveFrom() + "; this row's date doesn't come after it.");
		}
		if (!dryRun) {
			versionBand(current, currency, minAmount, midAmount, maxAmount, effectiveFrom, note, createdBy);
		}
		return new BandImportRowResult(rowNumber, "VERSION", jobLevelId, countryCode, currency,
				minAmount, midAmount, maxAmount, effectiveFrom, null);
	}

	private void requireOrdered(BigDecimal minAmount, BigDecimal midAmount, BigDecimal maxAmount) {
		if (minAmount.compareTo(midAmount) > 0 || midAmount.compareTo(maxAmount) > 0) {
			throw new BandOrderingException();
		}
	}

	private BandImportRowResult errorRow(int rowNumber, String rawLine, String message) {
		return new BandImportRowResult(rowNumber, "ERROR", null, null, null, null, null, null, null, message);
	}

	private SalaryBand versionBand(
			SalaryBand current, String currency, BigDecimal minAmount, BigDecimal midAmount, BigDecimal maxAmount,
			LocalDate effectiveFrom, String note, UUID createdBy) {
		// close(effectiveFrom), not minusDays(1): findEffective's "asAt" check is
		// effectiveTo > asAt (exclusive), the same convention as compensation_records' `[)` daterange
		// (EffectiveDating.apply()'s comment has the full reasoning + empirical verification).
		// Subtracting a day here would leave the day before a new version starts covered by neither
		// version.
		current.close(effectiveFrom);
		salaryBandRepository.saveAndFlush(current);

		SalaryBand successor = SalaryBand.builder()
				.jobLevelId(current.getJobLevelId())
				.countryCode(current.getCountryCode())
				.currency(currency)
				.minAmount(minAmount)
				.midAmount(midAmount)
				.maxAmount(maxAmount)
				.effectiveFrom(effectiveFrom)
				.createdBy(createdBy)
				.note(note)
				.build();
		return salaryBandRepository.save(successor);
	}

	private BandResponse toResponse(SalaryBand band) {
		String currency = band.getCurrency();
		long headcount = band.getEffectiveTo() == null ? employeeCurrentCompRepository.countByBandId(band.getId()) : 0;
		return new BandResponse(
				band.getId(), band.getJobLevelId(), band.getCountryCode(),
				new Money(band.getMinAmount(), currency), new Money(band.getMidAmount(), currency), new Money(band.getMaxAmount(), currency),
				band.getEffectiveFrom(), band.getEffectiveTo(), band.getNote(), headcount);
	}

}
