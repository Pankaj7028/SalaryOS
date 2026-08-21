package com.acme.salaryos.change.service;

import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.change.domain.CompensationChange;
import com.acme.salaryos.change.dto.ChangeResponse;
import com.acme.salaryos.change.dto.ProposeChangeRequest;
import com.acme.salaryos.change.dto.UpdateDraftRequest;
import com.acme.salaryos.change.repository.CompensationChangeRepository;
import com.acme.salaryos.common.money.Money;
import com.acme.salaryos.compensation.domain.EmployeeCurrentComp;
import com.acme.salaryos.compensation.effective.EffectiveDating;
import com.acme.salaryos.compensation.repository.EmployeeCurrentCompRepository;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.repository.EmployeeRepository;
import com.acme.salaryos.reference.domain.Location;
import com.acme.salaryos.reference.repository.LocationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

	public ChangeService(
			CompensationChangeRepository changeRepository,
			EmployeeRepository employeeRepository,
			EmployeeCurrentCompRepository employeeCurrentCompRepository,
			LocationRepository locationRepository,
			SalaryBandRepository salaryBandRepository) {
		this.changeRepository = changeRepository;
		this.employeeRepository = employeeRepository;
		this.employeeCurrentCompRepository = employeeCurrentCompRepository;
		this.locationRepository = locationRepository;
		this.salaryBandRepository = salaryBandRepository;
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
