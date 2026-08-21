package com.acme.salaryos.auth.filter;

import com.acme.salaryos.auth.domain.UserSession;
import com.acme.salaryos.auth.repository.UserSessionRepository;
import com.acme.salaryos.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2.1: proves the filter checks {@code jti} against {@code user_sessions} for revocation, and
 * leaves the request unauthenticated on any failure rather than throwing.
 */
@ExtendWith(MockitoExtension.class)
class SessionCookieAuthFilterTest {

	@Mock
	private JwtService jwtService;
	@Mock
	private UserSessionRepository userSessionRepository;
	@Mock
	private HttpServletRequest request;
	@Mock
	private HttpServletResponse response;
	@Mock
	private FilterChain filterChain;
	@Mock
	private Claims claims;

	private final UUID userId = UUID.randomUUID();
	private final UUID jti = UUID.randomUUID();

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void validUnrevokedSessionAuthenticatesWithRoleAuthority() throws Exception {
		givenCookie("valid-token");
		when(jwtService.validate("valid-token")).thenReturn(claims);
		when(claims.getId()).thenReturn(jti.toString());
		when(claims.getSubject()).thenReturn(userId.toString());
		when(claims.get("role", String.class)).thenReturn("HR_ADMIN");
		when(userSessionRepository.findByJti(jti)).thenReturn(Optional.of(
				UserSession.builder().id(UUID.randomUUID()).userId(userId).jti(jti).build()));

		newFilter().doFilterInternal(request, response, filterChain);

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assertThat(authentication).isNotNull();
		assertThat(authentication.getPrincipal()).isEqualTo(userId);
		assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_HR_ADMIN");
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void revokedSessionDoesNotAuthenticate() throws Exception {
		givenCookie("revoked-token");
		when(jwtService.validate("revoked-token")).thenReturn(claims);
		when(claims.getId()).thenReturn(jti.toString());
		when(userSessionRepository.findByJti(jti)).thenReturn(Optional.of(
				UserSession.builder().id(UUID.randomUUID()).userId(userId).jti(jti)
						.revokedAt(Instant.now()).build()));

		newFilter().doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void unknownJtiDoesNotAuthenticate() throws Exception {
		givenCookie("unknown-session-token");
		when(jwtService.validate("unknown-session-token")).thenReturn(claims);
		when(claims.getId()).thenReturn(jti.toString());
		when(userSessionRepository.findByJti(jti)).thenReturn(Optional.empty());

		newFilter().doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void invalidTokenDoesNotAuthenticateAndDoesNotThrow() throws Exception {
		givenCookie("garbage-token");
		when(jwtService.validate("garbage-token")).thenThrow(new JwtException("bad signature"));

		newFilter().doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void noCookieAtAllDoesNotAuthenticate() throws Exception {
		when(request.getCookies()).thenReturn(null);

		newFilter().doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	private void givenCookie(String value) {
		when(request.getCookies()).thenReturn(new Cookie[] { new Cookie(SessionCookieAuthFilter.SESSION_COOKIE_NAME, value) });
	}

	private SessionCookieAuthFilter newFilter() {
		return new SessionCookieAuthFilter(jwtService, userSessionRepository);
	}

}
