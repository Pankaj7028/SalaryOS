package com.acme.salaryos.auth.web;

import com.acme.salaryos.auth.dto.CreateUserRequest;
import com.acme.salaryos.auth.dto.ResetTokenResponse;
import com.acme.salaryos.auth.dto.UpdateUserRequest;
import com.acme.salaryos.auth.dto.UserSummaryResponse;
import com.acme.salaryos.auth.service.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** P8.1 (Technical-Requirements.md §5, FR-1.5/FR-1.6). Manage users & roles: HR Admin only. */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

	private final UserAdminService userAdminService;

	public UserAdminController(UserAdminService userAdminService) {
		this.userAdminService = userAdminService;
	}

	@GetMapping
	@PreAuthorize("hasRole('HR_ADMIN')")
	public List<UserSummaryResponse> list() {
		return userAdminService.list();
	}

	@PostMapping
	@PreAuthorize("hasRole('HR_ADMIN')")
	public UserSummaryResponse create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal UUID currentUserId) {
		return userAdminService.create(request, currentUserId);
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasRole('HR_ADMIN')")
	public UserSummaryResponse update(
			@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request, @AuthenticationPrincipal UUID currentUserId) {
		return userAdminService.update(id, request, currentUserId);
	}

	@PostMapping("/{id}/reset-token")
	@PreAuthorize("hasRole('HR_ADMIN')")
	public ResetTokenResponse issueResetToken(@PathVariable UUID id, @AuthenticationPrincipal UUID currentUserId) {
		return userAdminService.issueResetToken(id, currentUserId);
	}

}
