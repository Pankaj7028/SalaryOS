package com.acme.salaryos.employee.service;

import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.common.money.Money;
import com.acme.salaryos.common.paging.Cursor;
import com.acme.salaryos.common.paging.CursorCodec;
import com.acme.salaryos.common.paging.KeysetPage;
import com.acme.salaryos.compensation.domain.CompensationComponent;
import com.acme.salaryos.compensation.domain.EmployeeCurrentComp;
import com.acme.salaryos.compensation.projection.EmployeeCurrentCompProjector;
import com.acme.salaryos.compensation.repository.CompensationComponentRepository;
import com.acme.salaryos.compensation.repository.CompensationRecordRepository;
import com.acme.salaryos.compensation.repository.EmployeeCurrentCompRepository;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.dto.BandBoundaries;
import com.acme.salaryos.employee.dto.CompensationComponentResponse;
import com.acme.salaryos.employee.dto.EmployeeCreateRequest;
import com.acme.salaryos.employee.dto.EmployeeDetailResponse;
import com.acme.salaryos.employee.dto.EmployeeSummaryResponse;
import com.acme.salaryos.employee.dto.EmployeeUpdateRequest;
import com.acme.salaryos.employee.dto.PeerComparisonResponse;
import com.acme.salaryos.employee.repository.EmployeeRepository;
import com.acme.salaryos.employee.spec.EmployeeSpecifications;
import com.acme.salaryos.reference.domain.Location;
import com.acme.salaryos.reference.repository.LocationRepository;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/** FR-2.2/2.3: search, filters, sort, keyset pagination. FR-2.5/2.6: create, edit, terminate. */
@Service
public class EmployeeService {

	private static final Sort SORT = Sort.by("lastName", "id");
	/** FR-6.6/FR-6.4's shared threshold: a cohort under this size could identify individuals. */
	private static final int PEER_COHORT_SUPPRESSION_THRESHOLD = 5;

	private final EmployeeRepository employeeRepository;
	private final EmployeeCurrentCompRepository employeeCurrentCompRepository;
	private final CompensationRecordRepository compensationRecordRepository;
	private final CompensationComponentRepository compensationComponentRepository;
	private final SalaryBandRepository salaryBandRepository;
	private final LocationRepository locationRepository;
	private final EmployeeCurrentCompProjector projector;
	private final CursorCodec cursorCodec;

	public EmployeeService(
			EmployeeRepository employeeRepository,
			EmployeeCurrentCompRepository employeeCurrentCompRepository,
			CompensationRecordRepository compensationRecordRepository,
			CompensationComponentRepository compensationComponentRepository,
			SalaryBandRepository salaryBandRepository,
			LocationRepository locationRepository,
			EmployeeCurrentCompProjector projector,
			CursorCodec cursorCodec) {
		this.employeeRepository = employeeRepository;
		this.employeeCurrentCompRepository = employeeCurrentCompRepository;
		this.compensationRecordRepository = compensationRecordRepository;
		this.compensationComponentRepository = compensationComponentRepository;
		this.salaryBandRepository = salaryBandRepository;
		this.locationRepository = locationRepository;
		this.projector = projector;
		this.cursorCodec = cursorCodec;
	}

	public KeysetPage<EmployeeSummaryResponse> list(
			String query, UUID departmentId, UUID locationId, String countryCode, UUID jobLevelId,
			String status, String cursor, int limit) {

		// Specification.where/and reject a null argument outright (no longer the historical
		// null-means-"no restriction" behaviour) — start unrestricted and fold in only the
		// filters actually supplied.
		Specification<Employee> spec = Specification.<Employee>unrestricted()
				.and(nonNullOrUnrestricted(EmployeeSpecifications.search(query)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.departmentId(departmentId)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.locationId(locationId)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.countryCode(countryCode)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.jobLevelId(jobLevelId)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.status(status)));

		ScrollPosition position = toScrollPosition(cursor);

		Window<Employee> window = employeeRepository.findBy(spec,
				q -> q.sortBy(SORT).limit(limit).scroll(position));

		List<Employee> employees = window.getContent();
		Map<UUID, EmployeeCurrentComp> currentComp = employeeCurrentCompRepository
				.findAllById(employees.stream().map(Employee::getId).toList())
				.stream()
				.collect(Collectors.toMap(EmployeeCurrentComp::getEmployeeId, c -> c));
		Map<UUID, SalaryBand> bands = fetchBands(currentComp.values());

		List<EmployeeSummaryResponse> items = employees.stream()
				.map(employee -> {
					EmployeeCurrentComp comp = currentComp.get(employee.getId());
					SalaryBand band = comp == null || comp.getBandId() == null ? null : bands.get(comp.getBandId());
					return toSummary(employee, comp, band);
				})
				.toList();

		String nextCursor = null;
		if (window.hasNext() && !employees.isEmpty()) {
			nextCursor = encodeCursor(window.positionAt(employees.size() - 1));
		}

		return new KeysetPage<>(items, nextCursor);
	}

	/** FR-2.7: same filters as {@link #list}, unpaginated — the export always matches the on-screen filter. */
	public List<EmployeeSummaryResponse> exportAll(
			String query, UUID departmentId, UUID locationId, String countryCode, UUID jobLevelId, String status) {

		Specification<Employee> spec = Specification.<Employee>unrestricted()
				.and(nonNullOrUnrestricted(EmployeeSpecifications.search(query)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.departmentId(departmentId)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.locationId(locationId)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.countryCode(countryCode)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.jobLevelId(jobLevelId)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.status(status)));

		List<Employee> employees = employeeRepository.findAll(spec, SORT);
		Map<UUID, EmployeeCurrentComp> currentComp = employeeCurrentCompRepository
				.findAllById(employees.stream().map(Employee::getId).toList())
				.stream()
				.collect(Collectors.toMap(EmployeeCurrentComp::getEmployeeId, c -> c));
		Map<UUID, SalaryBand> bands = fetchBands(currentComp.values());

		return employees.stream()
				.map(employee -> {
					EmployeeCurrentComp comp = currentComp.get(employee.getId());
					SalaryBand band = comp == null || comp.getBandId() == null ? null : bands.get(comp.getBandId());
					return toSummary(employee, comp, band);
				})
				.toList();
	}

	public EmployeeDetailResponse get(UUID id) {
		Employee employee = employeeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		EmployeeCurrentComp comp = employeeCurrentCompRepository.findById(id).orElse(null);
		return toDetail(employee, comp, findBand(comp), fetchComponents(comp));
	}

	/**
	 * FR-6.6: this employee's position against the pay distribution of their cohort (same job
	 * level, any location sharing their location's country), using {@code normalizedAnnualBase} —
	 * always USD (CLAUDE.md §6.4) — so a cohort spanning several currencies still compares fairly.
	 * Cohorts under {@link #PEER_COHORT_SUPPRESSION_THRESHOLD} are suppressed: no percentile figure
	 * could avoid identifying an individual in a group that small.
	 */
	public PeerComparisonResponse peers(UUID id) {
		Employee employee = employeeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		Location location = locationRepository.findById(employee.getLocationId()).orElseThrow(NoSuchElementException::new);
		List<UUID> countryLocationIds = locationRepository.findByCountryCode(location.getCountryCode()).stream()
				.map(Location::getId)
				.toList();

		List<Employee> cohort = employeeRepository.findByJobLevelIdAndLocationIdInAndStatusNot(
				employee.getJobLevelId(), countryLocationIds, "TERMINATED");

		Map<UUID, EmployeeCurrentComp> comps = employeeCurrentCompRepository
				.findAllById(cohort.stream().map(Employee::getId).toList())
				.stream()
				.collect(Collectors.toMap(EmployeeCurrentComp::getEmployeeId, c -> c));

		List<BigDecimal> normalizedBases = comps.values().stream()
				.map(EmployeeCurrentComp::getNormalizedAnnualBase)
				.filter(java.util.Objects::nonNull)
				.sorted()
				.toList();

		int cohortSize = normalizedBases.size();
		if (cohortSize < PEER_COHORT_SUPPRESSION_THRESHOLD) {
			return new PeerComparisonResponse(cohortSize, true, null, null, null, null);
		}

		BigDecimal selfValue = comps.containsKey(id) ? comps.get(id).getNormalizedAnnualBase() : null;
		String baseCurrency = "USD";
		Money p25 = new Money(percentile(normalizedBases, 0.25), baseCurrency);
		Money median = new Money(percentile(normalizedBases, 0.50), baseCurrency);
		Money p75 = new Money(percentile(normalizedBases, 0.75), baseCurrency);
		Integer percentileRank = selfValue == null ? null : percentileRankOf(normalizedBases, selfValue);

		return new PeerComparisonResponse(cohortSize, false, p25, median, p75, percentileRank);
	}

	@Transactional
	public EmployeeDetailResponse create(EmployeeCreateRequest request) {
		Employee employee = Employee.builder()
				.employeeNumber(request.employeeNumber())
				.firstName(request.firstName())
				.lastName(request.lastName())
				.workEmail(request.workEmail())
				.departmentId(request.departmentId())
				.locationId(request.locationId())
				.jobFamilyId(request.jobFamilyId())
				.jobLevelId(request.jobLevelId())
				.managerId(request.managerId())
				.hireDate(request.hireDate())
				.employmentType(request.employmentType())
				.fte(request.fte())
				.build();
		employeeRepository.save(employee);
		return toDetail(employee, null, null, List.of());
	}

	/** FR-2.5: editing job level or location never touches pay — see {@link Employee#updateProfile}. */
	@Transactional
	public EmployeeDetailResponse update(UUID id, EmployeeUpdateRequest request) {
		Employee employee = employeeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		employee.updateProfile(
				request.firstName(), request.lastName(), request.workEmail(), request.departmentId(),
				request.locationId(), request.jobFamilyId(), request.jobLevelId(), request.managerId(),
				request.employmentType(), request.fte());
		employeeRepository.save(employee);
		EmployeeCurrentComp comp = employeeCurrentCompRepository.findById(id).orElse(null);
		return toDetail(employee, comp, findBand(comp), fetchComponents(comp));
	}

	/** FR-2.6: sets status and closes the open comp period on the termination date, if one exists. */
	@Transactional
	public EmployeeDetailResponse terminate(UUID id, LocalDate terminationDate) {
		Employee employee = employeeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		employee.terminate(terminationDate);
		employeeRepository.save(employee);

		compensationRecordRepository.findByEmployeeIdAndEffectiveToIsNull(id).ifPresent(open -> {
			open.close(terminationDate);
			compensationRecordRepository.save(open);
		});
		// Closing the ledger's open period leaves no open period at all — refresh removes the now-stale
		// employee_current_comp row, so a terminated employee stops showing a "current pay" that no
		// longer exists (Technical-Requirements.md §4.4: the projection is maintained transactionally
		// by whichever service call changed the ledger, not by a trigger).
		projector.refresh(id);

		EmployeeCurrentComp comp = employeeCurrentCompRepository.findById(id).orElse(null);
		return toDetail(employee, comp, findBand(comp), fetchComponents(comp));
	}

	private Specification<Employee> nonNullOrUnrestricted(Specification<Employee> spec) {
		return spec == null ? Specification.unrestricted() : spec;
	}

	private ScrollPosition toScrollPosition(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return ScrollPosition.keyset();
		}
		Cursor decoded = cursorCodec.decode(cursor);
		Map<String, Object> keys = Map.of(
				"lastName", decoded.keys().get("lastName"),
				"id", UUID.fromString(decoded.keys().get("id")));
		return ScrollPosition.forward(keys);
	}

	private String encodeCursor(ScrollPosition position) {
		if (!(position instanceof KeysetScrollPosition keysetPosition)) {
			return null;
		}
		Map<String, Object> keys = keysetPosition.getKeys();
		return cursorCodec.encode(new Cursor(Map.of(
				"lastName", String.valueOf(keys.get("lastName")),
				"id", String.valueOf(keys.get("id")))));
	}

	private Map<UUID, SalaryBand> fetchBands(java.util.Collection<EmployeeCurrentComp> comps) {
		List<UUID> bandIds = comps.stream()
				.map(EmployeeCurrentComp::getBandId)
				.filter(java.util.Objects::nonNull)
				.distinct()
				.toList();
		return salaryBandRepository.findAllById(bandIds).stream()
				.collect(Collectors.toMap(SalaryBand::getId, b -> b));
	}

	private SalaryBand findBand(EmployeeCurrentComp comp) {
		if (comp == null || comp.getBandId() == null) {
			return null;
		}
		return salaryBandRepository.findById(comp.getBandId()).orElse(null);
	}

	private BandBoundaries toBoundaries(SalaryBand band) {
		if (band == null) {
			return null;
		}
		String currency = band.getCurrency();
		return new BandBoundaries(
				new Money(band.getMinAmount(), currency),
				new Money(band.getMidAmount(), currency),
				new Money(band.getMaxAmount(), currency));
	}

	private EmployeeSummaryResponse toSummary(Employee employee, EmployeeCurrentComp comp, SalaryBand band) {
		return new EmployeeSummaryResponse(
				employee.getId(), employee.getEmployeeNumber(), employee.getFirstName(), employee.getLastName(),
				employee.getWorkEmail(), employee.getDepartmentId(), employee.getLocationId(), employee.getJobLevelId(),
				employee.getEmploymentType(), employee.getFte(), employee.getStatus(), employee.getHireDate(),
				employee.getTerminationDate(), employee.isBandMismatched(),
				comp == null ? null : comp.getBase(),
				comp == null ? null : comp.getCompaRatio(),
				comp == null ? null : comp.getRangePenetration(),
				comp == null ? null : comp.getBandStatus(),
				toBoundaries(band));
	}

	private EmployeeDetailResponse toDetail(
			Employee employee, EmployeeCurrentComp comp, SalaryBand band, List<CompensationComponentResponse> components) {
		return new EmployeeDetailResponse(
				employee.getId(), employee.getEmployeeNumber(), employee.getFirstName(), employee.getLastName(),
				employee.getWorkEmail(), employee.getDepartmentId(), employee.getLocationId(),
				employee.getJobFamilyId(), employee.getJobLevelId(), employee.getManagerId(),
				employee.getEmploymentType(), employee.getFte(), employee.getStatus(), employee.getHireDate(),
				employee.getTerminationDate(), employee.isBandMismatched(),
				comp == null ? null : comp.getBase(),
				comp == null ? null : comp.getCompaRatio(),
				comp == null ? null : comp.getRangePenetration(),
				comp == null ? null : comp.getBandStatus(),
				toBoundaries(band),
				components);
	}

	private List<CompensationComponentResponse> fetchComponents(EmployeeCurrentComp comp) {
		if (comp == null) {
			return List.of();
		}
		return compensationComponentRepository.findByCompensationRecordId(comp.getCompensationRecordId()).stream()
				.map(c -> new CompensationComponentResponse(c.getComponentType(), c.getAmount(), c.getPercentOfBase(), c.isRecurring()))
				.toList();
	}

	/** Linear-interpolation percentile (numpy's default "linear" method) over an ascending-sorted list. */
	private BigDecimal percentile(List<BigDecimal> sortedAscending, double p) {
		int n = sortedAscending.size();
		double rank = p * (n - 1);
		int lower = (int) Math.floor(rank);
		int upper = (int) Math.ceil(rank);
		if (lower == upper) {
			return sortedAscending.get(lower);
		}
		BigDecimal lowerValue = sortedAscending.get(lower);
		BigDecimal upperValue = sortedAscending.get(upper);
		BigDecimal fraction = BigDecimal.valueOf(rank - lower);
		return lowerValue.add(upperValue.subtract(lowerValue).multiply(fraction));
	}

	/** Share of the cohort at or below this value, as a whole-number percentile. */
	private int percentileRankOf(List<BigDecimal> sortedAscending, BigDecimal value) {
		long atOrBelow = sortedAscending.stream().filter(v -> v.compareTo(value) <= 0).count();
		return (int) Math.round((atOrBelow * 100.0) / sortedAscending.size());
	}

}
