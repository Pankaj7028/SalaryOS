package com.acme.salaryos.market.service;

import com.acme.salaryos.market.domain.MarketDataPoint;
import com.acme.salaryos.market.dto.MarketImportResult;
import com.acme.salaryos.market.dto.MarketImportRowResult;
import com.acme.salaryos.market.repository.MarketDataPointRepository;
import com.acme.salaryos.reference.repository.CountryRepository;
import com.acme.salaryos.reference.repository.JobLevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * P11.5. Import of a market survey ACME already buys.
 *
 * <p>Same CSV discipline the bands importer established at P5.3, deliberately: header row, plain
 * line split (no field here can contain a comma, so no CSV library), one bad row becomes an
 * {@code ERROR} entry and never blocks the rest, and a dry run diffs without applying. A second
 * import shape that behaved differently would be a trap for whoever uses both.
 *
 * <p>Columns: {@code source,jobLevelId,countryCode,currency,p25,p50,p75,effectiveMonth}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

	private static final int EXPECTED_COLUMNS = 8;

	private final MarketDataPointRepository marketDataPointRepository;
	private final JobLevelRepository jobLevelRepository;
	private final CountryRepository countryRepository;

	@Transactional
	public MarketImportResult importCsv(MultipartFile file, boolean dryRun, UUID importedBy) {
		List<MarketImportRowResult> rows = new ArrayList<>();
		int created = 0;
		int updated = 0;
		int errors = 0;

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
			reader.readLine(); // header
			int rowNumber = 1;
			String line;
			while ((line = reader.readLine()) != null) {
				rowNumber++;
				if (line.isBlank()) {
					continue;
				}
				MarketImportRowResult result = importRow(rowNumber, line, dryRun, importedBy);
				rows.add(result);
				switch (result.action()) {
					case "CREATE" -> created++;
					case "UPDATE" -> updated++;
					default -> errors++;
				}
			}
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Could not read the uploaded CSV file.", e);
		}

		int rowsApplied = dryRun ? 0 : created + updated;
		log.info("Market import: {} rows, {} created, {} updated, {} errors (dryRun={})",
				rows.size(), created, updated, errors, dryRun);
		return new MarketImportResult(dryRun, rows.size(), created, updated, errors, rowsApplied, rows);
	}

	/**
	 * One row's worth of parsing and validation. Every failure is caught and turned into an
	 * {@code ERROR} row carrying its own message — the caller sees which row failed and why, and
	 * the surviving rows still import.
	 */
	private MarketImportRowResult importRow(int rowNumber, String line, boolean dryRun, UUID importedBy) {
		String[] fields = line.split(",", -1);
		if (fields.length != EXPECTED_COLUMNS) {
			return error(rowNumber, "Expected " + EXPECTED_COLUMNS + " columns, found " + fields.length + ".");
		}

		String source = fields[0].trim();
		String countryCode = fields[2].trim().toUpperCase();
		try {
			if (source.isEmpty()) {
				return error(rowNumber, "Source is required.");
			}

			UUID jobLevelId = UUID.fromString(fields[1].trim());
			String currency = fields[3].trim().toUpperCase();
			BigDecimal p25 = new BigDecimal(fields[4].trim());
			BigDecimal p50 = new BigDecimal(fields[5].trim());
			BigDecimal p75 = new BigDecimal(fields[6].trim());
			LocalDate effectiveMonth = LocalDate.parse(fields[7].trim()).withDayOfMonth(1);

			if (p25.signum() <= 0 || p50.signum() <= 0 || p75.signum() <= 0) {
				return error(rowNumber, "Percentiles must be greater than zero.");
			}
			// Mirrors the market_percentiles_ordered constraint. Checked here too so the row gets a
			// readable message and the rest of the file survives, rather than the whole transaction
			// dying on a constraint violation — the same reason BandService validates before insert.
			if (p25.compareTo(p50) > 0 || p50.compareTo(p75) > 0) {
				return error(rowNumber, "Percentiles must be ordered p25 <= p50 <= p75.");
			}

			// Foreign keys are checked by lookup BEFORE any insert, not left to the constraint. A
			// constraint violation inside this shared transaction would abort it in Postgres, so
			// every later row would fail too — which would quietly break the importer's core
			// promise that one bad row never blocks the rest. Same approach BandService takes.
			if (!jobLevelRepository.existsById(jobLevelId)) {
				return error(rowNumber, "No job level with id " + jobLevelId + ".");
			}
			if (!countryRepository.existsById(countryCode)) {
				return error(rowNumber, "No country with code " + countryCode + ".");
			}

			var existing = marketDataPointRepository
					.findBySourceAndJobLevelIdAndCountryCodeAndEffectiveMonth(
							source, jobLevelId, countryCode, effectiveMonth);

			if (existing.isPresent()) {
				if (!dryRun) {
					MarketDataPoint point = existing.get();
					point.setP25Amount(p25);
					point.setP50Amount(p50);
					point.setP75Amount(p75);
					point.setImportedBy(importedBy);
					marketDataPointRepository.save(point);
				}
				return new MarketImportRowResult(rowNumber, "UPDATE", source, countryCode, null);
			}

			if (!dryRun) {
				marketDataPointRepository.save(MarketDataPoint.builder()
						.source(source)
						.jobLevelId(jobLevelId)
						.countryCode(countryCode)
						.currency(currency)
						.p25Amount(p25)
						.p50Amount(p50)
						.p75Amount(p75)
						.effectiveMonth(effectiveMonth)
						.importedBy(importedBy)
						.build());
			}
			return new MarketImportRowResult(rowNumber, "CREATE", source, countryCode, null);
		}
		catch (IllegalArgumentException | DateTimeParseException e) {
			return error(rowNumber, "Could not read this row: " + e.getMessage());
		}
	}

	private MarketImportRowResult error(int rowNumber, String message) {
		return new MarketImportRowResult(rowNumber, "ERROR", null, null, message);
	}

}
