package com.acme.salaryos;

import com.acme.salaryos.audit.AuditController;
import com.acme.salaryos.audit.AuditService;
import com.acme.salaryos.audit.dto.AuditEventResponse;
import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P8.3's own Verify clause and FR-7.4: audit search filters by actor, entity, action, and date
 * range, and the CSV export honours the exact same filter. Writes real rows through
 * {@code AuditService.recordWrite} (the same call every other domain service makes) rather than
 * a hand-crafted repository save, so this exercises the actual write path too.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class AuditSearchTest {

	@Autowired
	private AuditService auditService;
	@Autowired
	private AuditController auditController;
	@Autowired
	private UserRepository userRepository;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void searchFiltersByActorEntityActionAndDateRangeAndExportMatchesTheSameFilter() {
		User actor = userRepository.save(User.builder()
				.email("audit-search@acme.test").fullName("Audit Search").passwordHash("{argon2}stub").role("HR_ADMIN").build());

		String entityType = "AUDIT_SEARCH_TEST_" + UUID.randomUUID();
		UUID bandLikeId = UUID.randomUUID();
		UUID otherId = UUID.randomUUID();

		auditService.recordWrite(actor.getId(), "CREATE_THING", entityType, bandLikeId, null, "created");
		auditService.recordWrite(actor.getId(), "UPDATE_THING", entityType, bandLikeId, "created", "updated");
		auditService.recordWrite(actor.getId(), "CREATE_THING", entityType, otherId, null, "created-other");

		// Unfiltered-by-entity-type but filtered by actor: at least our three rows, order newest first.
		List<AuditEventResponse> byActor = auditService.search(actor.getId(), null, null, null, null);
		assertThat(byActor).hasSizeGreaterThanOrEqualTo(3);
		assertThat(byActor).isSortedAccordingTo((a, b) -> b.occurredAt().compareTo(a.occurredAt()));

		// Actor identity is resolved, not left as a bare id.
		assertThat(byActor.get(0).actorEmail()).isEqualTo("audit-search@acme.test");
		assertThat(byActor.get(0).actorRole()).isEqualTo("HR_ADMIN");

		// Narrow by entity type: exactly our three rows now.
		List<AuditEventResponse> byEntityType = auditService.search(actor.getId(), entityType, null, null, null);
		assertThat(byEntityType).hasSize(3);

		// Narrow further by action: exactly the two CREATE_THING rows.
		List<AuditEventResponse> byAction = auditService.search(actor.getId(), entityType, "CREATE_THING", null, null);
		assertThat(byAction).hasSize(2);
		assertThat(byAction).allMatch(row -> row.action().equals("CREATE_THING"));

		// A date range entirely before these writes excludes them all.
		Instant longAgo = Instant.now().minus(365, ChronoUnit.DAYS);
		List<AuditEventResponse> byDateRange = auditService.search(actor.getId(), entityType, null, longAgo.minusSeconds(60), longAgo);
		assertThat(byDateRange).isEmpty();

		// The export endpoint returns CSV attachment bytes for the exact same filter -- called
		// directly on the bean (bypassing MockMvc), so @PreAuthorize needs its own authentication,
		// not the servlet-filter-chain one a real request would carry.
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken("audit-search-test", null, "ROLE_HR_ADMIN"));
		ResponseEntity<byte[]> exported = auditController.export(actor.getId(), entityType, "CREATE_THING", null, null);
		assertThat(exported.getHeaders().getContentDisposition().getFilename()).isEqualTo("audit-log.csv");
		String csv = new String(exported.getBody(), StandardCharsets.UTF_8);
		assertThat(csv).startsWith("Occurred At,Actor Email,Actor Role,Action,Entity Type,Entity Id,IP");
		assertThat(csv.lines().count()).isEqualTo(3); // header + 2 CREATE_THING rows
		assertThat(csv).contains("CREATE_THING").doesNotContain("UPDATE_THING");
	}

}
