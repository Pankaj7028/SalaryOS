package com.acme.salaryos.savedview.web;

import com.acme.salaryos.savedview.dto.SaveViewRequest;
import com.acme.salaryos.savedview.dto.SavedViewResponse;
import com.acme.salaryos.savedview.service.SavedViewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * P10.3. Every role may save and share a view.
 *
 * <p>That is not a widening of access. A saved view carries no data — only a route and a query
 * string — and replaying one issues the same request the user could have typed, answered by the
 * endpoint that already enforces their own role's limits. An Auditor opening a view an HR Admin
 * saved gets the Auditor's answer. There is deliberately no {@code HR_ADMIN}-only variant: a
 * personal bookmark over data you can already reach is not a capability in CLAUDE.md §7's sense,
 * which is why the RBAC table has no row for it.
 */
@Slf4j
@RestController
@RequestMapping("/api/saved-views")
@RequiredArgsConstructor
public class SavedViewController {

	private final SavedViewService savedViewService;

	@GetMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public List<SavedViewResponse> list(@AuthenticationPrincipal UUID currentUserId) {
		return savedViewService.list(currentUserId);
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public SavedViewResponse save(
			@AuthenticationPrincipal UUID currentUserId,
			@Valid @RequestBody SaveViewRequest request) {
		return savedViewService.save(currentUserId, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public void delete(@AuthenticationPrincipal UUID currentUserId, @PathVariable UUID id) {
		savedViewService.delete(currentUserId, id);
	}

}
