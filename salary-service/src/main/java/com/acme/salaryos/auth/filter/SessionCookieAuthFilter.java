package com.acme.salaryos.auth.filter;

import com.acme.salaryos.auth.repository.UserSessionRepository;
import com.acme.salaryos.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads {@code sos_session}, validates it locally (signature/claims/expiry), and checks the
 * {@code jti} against {@code user_sessions} for revocation — CLAUDE.md §4.1. One authority, no
 * hierarchy: {@code ROLE_<role>} straight from the token's {@code role} claim.
 *
 * <p>Any failure — missing cookie, bad signature, expired, unknown or revoked {@code jti} —
 * leaves the request unauthenticated rather than throwing; {@code anyRequest().authenticated()}
 * in {@code SecurityConfig} is what turns that into a 401.
 */
@RequiredArgsConstructor
public class SessionCookieAuthFilter extends OncePerRequestFilter {

	public static final String SESSION_COOKIE_NAME = "sos_session";

	private final JwtService jwtService;
	private final UserSessionRepository userSessionRepository;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		String token = readCookie(request, SESSION_COOKIE_NAME);
		if (token != null) {
			authenticate(token).ifPresent(SecurityContextHolder.getContext()::setAuthentication);
		}
		filterChain.doFilter(request, response);
	}

	private Optional<UsernamePasswordAuthenticationToken> authenticate(String token) {
		try {
			Claims claims = jwtService.validate(token);
			UUID jti = UUID.fromString(claims.getId());

			boolean sessionIsValid = userSessionRepository.findByJti(jti)
					.filter(session -> session.getRevokedAt() == null)
					.isPresent();
			if (!sessionIsValid) {
				return Optional.empty();
			}

			UUID userId = UUID.fromString(claims.getSubject());
			String role = claims.get("role", String.class);
			List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
			return Optional.of(new UsernamePasswordAuthenticationToken(userId, null, authorities));
		}
		catch (JwtException | IllegalArgumentException invalidToken) {
			return Optional.empty();
		}
	}

	private String readCookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		return Arrays.stream(cookies)
				.filter(cookie -> name.equals(cookie.getName()))
				.map(Cookie::getValue)
				.findFirst()
				.orElse(null);
	}

}
