package com.acme.salaryos.savedview.repository;

import com.acme.salaryos.savedview.domain.SavedView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Derived queries only — nothing here needs native SQL, so nothing here can forget its schema. */
public interface SavedViewRepository extends JpaRepository<SavedView, UUID> {

	/** The picker's read: everything this user owns, plus everything anyone has shared. */
	List<SavedView> findByOwnerIdOrSharedTrueOrderByNameAsc(UUID ownerId);

	Optional<SavedView> findByOwnerIdAndName(UUID ownerId, String name);

}
