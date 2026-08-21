package com.acme.salaryos.reference.repository;

import com.acme.salaryos.reference.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {
}
