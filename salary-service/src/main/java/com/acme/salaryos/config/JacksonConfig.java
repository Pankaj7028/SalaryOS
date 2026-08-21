package com.acme.salaryos.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

/**
 * Every {@code BigDecimal} — every money amount, every ratio — serialises as a JSON STRING, never
 * a bare number (CLAUDE.md §6.1; {@code lib/money.ts}'s own contract: "amount is a STRING, not a
 * number... an IEEE-754 double cannot represent every such value exactly"). Without this, Jackson
 * 3's default writes a bare JSON number, which happens to round-trip through {@code
 * Intl.NumberFormat} without visibly breaking — until a consumer does a string operation on it
 * (P6.4's {@code <Delta>}, which strips a leading "-" with {@code .replace()}), at which point it
 * throws. Fixed at the source rather than in each consumer.
 */
@Configuration
public class JacksonConfig {

	@Bean
	public JsonMapperBuilderCustomizer bigDecimalAsStringCustomizer() {
		SimpleModule module = new SimpleModule();
		module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
		return builder -> builder.addModule(module);
	}

}
