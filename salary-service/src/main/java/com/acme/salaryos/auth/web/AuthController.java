package com.acme.salaryos.auth.web;

import com.acme.salaryos.auth.dto.LoginRequest;
import com.acme.salaryos.auth.dto.MeResponse;
import com.acme.salaryos.auth.filter.SessionCookieAuthFilter;
import com.acme.salaryos.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** FR-1.1–1.4: login, logout, refresh (with rotation), and the current user. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private static final String REFRESH_COOKIE_NAME = "sos_refresh";
	private static final String REFRESH_COOKIE_PATH = "/api/auth";

	private final AuthService authService;
	private final CsrfTokenRepository csrfTokenRepository;
	private final Duration sessionTtl;

	public AuthController(
			AuthService authService,
			CsrfTokenRepository csrfTokenRepository,
			@Value("${app.session.ttl}") Duration sessionTtl) {
		this.authService = authService;
		this.csrfTokenRepository = csrfTokenRepository;
		this.sessionTtl = sessionTtl;
	}

	@PostMapping("/login")
	public void login(
			@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		AuthService.IssuedSession session = authService.login(
				request.email(), request.password(), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
		setSessionCookies(httpResponse, session);
		issueCsrfCookie(httpRequest, httpResponse);
	}

	@PostMapping("/logout")
	public void logout(
			@CookieValue(name = SessionCookieAuthFilter.SESSION_COOKIE_NAME, required = false) String sessionToken,
			HttpServletResponse httpResponse) {
		authService.logout(sessionToken);
		clearSessionCookies(httpResponse);
	}

	@PostMapping("/refresh")
	public void refresh(
			@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		AuthService.IssuedSession session = authService.refresh(
				refreshToken, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
		setSessionCookies(httpResponse, session);
	}

	@GetMapping("/me")
	public MeResponse me(Authentication authentication) {
		return authService.me((UUID) authentication.getPrincipal());
	}

	private void setSessionCookies(HttpServletResponse response, AuthService.IssuedSession session) {
		addCookie(response, ResponseCookie.from(SessionCookieAuthFilter.SESSION_COOKIE_NAME, session.accessToken())
				.httpOnly(true).secure(true).sameSite("Lax").path("/")
				.maxAge(sessionTtl)
				.build());
		addCookie(response, ResponseCookie.from(REFRESH_COOKIE_NAME, session.refreshToken())
				.httpOnly(true).secure(true).sameSite("Lax").path(REFRESH_COOKIE_PATH)
				.maxAge(Duration.between(Instant.now(), session.refreshTokenExpiresAt()))
				.build());
	}

	private void clearSessionCookies(HttpServletResponse response) {
		addCookie(response, ResponseCookie.from(SessionCookieAuthFilter.SESSION_COOKIE_NAME, "")
				.httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(0).build());
		addCookie(response, ResponseCookie.from(REFRESH_COOKIE_NAME, "")
				.httpOnly(true).secure(true).sameSite("Lax").path(REFRESH_COOKIE_PATH).maxAge(0).build());
	}

	private void addCookie(HttpServletResponse response, ResponseCookie cookie) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	/** Forces the (otherwise lazily-generated) CSRF token to be written as the readable {@code sos_csrf} cookie. */
	private void issueCsrfCookie(HttpServletRequest request, HttpServletResponse response) {
		CsrfToken token = csrfTokenRepository.generateToken(request);
		csrfTokenRepository.saveToken(token, request, response);
	}

}
