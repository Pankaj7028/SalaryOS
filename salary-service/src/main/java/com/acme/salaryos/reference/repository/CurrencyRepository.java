package com.acme.salaryos.reference.repository;

import com.acme.salaryos.reference.domain.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<Currency, String> {
}
