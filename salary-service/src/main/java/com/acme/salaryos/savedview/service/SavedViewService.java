package com.acme.salaryos.savedview.service;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.savedview.domain.SavedView;
import com.acme.salaryos.savedview.dto.SaveViewRequest;
import com.acme.salaryos.savedview.dto.SavedViewResponse;
import com.acme.salaryos.savedview.repository.SavedViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * P10.3. The saved-question library promised by {@code requirements-one-pager.md} in the same
 * sentence that excluded a free-text pay assistant.
 *
 * <p>This service deliberately holds no query logic. A saved view is a route plus a query string;
 * replaying one issues the identical request the user could have typed, against endpoints that
 * already enforce RBAC, cohort suppression and demographic isolation. That is the whole reason the
 * contract offered this as the safe substitute — <em>"the same questions, answered by queries that
 * can be audited"</em> — and it only stays true as long as nothing here starts interpreting the
 * query string.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavedViewService {

	private final SavedViewRepository savedViewRepository;
	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public List<SavedViewResponse> list(UUID currentUserId) {
		List<SavedView> views = savedViewRepository.findByOwnerIdOrSharedTrueOrderByNameAsc(currentUserId);
		Map<UUID, String> ownerNames = ownerNames(views);

		return views.stream()
				.map(view -> toResponse(view, currentUserId, ownerNames))
				// Own views first, then shared ones, each alphabetically — a picker where your own
				// saved questions sink below a colleague's is a picker you stop using.
				.sorted(Comparator.comparing(SavedViewResponse::ownedByMe).reversed()
						.thenComparing(SavedViewResponse::name, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	/**
	 * Save, or overwrite this user's view of the same name. Re-saving under an existing name is an
	 * update rather than a second row, matching the unique constraint in {@code V14} — two views
	 * called "Below band, Germany" that a picker cannot tell apart is worse than losing the older
	 * one, and the user's intent when reusing a name is plainly "replace".
	 */
	@Transactional
	public SavedViewResponse save(UUID currentUserId, SaveViewRequest request) {
		String name = request.name().trim();

		SavedView view = savedViewRepository.findByOwnerIdAndName(currentUserId, name)
				.map(existing -> {
					existing.setRoute(request.route());
					existing.setQueryString(request.queryString());
					existing.setShared(request.shared());
					return existing;
				})
				.orElseGet(() -> SavedView.builder()
						.ownerId(currentUserId)
						.name(name)
						.route(request.route())
						.queryString(request.queryString())
						.shared(request.shared())
						.build());

		SavedView saved = savedViewRepository.saveAndFlush(view);
		log.info("Saved view '{}' for user {} (shared={})", name, currentUserId, request.shared());
		return toResponse(saved, currentUserId, ownerNames(List.of(saved)));
	}

	/**
	 * Only the owner may delete. A non-owner gets the same 404 as a view that does not exist —
	 * a 403 would confirm that someone else's view by that id is real, which is a small leak but a
	 * free one to avoid.
	 */
	@Transactional
	public void delete(UUID currentUserId, UUID id) {
		SavedView view = savedViewRepository.findById(id)
				.filter(candidate -> candidate.getOwnerId().equals(currentUserId))
				.orElseThrow(() -> new NoSuchElementException("Saved view not found."));

		savedViewRepository.delete(view);
		log.info("Deleted saved view {} for user {}", id, currentUserId);
	}

	private Map<UUID, String> ownerNames(List<SavedView> views) {
		Set<UUID> ownerIds = views.stream().map(SavedView::getOwnerId).collect(Collectors.toSet());
		if (ownerIds.isEmpty()) {
			return Map.of();
		}
		return userRepository.findAllById(ownerIds).stream()
				.collect(Collectors.toMap(User::getId, User::getFullName));
	}

	private SavedViewResponse toResponse(SavedView view, UUID currentUserId, Map<UUID, String> ownerNames) {
		boolean ownedByMe = view.getOwnerId().equals(currentUserId);
		return new SavedViewResponse(
				view.getId(),
				view.getName(),
				view.getRoute(),
				view.getQueryString(),
				view.isShared(),
				ownedByMe,
				ownerNames.getOrDefault(view.getOwnerId(), "Unknown"),
				view.getCreatedAt());
	}

}
