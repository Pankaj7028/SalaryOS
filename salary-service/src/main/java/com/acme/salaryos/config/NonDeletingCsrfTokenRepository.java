package com.acme.salaryos.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * Wraps the real {@code sos_csrf} cookie repository and refuses to let the security filter
 * chain delete the cookie via an implicit {@code saveToken(null, ...)} — this app's CSRF cookie
 * lifecycle is owned explicitly by {@code AuthController#login} (issues it) and never anywhere
 * else; nothing in this codebase's normal request handling should ever need to clear it.
 *
 * <p>Found during a post-P8 feature build (P8's own QA pass had exercised every screen, but
 * always via a single request per browser session — never the ordinary "sign in, browse, then
 * submit a form a few navigations later" sequence a real user follows): on every plain
 * authenticated {@code GET} — {@code /api/auth/me}, {@code /api/employees}, anything —
 * Spring Security 7's {@code CsrfFilter} was calling {@code saveToken(null, ...)} on the
 * delegate, which {@code CookieCsrfTokenRepository} turns into an explicit cookie deletion
 * ({@code Max-Age=0}). The token the client already had was valid; nothing needed regenerating.
 * The result: the very first navigation after signing in silently deleted the CSRF cookie, and
 * every mutation attempted afterward — propose a change, create a band, create an employee,
 * anything — 403'd with "Access denied," indistinguishable from an actual RBAC failure. This
 * went unnoticed through every prior build step because every prior verification exercised
 * mutations either directly via {@code curl} (skipping the browser's real cookie lifecycle
 * entirely) or as the very first request in a fresh session (before any GET had a chance to
 * trigger it) — never both a GET and a POST in the same session, which is how every real user
 * actually uses the app.
 *
 * <p>{@code loadToken}/{@code generateToken} are untouched — reading the existing cookie and
 * minting a fresh one when genuinely absent both still work exactly as {@code
 * CookieCsrfTokenRepository} implements them. Only the destructive no-op-that-isn't is disarmed.
 */
public class NonDeletingCsrfTokenRepository implements CsrfTokenRepository {

	private final CsrfTokenRepository delegate;

	public NonDeletingCsrfTokenRepository(CsrfTokenRepository delegate) {
		this.delegate = delegate;
	}

	@Override
	public CsrfToken generateToken(HttpServletRequest request) {
		return delegate.generateToken(request);
	}

	@Override
	public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
		if (token == null) {
			return;
		}
		delegate.saveToken(token, request, response);
	}

	@Override
	public CsrfToken loadToken(HttpServletRequest request) {
		return delegate.loadToken(request);
	}

}
