package com.acme.salaryos.auth.repository;

import com.acme.salaryos.auth.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
}
