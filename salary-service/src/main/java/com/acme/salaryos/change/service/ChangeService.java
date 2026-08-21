package com.acme.salaryos.change.service;

import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.change.domain.CompensationChange;
import com.acme.salaryos.change.dto.ChangeBulkUploadResult;
import com.acme.salaryos.change.dto.ChangeBulkUploadRowResult;
import com.acme.salaryos.change.dto.ChangeResponse;
import com.acme.salaryos.change.dto.ProposeChangeRequest;
import com.acme.salaryos.change.dto.UpdateDraftRequest;
import com.acme.salaryos.change.dto.ChangeImpactPreviewResponse;
import com.acme.salaryos.change.repository.CompensationChangeRepository;
import com.acme.salaryos.common.money.Money;
import com.acme.salaryos.compensation.domain.CompensationRecord;
import com.acme.salaryos.compensation.domain.EmployeeCurrentComp;
import com.acme.salaryos.compensation.effective.ApplyCommand;
import com.acme.salaryos.compensation.effective.EffectiveDating;
import com.acme.salaryos.compensation.repository.EmployeeCurrentCompRepository;
import com.acme.salaryos.employee.dto.BandBoundaries;
import com.acme.salaryos.employee.dto.PeerImpactPreview;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.repository.EmployeeRepository;
import com.acme.salaryos.employee.service.EmployeeService;
import com.acme.salaryos.reference.domain.Location;
import com.acme.salaryos.reference.repository.LocationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * FR-5: the proposal/approval lifecycle (CLAUDE.md §8). {@code DRAFT} → {@code PENDING} →
 * {@code APPROVED} → {@code APPLIED}, or {@code PENDING} → {@code REJECTED}. Only {@code APPLIED}
 * ever touches the ledger — that wiring is {@code ApplyDueChangesJob}'s job (P6.2), not this
 * class's.
 */
@Service
public class ChangeService {

	private static final List<String> OPEN_STATUSES = List.of("DRAFT", "PENDING", "APPROVED");

	private final CompensationChangeRepository changeRepository;
	private final EmployeeRepository employeeRepository;
	private final EmployeeCurrentCompRepository employeeCurrentCompRepository;
	private final LocationRepository locationRepository;
	private final SalaryBandRepository salaryBandRepository;
	private final EffectiveDating effectiveDating;
	private final EmployeeService employeeService;

	public ChangeService(
			CompensationChangeRepository changeRepository,
			EmployeeRepository employeeRepository,
			EmployeeCurrentCompRepository employeeCurrentCompRepository,
			LocationRepository locationRepository,
			SalaryBandRepository salaryBandRepository,
			EffectiveDating effectiveDating,
			EmployeeService employeeService) {
		this.changeRepository = changeRepository;
		this.employeeRepository = employeeRepository;
		this.employeeCurrentCompRepository = employeeCurrentCompRepository;
		this.locationRepository = locationRepository;
		this.salaryBandRepository = salaryBandRepository;
		this.effectiveDating = effectiveDating;
		this.employeeService = employeeService;
	}

	public List<ChangeResponse> list(UUID employeeId, String status, LocalDate fromDate, LocalDate toDate) {
		Specification<CompensationChange> spec = Specification.<CompensationChange>unrestricted()
				.and(employeeId == null ? Specification.unrestricted() : (root, q, cb) -> cb.equal(root.get("employeeId"), employeeId))
				.and(status == null ? Specification.unrestricted() : (root, q, cb) -> cb.equal(root.get("status"), status))
				.and(fromDate == null ? Specification.unrestricted() : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("effectiveDate"), fromDate))
				.and(toDate == null ? Specification.unrestricted() : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("effectiveDate"), toDate));

		return changeRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "proposedAt")).stream()
				.map(this::toResponse)
				.toList();
	}

	/**
	 * ui doc §8.4: the propose-change dialog's live impact panel. Computes what {@code propose()}
	 * would produce if this exact request were submitted — delta, resulting compa-ratio, band
	 * status, peer percentile before/after — without creating a DRAFT. Requires an existing current
	 * comp record, same as {@code propose()} itself; a hire with no comp yet has nothing to preview
	 * against.
	 */
	public ChangeImpactPreviewResponse previewImpact(UUID employeeId, LocalDate effectiveDate, BigDecimal newBaseAmount, String currency) {
		EmployeeCurrentComp current = employeeCurrentCompRepository.findById(employeeId)
				.orElseThrow(NoCurrentCompensationException::new);
		CompensationRecord proposed = effectiveDating.preview(employeeId, effectiveDate, newBaseAmount, currency);

		BigDecimal deltaAnnual = proposed.getAnnualBaseAmount().subtract(current.getAnnualBaseAmount());
		BigDecimal deltaPercent = current.getAnnualBaseAmount().signum() == 0
				? BigDecimal.ZERO
				: deltaAnnual.divide(current.getAnnualBaseAmount(), 6, RoundingMode.HALF_UP);

		SalaryBand band = proposed.getBandId() == null ? null : salaryBandRepository.findById(proposed.getBandId()).orElse(null);
		BandBoundaries boundaries = band == null ? null : new BandBoundaries(
				new Money(band.getMinAmount(), band.getCurrency()),
				new Money(band.getMidAmount(), band.getCurrency()),
				new Money(band.getMaxAmount(), band.getCurrency()));
		String proposedBandStatus = EffectiveDating.bandStatus(proposed.getAnnualBaseAmount(), band);
		boolean noteRequired = band != null && !"IN_BAND".equals(proposedBandStatus);

		PeerImpactPreview peers = employeeService.peersImpact(employeeId, proposed.getNormalizedAnnualBase().amount());

		return new ChangeImpactPreviewResponse(
				current.getBase(), new Money(newBaseAmount, currency), new Money(deltaAnnual, currency), deltaPercent,
				current.getCompaRatio(), proposed.getCompaRatio(), current.getRangePenetration(), proposed.getRangePenetration(),
				current.getBandStatus(), proposedBandStatus, boundaries, noteRequired,
				peers.cohortSize(), peers.suppressed(), peers.percentileBefore(), peers.percentileAfter());
	}

	@Transactional
	public ChangeResponse propose(ProposeChangeRequest request, UUID proposedBy) {
		Employee employee = employeeRepository.findById(request.employeeId()).orElseThrow(NoSuchElementException::new);
		requireNoOpenChange(request.employeeId());

		EmployeeCurrentComp comp = employeeCurrentCompRepository.findById(request.employeeId())
				.orElseThrow(NoCurrentCompensationException::new);
		if (!comp.getBase().currency().equals(request.currency())) {
			throw new ChangeCurrencyMismatchException();
		}

		requireNoteIfNeeded(employee, request.effectiveDate(), request.newBaseAmount(), request.changeReason(), request.note());

		CompensationChange change = CompensationChange.builder()
				.employeeId(request.employeeId())
				.status("DRAFT")
				.effectiveDate(request.effectiveDate())
				.currentBaseAmount(comp.getAnnualBaseAmount())
				.newBaseAmount(request.newBaseAmount())
				.currency(request.currency())
				.changeReason(request.changeReason())
				.performanceRating(request.performanceRating())
				.note(request.note())
				.proposedBy(proposedBy)
				.build();
		return toResponse(changeRepository.save(change));
	}

	@Transactional
	public ChangeResponse updateDraft(UUID id, UpdateDraftRequest request) {
		CompensationChange change = changeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		requireDraft(change);
		Employee employee = employeeRepository.findById(change.getEmployeeId()).orElseThrow(NoSuchElementException::new);
		requireNoteIfNeeded(employee, request.effectiveDate(), request.newBaseAmount(), request.changeReason(), request.note());

		change.updateDraft(request.effectiveDate(), request.newBaseAmount(), request.currency(),
				request.changeReason(), request.performanceRating(), request.note());
		return toResponse(changeRepository.save(change));
	}

	@Transactional
	public ChangeResponse submit(UUID id) {
		CompensationChange change = changeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		requireDraft(change);
		change.submit();
		return toResponse(changeRepository.save(change));
	}

	@Transactional
	public void discardDraft(UUID id) {
		CompensationChange change = changeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		requireDraft(change);
		changeRepository.delete(change);
	}

	/** FR-5.5: the proposer can never approve their own proposal. */
	@Transactional
	public ChangeResponse approve(UUID id, UUID decidedBy, String decisionNote) {
		CompensationChange change = changeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		requirePending(change);
		if (change.getProposedBy().equals(decidedBy)) {
			throw new SelfApprovalException();
		}
		change.approve(decidedBy, decisionNote);
		return toResponse(changeRepository.save(change));
	}

	@Transactional
	public ChangeResponse reject(UUID id, UUID decidedBy, String decisionNote) {
		CompensationChange change = changeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		requirePending(change);
		change.reject(decidedBy, decisionNote);
		return toResponse(changeRepository.save(change));
	}

	/**
	 * FR-5.7: writes the ledger row and marks the change {@code APPLIED} in one transaction
	 * (Technical-Requirements.md §4.4's "same transaction" discipline, applied here too) — a
	 * partial failure can never leave the change APPROVED with a ledger row already written, or
	 * APPLIED with none. Re-validates status/date itself rather than trusting the caller's
	 * candidate list is still accurate by the time this runs — {@code ApplyDueChangesJob} calls
	 * this once per due change, each in its own transaction, so one change's rejection here
	 * (already applied, or no longer approved) can't roll back the others.
	 */
	@Transactional
	public CompensationRecord applyDueChange(UUID id, LocalDate asOf) {
		CompensationChange change = changeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		if (!"APPROVED".equals(change.getStatus())) {
			throw new ChangeNotPendingException();
		}
		if (change.getEffectiveDate().isAfter(asOf)) {
			throw new ChangeNotDueException(change.getEffectiveDate());
		}

		CompensationRecord record = effectiveDating.apply(new ApplyCommand(
				change.getEmployeeId(), change.getEffectiveDate(), change.getNewBaseAmount(), change.getCurrency(),
				"ANNUAL", change.getChangeReason(), change.getId(), change.getDecidedBy()));

		change.apply(record.getId());
		changeRepository.save(change);
		return record;
	}

	/**
	 * FR-5.8: one row is {@code employeeNumber,newAmount,changeReason,note} (note optional) — no
	 * {@code currency} column, unlike the band CSV's, since a merit row uses the employee's own
	 * current pay currency by construction, never a value the uploader could get wrong. Every row
	 * is independent: a bad row becomes an {@code ERROR} entry and never blocks the rest, so partial
	 * success is the normal outcome, not a failure mode (backend doc §3, matching {@code
	 * BandService#importCsv}'s per-row isolation). No {@code dryRun} — unlike a band version, a DRAFT
	 * proposal is cheap to discard, so there is no separate preview step to design around.
	 */
	public ChangeBulkUploadResult bulkUpload(MultipartFile file, LocalDate effectiveDate, UUID proposedBy) {
		List<ChangeBulkUploadRowResult> rows = new ArrayList<>();
		int proposed = 0;
		int errors = 0;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
			reader.readLine(); // header
			int rowNumber = 1;
			String line;
			while ((line = reader.readLine()) != null) {
				rowNumber++;
				if (line.isBlank()) {
					continue;
				}
				ChangeBulkUploadRowResult result = bulkUploadRow(rowNumber, line, effectiveDate, proposedBy);
				rows.add(result);
				if ("PROPOSED".equals(result.action())) {
					proposed++;
				} else {
					errors++;
				}
			}
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Could not read the uploaded CSV file.", e);
		}

		return new ChangeBulkUploadResult(rows.size(), proposed, errors, rows);
	}

	private ChangeBulkUploadRowResult bulkUploadRow(int rowNumber, String line, LocalDate effectiveDate, UUID proposedBy) {
		String[] fields = line.split(",", -1);
		if (fields.length < 3) {
			return new ChangeBulkUploadRowResult(rowNumber, "ERROR", null, null, null, null,
					"Expected 3 or 4 columns (employeeNumber,newAmount,changeReason[,note]), found " + fields.length + ".");
		}

		String employeeNumber = fields[0].trim();
		String changeReason = fields[2].trim();
		BigDecimal newAmount;
		try {
			newAmount = new BigDecimal(fields[1].trim());
		}
		catch (NumberFormatException malformed) {
			return new ChangeBulkUploadRowResult(rowNumber, "ERROR", employeeNumber, null, changeReason, null,
					"Could not parse newAmount: " + malformed.getMessage());
		}
		String note = fields.length > 3 && !fields[3].isBlank() ? fields[3].trim() : null;

		try {
			Employee employee = employeeRepository.findByEmployeeNumber(employeeNumber)
					.orElseThrow(() -> new NoSuchElementException("No employee with number " + employeeNumber + "."));
			EmployeeCurrentComp comp = employeeCurrentCompRepository.findById(employee.getId())
					.orElseThrow(NoCurrentCompensationException::new);

			ChangeResponse change = propose(new ProposeChangeRequest(
					employee.getId(), effectiveDate, newAmount, comp.getBase().currency(), changeReason, null, note), proposedBy);

			return new ChangeBulkUploadRowResult(rowNumber, "PROPOSED", employeeNumber, newAmount, changeReason, change.id(), null);
		}
		catch (RuntimeException rejected) {
			return new ChangeBulkUploadRowResult(rowNumber, "ERROR", employeeNumber, newAmount, changeReason, null, rejected.getMessage());
		}
	}

	private void requireNoOpenChange(UUID employeeId) {
		changeRepository.findByEmployeeIdAndStatusIn(employeeId, OPEN_STATUSES).ifPresent(open -> {
			throw new OpenChangeAlreadyExistsException(open.getId());
		});
	}

	private void requireDraft(CompensationChange change) {
		if (!"DRAFT".equals(change.getStatus())) {
			throw new ChangeNotDraftException();
		}
	}

	private void requirePending(CompensationChange change) {
		if (!"PENDING".equals(change.getStatus())) {
			throw new ChangeNotPendingException();
		}
	}

	/** FR-5.2/FR-5.4: a note is mandatory for a correction, and for a proposal landing outside the band. */
	private void requireNoteIfNeeded(Employee employee, LocalDate effectiveDate, BigDecimal newBaseAmount, String changeReason, String note) {
		boolean hasNote = note != null && !note.isBlank();

		if ("CORRECTION".equals(changeReason) && !hasNote) {
			throw new ChangeNoteRequiredException("for a correction");
		}

		Location location = locationRepository.findById(employee.getLocationId()).orElseThrow(NoSuchElementException::new);
		SalaryBand band = salaryBandRepository
				.findEffective(employee.getJobLevelId(), location.getCountryCode(), effectiveDate)
				.orElse(null);
		if (band != null && !"IN_BAND".equals(EffectiveDating.bandStatus(newBaseAmount, band)) && !hasNote) {
			throw new ChangeNoteRequiredException("for a change landing outside the band");
		}
	}

	private ChangeResponse toResponse(CompensationChange change) {
		return new ChangeResponse(
				change.getId(), change.getEmployeeId(), change.getStatus(), change.getEffectiveDate(),
				new Money(change.getCurrentBaseAmount(), change.getCurrency()),
				new Money(change.getNewBaseAmount(), change.getCurrency()),
				change.getChangeReason(), change.getPerformanceRating(), change.getNote(),
				change.getProposedBy(), change.getProposedAt(), change.getDecidedBy(), change.getDecidedAt(),
				change.getDecisionNote(), change.getAppliedAt(), change.getAppliedRecordId());
	}

}
