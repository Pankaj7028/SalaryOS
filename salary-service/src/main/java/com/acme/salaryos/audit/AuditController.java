package com.acme.salaryos.audit;

import com.acme.salaryos.audit.dto.AuditEventResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** P8.3 (Technical-Requirements.md §5, FR-7.4). Read the audit log: HR Admin, Auditor. */
@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {

	private final AuditService auditService;

	public AuditController(AuditService auditService) {
		this.auditService = auditService;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','AUDITOR')")
	public List<AuditEventResponse> search(
			@RequestParam(required = false) UUID actorUserId,
			@RequestParam(required = false) String entityType,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to) {
		return auditService.search(actorUserId, entityType, action, from, to);
	}

	/** FR-7.4: CSV of the exact same filter as {@link #search} — never a separate, driftable query. */
	@GetMapping("/export")
	@PreAuthorize("hasAnyRole('HR_ADMIN','AUDITOR')")
	public ResponseEntity<byte[]> export(
			@RequestParam(required = false) UUID actorUserId,
			@RequestParam(required = false) String entityType,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to) {
		List<AuditEventResponse> rows = auditService.search(actorUserId, entityType, action, from, to);
		byte[] csv = toCsv(rows);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("text/csv"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
				.body(csv);
	}

	private byte[] toCsv(List<AuditEventResponse> rows) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (PrintWriter writer = new PrintWriter(buffer, false, StandardCharsets.UTF_8)) {
			writer.println("Occurred At,Actor Email,Actor Role,Action,Entity Type,Entity Id,IP");
			for (AuditEventResponse row : rows) {
				writer.println(String.join(",",
						csvField(row.occurredAt() == null ? null : row.occurredAt().toString()),
						csvField(row.actorEmail()),
						csvField(row.actorRole()),
						csvField(row.action()),
						csvField(row.entityType()),
						csvField(row.entityId() == null ? null : row.entityId().toString()),
						csvField(row.ip())));
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

}
