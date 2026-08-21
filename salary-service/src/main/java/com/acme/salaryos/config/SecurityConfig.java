package com.acme.salaryos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * P0.2 placeholder so the liveness probe is reachable before auth exists.
 *
 * <p>Replaced wholesale at P2.1 by the real chain in {@code CLAUDE.md §4}: cookie-borne JWT,
 * {@code SessionCookieAuthFilter}, Argon2id, and double-submit CSRF. Until then everything except
 * the health endpoint requires authentication, so no endpoint is accidentally left open.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.anyRequest().authenticated())
				.httpBasic(Customizer.withDefaults())
				.build();
	}

}
