package com.acme.salaryos.reference.repository;

import com.acme.salaryos.reference.domain.ReasonCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReasonCodeRepository extends JpaRepository<ReasonCode, String> {
}
