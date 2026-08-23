package com.acme.salaryos.savedview.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A named, replayable question — the "saved-question library" the scope contract promised as the
 * substitute for the excluded free-text pay assistant.
 *
 * <p>Deliberately dumb: it stores a route and a query string, not a query. Every guardrail — cohort
 * suppression, RBAC, demographic isolation — stays where it already is, in the endpoints and in
 * SQL. Replaying a saved view is exactly the same request the user could have made by hand, so a
 * view saved by an HR Admin and opened by an Auditor returns what the *Auditor* is allowed to see,
 * with no extra logic needed here.
 *
 * <p>No JPA relationship to {@code User} — {@code ownerId} is a plain column. A relationship would
 * let a fetch graph drag a user (and its password hash) into a saved-view response, which is the
 * same class of accident {@code employee_demographics} is kept away from in CLAUDE.md §6.6.
 */
@Entity
@Table(name = "saved_views")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SavedView {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID ownerId;

	@Setter
	private String name;

	@Setter
	private String route;

	@Setter
	private String queryString;

	@Setter
	private boolean shared;

	@CreationTimestamp
	private Instant createdAt;

}
