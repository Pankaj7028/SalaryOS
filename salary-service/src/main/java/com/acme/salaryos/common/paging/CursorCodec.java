package com.acme.salaryos.common.paging;

import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Encodes a {@link Cursor} as one opaque, URL-safe string. CLAUDE.md §9: list/filter/sort state
 * lives in the URL — this is the one piece of it a client must never parse or construct itself,
 * since it names internal sort-key values, not a user-chosen filter.
 */
@Component
public class CursorCodec {

	public String encode(Cursor cursor) {
		String queryString = cursor.keys().entrySet().stream()
				.map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
				.collect(Collectors.joining("&"));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(queryString.getBytes(StandardCharsets.UTF_8));
	}

	public Cursor decode(String encoded) {
		try {
			String queryString = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			Map<String, String> keys = new LinkedHashMap<>();
			for (String pair : queryString.split("&")) {
				String[] parts = pair.split("=", 2);
				keys.put(urlDecode(parts[0]), urlDecode(parts[1]));
			}
			return new Cursor(keys);
		}
		catch (RuntimeException malformed) {
			throw new InvalidCursorException(encoded);
		}
	}

	private String urlEncode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private String urlDecode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

}
