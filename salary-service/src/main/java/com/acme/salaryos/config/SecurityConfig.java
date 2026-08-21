package com.acme.salaryos.config;

import com.acme.salaryos.auth.filter.SessionCookieAuthFilter;
import com.acme.salaryos.auth.repository.UserSessionRepository;
import com.acme.salaryos.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * CLAUDE.md §4: cookie-borne JWT ({@code sos_session}), double-submit CSRF ({@code sos_csrf}),
 * Argon2id. Stateless — {@code SessionCookieAuthFilter} is the only thing that ever authenticates
 * a request; there is no {@code HttpSession}.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtService jwtService;
	private final UserSessionRepository userSessionRepository;
	private final ObjectMapper objectMapper;

	@Bean
	CookieCsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieName("sos_csrf");
		repository.setHeaderName("X-CSRF-Token");
		return repository;
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository) throws Exception {
		return http
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository)
						// Plain (non-XOR'd) handler: sos_csrf holds the raw token, and the client
						// echoes that exact value back in X-CSRF-Token — the classic double-submit
						// pattern (CLAUDE.md §4.1). The default XorCsrfTokenRequestAttributeHandler
						// expects the BREACH-masked value instead, which a plain cookie echo fails.
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
						.ignoringRequestMatchers("/api/auth/login", "/api/auth/refresh"))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(
						new SessionCookieAuthFilter(jwtService, userSessionRepository),
						UsernamePasswordAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/auth/login", "/api/auth/refresh",
								"/actuator/health", "/actuator/health/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, authException) ->
								writeProblemDetail(response, HttpStatus.UNAUTHORIZED, "Authentication required"))
						.accessDeniedHandler((request, response, accessDeniedException) ->
								writeProblemDetail(response, HttpStatus.FORBIDDEN, "Access denied")))
				.build();
	}

	private void writeProblemDetail(
			jakarta.servlet.http.HttpServletResponse response, HttpStatus status, String detail) throws java.io.IOException {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getWriter(), problemDetail);
	}

	/**
	 * {@code argon2} is the default id (CLAUDE.md §4.2): OWASP baseline — memory 19456 KiB,
	 * iterations 2, parallelism 1. The {@code {argon2}} prefix lets the algorithm rotate later
	 * without a data migration.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		Argon2PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
		return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2));
	}

}
