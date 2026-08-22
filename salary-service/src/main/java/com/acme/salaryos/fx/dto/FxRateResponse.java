package com.acme.salaryos.fx.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FxRateResponse(UUID id, LocalDate rateMonth, String baseCurrency, String quoteCurrency, BigDecimal rate) {
}
