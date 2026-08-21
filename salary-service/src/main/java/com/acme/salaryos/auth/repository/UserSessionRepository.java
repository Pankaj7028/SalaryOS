package com.acme.salaryos.auth.repository;

import com.acme.salaryos.auth.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

	Optional<UserSession> findByJti(UUID jti);

	Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

	List<UserSession> findByFamilyIdAndRevokedAtIsNull(UUID familyId);

}
