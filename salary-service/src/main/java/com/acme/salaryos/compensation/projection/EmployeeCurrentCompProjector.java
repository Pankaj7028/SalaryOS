package com.acme.salaryos.compensation.projection;

import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.compensation.domain.CompensationRecord;
import com.acme.salaryos.compensation.domain.EmployeeCurrentComp;
import com.acme.salaryos.compensation.effective.EffectiveDating;
import com.acme.salaryos.compensation.repository.CompensationRecordRepository;
import com.acme.salaryos.compensation.repository.EmployeeCurrentCompRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps {@code employee_current_comp} — a cache, not a second source of truth
 * (Technical-Requirements.md §4.4) — in step with the ledger. A table, not a view: the employee
 * list filters and sorts on compa-ratio/band status for 10,000 rows, and joining bands and FX per
 * row on every keystroke of a search box doesn't scale.
 *
 * <p>Maintained transactionally by {@link EffectiveDating}, in the same transaction as the ledger
 * write that changed it — deliberately not a database trigger, so the update is visible in the
 * code path that causes it. {@link #rebuildAll()} is the repair path for
 * {@code POST /api/admin/rebuild-projection}: the ledger is the truth, and any disagreement
 * between it and this table is a bug in the writer.
 */
@Service
public class EmployeeCurrentCompProjector {

	private final CompensationRecordRepository compensationRecordRepository;
	private final EmployeeCurrentCompRepository employeeCurrentCompRepository;
	private final SalaryBandRepository salaryBandRepository;

	public EmployeeCurrentCompProjector(
			CompensationRecordRepository compensationRecordRepository,
			EmployeeCurrentCompRepository employeeCurrentCompRepository,
			SalaryBandRepository salaryBandRepository) {
		this.compensationRecordRepository = compensationRecordRepository;
		this.employeeCurrentCompRepository = employeeCurrentCompRepository;
		this.salaryBandRepository = salaryBandRepository;
	}

	/**
	 * Re-derives one employee's projection row from whatever the ledger currently says is their
	 * open period. No open period (e.g. termination closed the last one with nothing to replace it)
	 * removes the row entirely — there is no "current pay" to show.
	 */
	@Transactional
	public void refresh(UUID employeeId) {
		Optional<CompensationRecord> open = compensationRecordRepository.findByEmployeeIdAndEffectiveToIsNull(employeeId);
		if (open.isEmpty()) {
			employeeCurrentCompRepository.deleteById(employeeId);
			return;
		}
		employeeCurrentCompRepository.save(toProjection(open.get()));
	}

	/** Full repair: wipes the table and re-derives every row from the ledger's open periods. */
	@Transactional
	public void rebuildAll() {
		employeeCurrentCompRepository.deleteAllInBatch();
		List<CompensationRecord> openPeriods = compensationRecordRepository.findByEffectiveToIsNull();
		List<EmployeeCurrentComp> projections = openPeriods.stream().map(this::toProjection).toList();
		employeeCurrentCompRepository.saveAll(projections);
	}

	private EmployeeCurrentComp toProjection(CompensationRecord record) {
		SalaryBand band = record.getBandId() == null ? null : salaryBandRepository.findById(record.getBandId()).orElse(null);
		return EmployeeCurrentComp.builder()
				.employeeId(record.getEmployeeId())
				.compensationRecordId(record.getId())
				.base(record.getBase())
				.annualBaseAmount(record.getAnnualBaseAmount())
				.normalizedAnnualBase(record.getNormalizedAnnualBase().amount())
				.bandId(record.getBandId())
				.compaRatio(record.getCompaRatio())
				.rangePenetration(record.getRangePenetration())
				.bandStatus(EffectiveDating.bandStatus(record.getAnnualBaseAmount(), band))
				.build();
	}

}
