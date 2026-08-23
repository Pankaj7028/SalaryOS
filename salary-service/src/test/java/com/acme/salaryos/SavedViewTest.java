package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.savedview.dto.SaveViewRequest;
import com.acme.salaryos.savedview.dto.SavedViewResponse;
import com.acme.salaryos.savedview.service.SavedViewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P10.3's Verify clause. The shared Testcontainers container accumulates rows from every other
 * test class, so every assertion here is scoped to this test's own freshly-created users — never
 * an unscoped count (the discipline {@code EmployeeListPaginationTest} established after being
 * bitten by exactly that).
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class SavedViewTest {

	@Autowired
	private SavedViewService savedViewService;
	@Autowired
	private UserRepository userRepository;

	private UUID newUser(String label) {
		User user = userRepository.saveAndFlush(User.builder()
				.email("savedview-" + label + "-" + UUID.randomUUID() + "@acme.test")
				.fullName("Saved View " + label)
				.passwordHash("{noop}irrelevant")
				.role("HR_MANAGER")
				.build());
		return user.getId();
	}

	private static SaveViewRequest request(String name, boolean shared) {
		return new SaveViewRequest(name, "/employees", "bandStatus=BELOW_MIN&country=DE", shared);
	}

	@Test
	void savesAndReplaysTheExactFilterSet() {
		UUID owner = newUser("owner");

		SavedViewResponse saved = savedViewService.save(owner, request("Below band, Germany", false));

		assertThat(saved.route()).isEqualTo("/employees");
		assertThat(saved.queryString()).isEqualTo("bandStatus=BELOW_MIN&country=DE");
		assertThat(saved.ownedByMe()).isTrue();
		assertThat(saved.ownerName()).isEqualTo("Saved View owner");
	}

	/**
	 * A shared view is visible to another user; an unshared one is not. This is the whole access
	 * model — there is no other rule.
	 */
	@Test
	void sharedViewsReachOtherUsersAndUnsharedOnesDoNot() {
		UUID owner = newUser("sharer");
		UUID other = newUser("colleague");

		savedViewService.save(owner, request("Shared question", true));
		savedViewService.save(owner, request("Private question", false));

		List<String> visibleToOther = savedViewService.list(other).stream()
				.filter(view -> view.ownerName().equals("Saved View sharer"))
				.map(SavedViewResponse::name)
				.toList();

		assertThat(visibleToOther).containsExactly("Shared question");
	}

	/** A colleague's shared view is theirs, not mine — the picker has to be able to say so. */
	@Test
	void aSharedViewIsNotOwnedByTheViewer() {
		UUID owner = newUser("author");
		UUID other = newUser("reader");
		savedViewService.save(owner, request("Someone else's", true));

		SavedViewResponse seen = savedViewService.list(other).stream()
				.filter(view -> view.name().equals("Someone else's"))
				.findFirst()
				.orElseThrow();

		assertThat(seen.ownedByMe()).isFalse();
		assertThat(seen.ownerName()).isEqualTo("Saved View author");
	}

	/** Re-saving a name replaces, per V14's unique constraint — never a second indistinguishable row. */
	@Test
	void reSavingTheSameNameUpdatesRatherThanDuplicating() {
		UUID owner = newUser("reviser");
		savedViewService.save(owner, request("Same name", false));

		savedViewService.save(owner, new SaveViewRequest("Same name", "/bands", "country=FR", true));

		List<SavedViewResponse> mine = savedViewService.list(owner).stream()
				.filter(SavedViewResponse::ownedByMe)
				.toList();

		assertThat(mine).hasSize(1);
		assertThat(mine.get(0).route()).isEqualTo("/bands");
		assertThat(mine.get(0).queryString()).isEqualTo("country=FR");
		assertThat(mine.get(0).shared()).isTrue();
	}

	/**
	 * A non-owner deleting gets 404, not 403 — a 403 would confirm the id names a real view
	 * belonging to someone else.
	 */
	@Test
	void onlyTheOwnerCanDeleteAndANonOwnerCannotTellItExists() {
		UUID owner = newUser("keeper");
		UUID intruder = newUser("intruder");
		SavedViewResponse saved = savedViewService.save(owner, request("Not yours", true));

		assertThatThrownBy(() -> savedViewService.delete(intruder, saved.id()))
				.isInstanceOf(NoSuchElementException.class);

		assertThat(savedViewService.list(owner)).extracting(SavedViewResponse::id).contains(saved.id());

		savedViewService.delete(owner, saved.id());
		assertThat(savedViewService.list(owner)).extracting(SavedViewResponse::id).doesNotContain(saved.id());
	}

	/** A view with no filters ("all employees") is legitimate — null normalises to empty, not NPE. */
	@Test
	void aViewWithNoFiltersIsValid() {
		UUID owner = newUser("unfiltered");

		SavedViewResponse saved = savedViewService.save(
				owner, new SaveViewRequest("Everyone", "/employees", null, false));

		assertThat(saved.queryString()).isEmpty();
	}

}
