package com.acme.salaryos.employee.service;

import com.acme.salaryos.audit.AuditService;
import com.acme.salaryos.band.domain.SalaryBand;
import com.acme.salaryos.band.repository.SalaryBandRepository;
import com.acme.salaryos.common.money.Money;
import com.acme.salaryos.common.paging.Cursor;
import com.acme.salaryos.common.paging.CursorCodec;
import com.acme.salaryos.common.paging.KeysetPage;
import com.acme.salaryos.compensation.domain.CompensationComponent;
import com.acme.salaryos.compensation.domain.CompensationRecord;
import com.acme.salaryos.compensation.domain.EmployeeCurrentComp;
import com.acme.salaryos.compensation.projection.EmployeeCurrentCompProjector;
import com.acme.salaryos.compensation.repository.CompensationComponentRepository;
import com.acme.salaryos.compensation.repository.CompensationRecordRepository;
import com.acme.salaryos.compensation.repository.EmployeeCurrentCompRepository;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.dto.BandBoundaries;
import com.acme.salaryos.employee.dto.CompensationComponentResponse;
import com.acme.salaryos.employee.dto.CompensationRecordResponse;
import com.acme.salaryos.employee.dto.EmployeeCreateRequest;
import com.acme.salaryos.employee.dto.EmployeeDetailResponse;
import com.acme.salaryos.employee.dto.EmployeeImportResult;
import com.acme.salaryos.employee.dto.EmployeeImportRowResult;
import com.acme.salaryos.employee.dto.EmployeeSummaryResponse;
import com.acme.salaryos.employee.dto.EmployeeUpdateRequest;
import com.acme.salaryos.employee.dto.PeerComparisonResponse;
import com.acme.salaryos.employee.dto.PeerImpactPreview;
import com.acme.salaryos.employee.repository.EmployeeRepository;
import com.acme.salaryos.employee.spec.EmployeeSpecifications;
import com.acme.salaryos.reference.domain.Location;
import com.acme.salaryos.reference.repository.DepartmentRepository;
import com.acme.salaryos.reference.repository.JobFamilyRepository;
import com.acme.salaryos.reference.repository.JobLevelRepository;
import com.acme.salaryos.reference.repository.LocationRepository;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
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
	private final DepartmentRepository departmentRepository;
	private final JobFamilyRepository jobFamilyRepository;
	private final JobLevelRepository jobLevelRepository;
	private final EmployeeCurrentCompProjector projector;
	private final CursorCodec cursorCodec;
	private final AuditService auditService;

	private static final Set<String> VALID_EMPLOYMENT_TYPES = Set.of("FULL_TIME", "PART_TIME", "CONTRACT");

	public EmployeeService(
			EmployeeRepository employeeRepository,
			EmployeeCurrentCompRepository employeeCurrentCompRepository,
			CompensationRecordRepository compensationRecordRepository,
			CompensationComponentRepository compensationComponentRepository,
			SalaryBandRepository salaryBandRepository,
			LocationRepository locationRepository,
			DepartmentRepository departmentRepository,
			JobFamilyRepository jobFamilyRepository,
			JobLevelRepository jobLevelRepository,
			EmployeeCurrentCompProjector projector,
			CursorCodec cursorCodec,
			AuditService auditService) {
		this.employeeRepository = employeeRepository;
		this.employeeCurrentCompRepository = employeeCurrentCompRepository;
		this.compensationRecordRepository = compensationRecordRepository;
		this.compensationComponentRepository = compensationComponentRepository;
		this.salaryBandRepository = salaryBandRepository;
		this.locationRepository = locationRepository;
		this.departmentRepository = departmentRepository;
		this.jobFamilyRepository = jobFamilyRepository;
		this.jobLevelRepository = jobLevelRepository;
		this.projector = projector;
		this.cursorCodec = cursorCodec;
		this.auditService = auditService;
	}

	public KeysetPage<EmployeeSummaryResponse> list(
			String query, UUID departmentId, UUID locationId, String countryCode, UUID jobLevelId,
			String status, String cursor, int limit, UUID currentUserId) {

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

		auditService.recordListRead(currentUserId, "EMPLOYEE", describeFilter(query, departmentId, locationId, countryCode, jobLevelId, status), items.size());

		return new KeysetPage<>(items, nextCursor);
	}

	private String describeFilter(String query, UUID departmentId, UUID locationId, String countryCode, UUID jobLevelId, String status) {
		StringBuilder sb = new StringBuilder();
		if (query != null) sb.append("q=").append(query).append(' ');
		if (departmentId != null) sb.append("departmentId=").append(departmentId).append(' ');
		if (locationId != null) sb.append("locationId=").append(locationId).append(' ');
		if (countryCode != null) sb.append("countryCode=").append(countryCode).append(' ');
		if (jobLevelId != null) sb.append("jobLevelId=").append(jobLevelId).append(' ');
		if (status != null) sb.append("status=").append(status).append(' ');
		return sb.isEmpty() ? "(none)" : sb.toString().trim();
	}

	/** FR-2.7: same filters as {@link #list}, unpaginated — the export always matches the on-screen filter. */
	public List<EmployeeSummaryResponse> exportAll(
			String query, UUID departmentId, UUID locationId, String countryCode, UUID jobLevelId, String status, UUID currentUserId) {

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

		List<EmployeeSummaryResponse> rows = employees.stream()
				.map(employee -> {
					EmployeeCurrentComp comp = currentComp.get(employee.getId());
					SalaryBand band = comp == null || comp.getBandId() == null ? null : bands.get(comp.getBandId());
					return toSummary(employee, comp, band);
				})
				.toList();

		auditService.recordListRead(currentUserId, "EMPLOYEE",
				"EXPORT " + describeFilter(query, departmentId, locationId, countryCode, jobLevelId, status), rows.size());

		return rows;
	}

	public EmployeeDetailResponse get(UUID id, UUID currentUserId) {
		Employee employee = employeeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		EmployeeCurrentComp comp = employeeCurrentCompRepository.findById(id).orElse(null);
		auditService.recordDetailRead(currentUserId, "EMPLOYEE", id);
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
		CohortComps cohort = fetchCohort(employee);

		if (cohort.normalizedBases().size() < PEER_COHORT_SUPPRESSION_THRESHOLD) {
			return new PeerComparisonResponse(cohort.normalizedBases().size(), true, null, null, null, null);
		}

		BigDecimal selfValue = cohort.byEmployeeId().containsKey(id) ? cohort.byEmployeeId().get(id).getNormalizedAnnualBase() : null;
		String baseCurrency = "USD";
		Money p25 = new Money(percentile(cohort.normalizedBases(), 0.25), baseCurrency);
		Money median = new Money(percentile(cohort.normalizedBases(), 0.50), baseCurrency);
		Money p75 = new Money(percentile(cohort.normalizedBases(), 0.75), baseCurrency);
		Integer percentileRank = selfValue == null ? null : percentileRankOf(cohort.normalizedBases(), selfValue);

		return new PeerComparisonResponse(cohort.normalizedBases().size(), false, p25, median, p75, percentileRank);
	}

	/**
	 * P6.4: the propose-change dialog's "peer percentile before and after" — same cohort and same
	 * p25/median/p75 as {@link #peers}, since one person's hypothetical raise doesn't move the
	 * market. Only the rank changes: {@code hypotheticalNormalizedAnnualBase} replaces this
	 * employee's own current contribution to the distribution before the "after" rank is computed,
	 * so a raise never appears to move anyone else's position.
	 */
	public PeerImpactPreview peersImpact(UUID id, BigDecimal hypotheticalNormalizedAnnualBase) {
		Employee employee = employeeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		CohortComps cohort = fetchCohort(employee);

		if (cohort.normalizedBases().size() < PEER_COHORT_SUPPRESSION_THRESHOLD) {
			return new PeerImpactPreview(cohort.normalizedBases().size(), true, null, null, null, null, null);
		}

		BigDecimal selfValue = cohort.byEmployeeId().containsKey(id) ? cohort.byEmployeeId().get(id).getNormalizedAnnualBase() : null;
		String baseCurrency = "USD";
		Money p25 = new Money(percentile(cohort.normalizedBases(), 0.25), baseCurrency);
		Money median = new Money(percentile(cohort.normalizedBases(), 0.50), baseCurrency);
		Money p75 = new Money(percentile(cohort.normalizedBases(), 0.75), baseCurrency);
		Integer percentileBefore = selfValue == null ? null : percentileRankOf(cohort.normalizedBases(), selfValue);

		List<BigDecimal> afterBases = new java.util.ArrayList<>(cohort.normalizedBases());
		if (selfValue != null) {
			afterBases.remove(selfValue);
		}
		afterBases.add(hypotheticalNormalizedAnnualBase);
		afterBases.sort(java.util.Comparator.naturalOrder());
		Integer percentileAfter = percentileRankOf(afterBases, hypotheticalNormalizedAnnualBase);

		return new PeerImpactPreview(cohort.normalizedBases().size(), false, p25, median, p75, percentileBefore, percentileAfter);
	}

	private record CohortComps(List<BigDecimal> normalizedBases, Map<UUID, EmployeeCurrentComp> byEmployeeId) {
	}

	private CohortComps fetchCohort(Employee employee) {
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

		return new CohortComps(normalizedBases, comps);
	}

	/** FR-6.7: the full ledger, newest period first. */
	public List<CompensationRecordResponse> compensationHistory(UUID id, UUID currentUserId) {
		if (!employeeRepository.existsById(id)) {
			throw new NoSuchElementException();
		}
		auditService.recordDetailRead(currentUserId, "EMPLOYEE_COMPENSATION_HISTORY", id);
		return compensationRecordRepository.findByEmployeeIdOrderByEffectiveFromDesc(id).stream()
				.map(this::toCompensationRecordResponse)
				.toList();
	}

	/** FR-3.6: the one period valid on {@code asAt}, or empty if this employee had no pay yet on that date. */
	public Optional<CompensationRecordResponse> compensationAsAt(UUID id, LocalDate asAt, UUID currentUserId) {
		if (!employeeRepository.existsById(id)) {
			throw new NoSuchElementException();
		}
		auditService.recordDetailRead(currentUserId, "EMPLOYEE_COMPENSATION_AS_AT", id);
		return compensationRecordRepository.findAsAt(id, asAt).map(this::toCompensationRecordResponse);
	}

	private CompensationRecordResponse toCompensationRecordResponse(CompensationRecord record) {
		return new CompensationRecordResponse(
				record.getId(), record.getEffectiveFrom(), record.getEffectiveTo(), record.getBase(),
				record.getPayFrequency(), record.getAnnualBaseAmount(), record.getNormalizedAnnualBase(),
				record.getBandId(), record.getCompaRatio(), record.getRangePenetration(), record.getChangeReason(),
				record.getChangeId(), record.getSupersededBy(), record.getCreatedBy(), record.getCreatedAt());
	}

	@Transactional
	public EmployeeDetailResponse create(EmployeeCreateRequest request, UUID currentUserId) {
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
		auditService.recordWrite(currentUserId, "CREATE_EMPLOYEE", "EMPLOYEE", employee.getId(), null, employee);
		return toDetail(employee, null, null, List.of());
	}

	/**
	 * P8.4: {@code dryRun} produces the same diff without writing anything (same contract as
	 * {@code BandService.importCsv}, P5.3). Rows are 1-indexed from the first data row (row 1 is
	 * the header: {@code employeeNumber,firstName,lastName,workEmail,departmentId,locationId,
	 * jobFamilyId,jobLevelId,managerId,hireDate,employmentType,fte} — {@code managerId} may be
	 * blank). An existing {@code employeeNumber} updates that employee's profile (never their pay —
	 * {@link Employee#updateProfile} is the same method the single-employee edit endpoint calls, so
	 * a level/location change here also sets {@code bandMismatched} exactly as it would there); a
	 * new one creates.
	 */
	@Transactional
	public EmployeeImportResult importCsv(MultipartFile file, boolean dryRun, UUID createdBy) {
		List<EmployeeImportRowResult> rows = new ArrayList<>();
		int created = 0;
		int updated = 0;
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
				EmployeeImportRowResult result = importRow(rowNumber, line, dryRun, createdBy);
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
		return new EmployeeImportResult(dryRun, rows.size(), created, updated, errors, rowsApplied, rows);
	}

	private EmployeeImportRowResult importRow(int rowNumber, String line, boolean dryRun, UUID createdBy) {
		String[] fields = line.split(",", -1);
		if (fields.length < 11) {
			return errorRow(rowNumber, line, "Expected 11 or 12 columns, found " + fields.length + ".");
		}

		String employeeNumber = fields[0].trim();
		String firstName = fields[1].trim();
		String lastName = fields[2].trim();
		String workEmail = fields[3].trim();
		UUID departmentId;
		UUID locationId;
		UUID jobFamilyId;
		UUID jobLevelId;
		UUID managerId;
		LocalDate hireDate;
		String employmentType = fields[10].trim();
		BigDecimal fte;
		try {
			departmentId = UUID.fromString(fields[4].trim());
			locationId = UUID.fromString(fields[5].trim());
			jobFamilyId = UUID.fromString(fields[6].trim());
			jobLevelId = UUID.fromString(fields[7].trim());
			managerId = fields[8].isBlank() ? null : UUID.fromString(fields[8].trim());
			hireDate = LocalDate.parse(fields[9].trim());
			fte = new BigDecimal(fields.length > 11 ? fields[11].trim() : "1.00");
		}
		catch (RuntimeException malformed) {
			return errorRow(rowNumber, line, "Could not parse row: " + malformed.getMessage());
		}

		if (employeeNumber.isBlank() || firstName.isBlank() || lastName.isBlank() || workEmail.isBlank()) {
			return errorRow(rowNumber, line, "employeeNumber, firstName, lastName, and workEmail are required.");
		}
		if (!VALID_EMPLOYMENT_TYPES.contains(employmentType)) {
			return errorRow(rowNumber, line, "employmentType must be one of " + VALID_EMPLOYMENT_TYPES + ".");
		}
		if (fte.compareTo(new BigDecimal("0.01")) < 0 || fte.compareTo(BigDecimal.ONE) > 0) {
			return errorRow(rowNumber, line, "fte must be between 0.01 and 1.00.");
		}
		if (!departmentRepository.existsById(departmentId)) {
			return errorRow(rowNumber, line, "No department " + departmentId + ".");
		}
		if (!locationRepository.existsById(locationId)) {
			return errorRow(rowNumber, line, "No location " + locationId + ".");
		}
		if (!jobFamilyRepository.existsById(jobFamilyId)) {
			return errorRow(rowNumber, line, "No job family " + jobFamilyId + ".");
		}
		if (!jobLevelRepository.existsById(jobLevelId)) {
			return errorRow(rowNumber, line, "No job level " + jobLevelId + ".");
		}
		if (managerId != null && !employeeRepository.existsById(managerId)) {
			return errorRow(rowNumber, line, "No employee " + managerId + " to serve as manager.");
		}

		Optional<Employee> existing = employeeRepository.findByEmployeeNumber(employeeNumber);
		if (existing.isEmpty()) {
			if (!dryRun) {
				Employee employee = Employee.builder()
						.employeeNumber(employeeNumber).firstName(firstName).lastName(lastName).workEmail(workEmail)
						.departmentId(departmentId).locationId(locationId).jobFamilyId(jobFamilyId).jobLevelId(jobLevelId)
						.managerId(managerId).hireDate(hireDate).employmentType(employmentType).fte(fte)
						.build();
				employeeRepository.save(employee);
				auditService.recordWrite(createdBy, "CREATE_EMPLOYEE", "EMPLOYEE", employee.getId(), null, employee);
			}
			return new EmployeeImportRowResult(rowNumber, "CREATE", employeeNumber, firstName, lastName, null);
		}

		if (!dryRun) {
			Employee employee = existing.get();
			String beforeJson = auditService.snapshot(employee);
			employee.updateProfile(firstName, lastName, workEmail, departmentId, locationId, jobFamilyId, jobLevelId, managerId, employmentType, fte);
			employeeRepository.save(employee);
			auditService.recordWriteFromJson(createdBy, "UPDATE_EMPLOYEE", "EMPLOYEE", employee.getId(), beforeJson, auditService.snapshot(employee));
		}
		return new EmployeeImportRowResult(rowNumber, "UPDATE", employeeNumber, firstName, lastName, null);
	}

	private EmployeeImportRowResult errorRow(int rowNumber, String rawLine, String message) {
		String[] fields = rawLine.split(",", -1);
		String employeeNumber = fields.length > 0 ? fields[0].trim() : null;
		return new EmployeeImportRowResult(rowNumber, "ERROR", employeeNumber, null, null, message);
	}

	/** FR-2.5: editing job level or location never touches pay — see {@link Employee#updateProfile}. */
	@Transactional
	public EmployeeDetailResponse update(UUID id, EmployeeUpdateRequest request, UUID currentUserId) {
		Employee employee = employeeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		String beforeJson = auditService.snapshot(employee);
		employee.updateProfile(
				request.firstName(), request.lastName(), request.workEmail(), request.departmentId(),
				request.locationId(), request.jobFamilyId(), request.jobLevelId(), request.managerId(),
				request.employmentType(), request.fte());
		employeeRepository.save(employee);
		auditService.recordWriteFromJson(currentUserId, "UPDATE_EMPLOYEE", "EMPLOYEE", id, beforeJson, auditService.snapshot(employee));
		EmployeeCurrentComp comp = employeeCurrentCompRepository.findById(id).orElse(null);
		return toDetail(employee, comp, findBand(comp), fetchComponents(comp));
	}

	/**
	 * FR-2.6: sets status and closes the open comp period on the termination date, if one exists.
	 * Pay runs through and includes the termination date (user-confirmed, P5.4) — {@code validity}
	 * is a `[)` range (inclusive start, exclusive end), so the row must close at
	 * {@code terminationDate.plusDays(1)}, not {@code terminationDate} itself, for that last day to
	 * actually be covered. Same convention as {@code EffectiveDating}'s closing math.
	 */
	@Transactional
	public EmployeeDetailResponse terminate(UUID id, LocalDate terminationDate, UUID currentUserId) {
		Employee employee = employeeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		String beforeJson = auditService.snapshot(employee);
		employee.terminate(terminationDate);
		employeeRepository.save(employee);

		compensationRecordRepository.findByEmployeeIdAndEffectiveToIsNull(id).ifPresent(open -> {
			open.close(terminationDate.plusDays(1));
			compensationRecordRepository.save(open);
		});
		// Closing the ledger's open period leaves no open period at all — refresh removes the now-stale
		// employee_current_comp row, so a terminated employee stops showing a "current pay" that no
		// longer exists (Technical-Requirements.md §4.4: the projection is maintained transactionally
		// by whichever service call changed the ledger, not by a trigger).
		projector.refresh(id);

		auditService.recordWriteFromJson(currentUserId, "TERMINATE_EMPLOYEE", "EMPLOYEE", id, beforeJson, auditService.snapshot(employee));

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
