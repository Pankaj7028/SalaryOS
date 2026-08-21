package com.acme.salaryos.band.repository;

import com.acme.salaryos.band.domain.SalaryBand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface SalaryBandRepository extends JpaRepository<SalaryBand, UUID> {

	/** The version of this (level × country) band in force on {@code asAt} — bands are effective-dated, never mutated in place (FR-4.5). */
	@Query("SELECT b FROM SalaryBand b WHERE b.jobLevelId = :jobLevelId AND b.countryCode = :countryCode "
			+ "AND b.effectiveFrom <= :asAt AND (b.effectiveTo IS NULL OR b.effectiveTo > :asAt)")
	Optional<SalaryBand> findEffective(
			@Param("jobLevelId") UUID jobLevelId, @Param("countryCode") String countryCode, @Param("asAt") LocalDate asAt);

	/** The current in-force version — not necessarily active *today* if it was versioned with a future effective date, but the one {@code create}/{@code update} must close before opening a successor. */
	Optional<SalaryBand> findByJobLevelIdAndCountryCodeAndEffectiveToIsNull(UUID jobLevelId, String countryCode);

}
