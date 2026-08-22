package com.acme.salaryos.employee.web;

import com.acme.salaryos.common.paging.KeysetPage;
import com.acme.salaryos.employee.dto.CompensationRecordResponse;
import com.acme.salaryos.employee.dto.EmployeeCreateRequest;
import com.acme.salaryos.employee.dto.EmployeeDetailResponse;
import com.acme.salaryos.employee.dto.EmployeeImportResult;
import com.acme.salaryos.employee.dto.EmployeeSummaryResponse;
import com.acme.salaryos.employee.dto.InitialCompensationRequest;
import com.acme.salaryos.employee.dto.EmployeeTerminateRequest;
import com.acme.salaryos.employee.dto.EmployeeUpdateRequest;
import com.acme.salaryos.employee.dto.PeerComparisonResponse;
import com.acme.salaryos.employee.service.EmployeeService;
import com.acme.salaryos.reference.dto.DepartmentResponse;
import com.acme.salaryos.reference.dto.JobLevelResponse;
import com.acme.salaryos.reference.dto.LocationResponse;
import com.acme.salaryos.reference.service.ReferenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * P4 (Technical-Requirements.md §5). {@code list}/{@code get} are real (P4.1); the rest are still
 * stubs (P4.2+). Every method carries the {@code @PreAuthorize} its capability requires per
 * CLAUDE.md §7 — {@code RolePermissionMatrixTest} fails the build on a missing or mismatched one.
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

	private final EmployeeService employeeService;
	private final ReferenceService referenceService;

	public EmployeeController(EmployeeService employeeService, ReferenceService referenceService) {
		this.employeeService = employeeService;
		this.referenceService = referenceService;
	}

	/** View employees & their pay: HR Admin, HR Manager, Comp Analyst, Auditor. */
	@GetMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public KeysetPage<EmployeeSummaryResponse> list(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) UUID departmentId,
			@RequestParam(required = false) UUID locationId,
			@RequestParam(required = false) String countryCode,
			@RequestParam(required = false) UUID jobLevelId,
			@RequestParam(required = false) String status,
			// IN_BAND/BELOW_MIN/ABOVE_MAX/NO_BAND (FR-2.2).
			@RequestParam(required = false) String bandStatus,
			// "compaRatio" for the FR-2.2 compa-ratio sort; anything else (including absent)
			// keeps the default lastName order.
			@RequestParam(required = false) String sortBy,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "50") int limit,
			@AuthenticationPrincipal UUID currentUserId) {
		int pageSize = Math.min(Math.max(limit, 1), 200);
		return employeeService.list(q, departmentId, locationId, countryCode, jobLevelId, status, bandStatus, sortBy, cursor, pageSize, currentUserId);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public EmployeeDetailResponse get(@PathVariable UUID id, @AuthenticationPrincipal UUID currentUserId) {
		return employeeService.get(id, currentUserId);
	}

	/** FR-6.7: the full pay-history ledger, newest period first. */
	@GetMapping("/{id}/compensation")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public List<CompensationRecordResponse> compensationHistory(@PathVariable UUID id, @AuthenticationPrincipal UUID currentUserId) {
		return employeeService.compensationHistory(id, currentUserId);
	}

	/** FR-3.6: what this employee was paid on a chosen date. 404 (via NoSuchElementException) if there was no pay yet on that date. */
	@GetMapping("/{id}/compensation/as-at")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public CompensationRecordResponse compensationAsAt(
			@PathVariable UUID id, @RequestParam LocalDate date, @AuthenticationPrincipal UUID currentUserId) {
		return employeeService.compensationAsAt(id, date, currentUserId).orElseThrow();
	}

	/** FR-6.6: this employee's position against their (job level × country) cohort's pay distribution. */
	@GetMapping("/{id}/peers")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public PeerComparisonResponse peers(@PathVariable UUID id) {
		return employeeService.peers(id);
	}

	/** FR-2.7: CSV of the exact same filter as {@link #list} — never a separate, driftable query. */
	@GetMapping("/export")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<byte[]> export(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) UUID departmentId,
			@RequestParam(required = false) UUID locationId,
			@RequestParam(required = false) String countryCode,
			@RequestParam(required = false) UUID jobLevelId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String bandStatus,
			@AuthenticationPrincipal UUID currentUserId) {

		List<EmployeeSummaryResponse> rows = employeeService.exportAll(q, departmentId, locationId, countryCode, jobLevelId, status, bandStatus, currentUserId);
		Map<UUID, String> departmentNames = referenceService.departments().stream()
				.collect(Collectors.toMap(DepartmentResponse::id, DepartmentResponse::name));
		Map<UUID, String> locationNames = referenceService.locations().stream()
				.collect(Collectors.toMap(LocationResponse::id, LocationResponse::name));
		Map<UUID, String> jobLevelTitles = referenceService.jobLevels().stream()
				.collect(Collectors.toMap(JobLevelResponse::id, JobLevelResponse::title));

		byte[] csv = toCsv(rows, departmentNames, locationNames, jobLevelTitles);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("text/csv"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"employees.csv\"")
				.body(csv);
	}

	private byte[] toCsv(
			List<EmployeeSummaryResponse> rows, Map<UUID, String> departmentNames,
			Map<UUID, String> locationNames, Map<UUID, String> jobLevelTitles) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (PrintWriter writer = new PrintWriter(buffer, false, StandardCharsets.UTF_8)) {
			writer.println("Employee Number,First Name,Last Name,Department,Location,Level,Base Pay,Currency,Compa-Ratio,Band Status,Status");
			for (EmployeeSummaryResponse row : rows) {
				writer.println(String.join(",",
						csvField(row.employeeNumber()),
						csvField(row.firstName()),
						csvField(row.lastName()),
						csvField(departmentNames.get(row.departmentId())),
						csvField(locationNames.get(row.locationId())),
						csvField(jobLevelTitles.get(row.jobLevelId())),
						csvField(row.currentBasePay() == null ? null : row.currentBasePay().amount().toPlainString()),
						csvField(row.currentBasePay() == null ? null : row.currentBasePay().currency()),
						csvField(row.compaRatio() == null ? null : row.compaRatio().toPlainString()),
						csvField(row.bandStatus()),
						csvField(row.status())));
			}
		}
		return buffer.toByteArray();
	}

	private String csvField(String value) {
		if (value == null) {
			return "";
		}
		String escaped = value.replace("\"", "\"\"");
		return "\"" + escaped + "\"";
	}

	/** Create / edit employee record: HR Admin, HR Manager. */
	@PostMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public EmployeeDetailResponse create(@Valid @RequestBody EmployeeCreateRequest request, @AuthenticationPrincipal UUID currentUserId) {
		return employeeService.create(request, currentUserId);
	}

	/** A new hire's first-ever pay period — not a proposed change, nothing to approve against.
	 * Same capability as {@link #create}: HR Admin, HR Manager. */
	@PostMapping("/{id}/initial-compensation")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public EmployeeDetailResponse setInitialCompensation(
			@PathVariable UUID id, @Valid @RequestBody InitialCompensationRequest request, @AuthenticationPrincipal UUID currentUserId) {
		return employeeService.setInitialCompensation(id, request, currentUserId);
	}

	/** P8.4 / "Import / bulk upload": HR Admin only. {@code ?dryRun=true} returns the diff without writing. */
	@PostMapping("/import")
	@PreAuthorize("hasRole('HR_ADMIN')")
	public EmployeeImportResult importCsv(
			@RequestPart MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun,
			@AuthenticationPrincipal UUID currentUserId) {
		return employeeService.importCsv(file, dryRun, currentUserId);
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public EmployeeDetailResponse update(
			@PathVariable UUID id, @Valid @RequestBody EmployeeUpdateRequest request, @AuthenticationPrincipal UUID currentUserId) {
		return employeeService.update(id, request, currentUserId);
	}

	@PostMapping("/{id}/terminate")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public EmployeeDetailResponse terminate(
			@PathVariable UUID id, @Valid @RequestBody EmployeeTerminateRequest request, @AuthenticationPrincipal UUID currentUserId) {
		return employeeService.terminate(id, request.terminationDate(), currentUserId);
	}

	private ResponseEntity<Void> notImplemented() {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

}
