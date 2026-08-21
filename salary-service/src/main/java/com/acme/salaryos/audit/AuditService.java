package com.acme.salaryos.audit;

import com.acme.salaryos.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.UUID;

/**
 * FR-7.1 / FR-7.2: every write and every read of individual pay data records actor, action,
 * entity, and timestamp. {@code occurredAt} is {@code @CreationTimestamp} on {@link AuditEvent}
 * itself — never set here.
 *
 * <p>Deliberately explicit calls at each write/read site, not a generic {@code @Audited} AOP
 * aspect capturing before/after by reflection: {@code ApplyDueChangesJob}'s scheduled path (P6.2)
 * has no HTTP request and therefore no {@code SecurityContextHolder} authentication to intercept —
 * every call site already carries its own acting-user id (the established
 * {@code @AuthenticationPrincipal UUID currentUserId} convention, or {@code decidedBy} for the
 * scheduled path), so passing it explicitly here is simpler and correct in both cases, not a
 * reflection-based guess at "the current user."
 *
 * <p>Callers must invoke this from within the SAME {@code @Transactional} method as the write
 * itself (backend doc §7 — "an audit trail that can be missing the one row that mattered is not
 * an audit trail"); this class starts no transaction of its own.
 */
@Service
public class AuditService {

	private final AuditEventRepository auditEventRepository;
	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;

	public AuditService(AuditEventRepository auditEventRepository, UserRepository userRepository, ObjectMapper objectMapper) {
		this.auditEventRepository = auditEventRepository;
		this.userRepository = userRepository;
		this.objectMapper = objectMapper;
	}

	/** FR-7.1: {@code before}/{@code after} are typically the entity itself (or {@code null} for a
	 * create/delete's missing side) — every entity in this app is a flat record of scalar/UUID
	 * fields (P1.9: no {@code @ManyToOne} object graphs), so direct serialisation is safe, no
	 * lazy-loading or circular-reference risk, and never touches {@code employee_demographics}
	 * (CLAUDE.md §6.6 — nothing audited here ever joins that table). */
	public void recordWrite(UUID actorUserId, String action, String entityType, UUID entityId, Object before, Object after) {
		save(actorUserId, action, entityType, entityId, toJson(before), toJson(after));
	}

	/** For a call site that mutates a JPA entity in place before it can log — snapshot the "before"
	 * state to a frozen JSON string immediately, before the mutation, then pass both strings to
	 * {@link #recordWriteFromJson}. Serialising the live (post-mutation) object at the end would
	 * silently record the "before" column as the after-state a second time. */
	public String snapshot(Object value) {
		return toJson(value);
	}

	public void recordWriteFromJson(UUID actorUserId, String action, String entityType, UUID entityId, String beforeJson, String afterJson) {
		save(actorUserId, action, entityType, entityId, beforeJson, afterJson);
	}

	/** FR-7.2: a list read records the filter and the count, never the individual ids — "the
	 * filter is what answers 'what were they looking for'" (backend doc §7). */
	public void recordListRead(UUID actorUserId, String entityType, String filterDescription, int count) {
		save(actorUserId, "READ_LIST", entityType, null, null,
				toJson(Map.of("filter", filterDescription, "count", count)));
	}

	/** FR-7.2: an individual-record read records which one. */
	public void recordDetailRead(UUID actorUserId, String entityType, UUID entityId) {
		save(actorUserId, "READ_DETAIL", entityType, entityId, null, null);
	}

	private void save(UUID actorUserId, String action, String entityType, UUID entityId, String beforeJson, String afterJson) {
		String actorRole = userRepository.findById(actorUserId).map(u -> u.getRole()).orElse("UNKNOWN");
		auditEventRepository.save(AuditEvent.builder()
				.actorUserId(actorUserId)
				.actorRole(actorRole)
				.action(action)
				.entityType(entityType)
				.entityId(entityId)
				.beforeJson(beforeJson)
				.afterJson(afterJson)
				.requestId(UUID.randomUUID())
				.ip(currentRemoteAddress())
				.build());
	}

	private String toJson(Object value) {
		return value == null ? null : objectMapper.writeValueAsString(value);
	}

	/** {@code null} outside an HTTP request (the scheduled {@code ApplyDueChangesJob} path) — the
	 * column is nullable for exactly this reason. */
	private InetAddress currentRemoteAddress() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
			return null;
		}
		try {
			return InetAddress.getByName(attrs.getRequest().getRemoteAddr());
		}
		catch (UnknownHostException e) {
			return null;
		}
	}

}
