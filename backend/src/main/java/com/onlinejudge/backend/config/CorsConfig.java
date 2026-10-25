package com.onlinejudge.backend.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;

/**
 * CORS for the React frontend. Tokens travel in the Authorization header, never
 * cookies, so credentials mode stays off (PRD §16).
 *
 * <p>The SPA is served by the same edge that proxies {@code /api}, so its own requests are
 * same-origin in the browser's eyes even though the backend only ever sees plain HTTP from
 * the TLS-terminating proxy (Caddy or a tunnel). Scheme and port therefore cannot be used to
 * decide same-origin here: behind a tunnel the browser says {@code https://host} while the
 * backend sees {@code http}. We allow an origin whose <em>host</em> matches the host this
 * request was addressed to, and keep the explicit allow-list for a genuinely separate
 * frontend host. A browser cannot forge {@code Host} for another site, so this does not widen
 * cross-site access: a request with {@code Host: oj.example.com} was addressed to it.
 */
@Configuration
public class CorsConfig {

    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    private static final List<String> ALLOWED_HEADERS =
            List.of("Authorization", "Content-Type", "Idempotency-Key");

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        List<String> configuredOrigins = List.copyOf(properties.allowedOrigins());
        return request -> {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedMethods(ALLOWED_METHODS);
            configuration.setAllowedHeaders(ALLOWED_HEADERS);
            configuration.setMaxAge(3600L);

            List<String> origins = new ArrayList<>(configuredOrigins);
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            if (origin != null && isRequestHost(origin, request)) {
                origins.add(origin);
            }
            configuration.setAllowedOrigins(origins);
            return configuration;
        };
    }

    /** True when the origin names the same host this request was addressed to (any scheme/port). */
    static boolean isRequestHost(String origin, HttpServletRequest request) {
        String originHost = hostOf(origin);
        if (originHost == null) {
            return false;
        }
        if (originHost.equalsIgnoreCase(request.getServerName())) {
            return true;
        }
        String hostHeader = request.getHeader(HttpHeaders.HOST);
        return hostHeader != null && originHost.equalsIgnoreCase(hostOf("//" + hostHeader));
    }

    private static String hostOf(String value) {
        try {
            return URI.create(value).getHost();
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
