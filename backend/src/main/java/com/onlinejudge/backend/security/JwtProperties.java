package com.onlinejudge.backend.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWT settings (AGENTS.md §7: grouped configuration instead of scattered @Value).
 *
 * @param secret        base64-encoded HMAC key (>= 32 bytes decoded); from the environment, never committed
 * @param accessTokenTtl access-token lifetime (PRD §16: 15 min)
 * @param refreshTokenTtl refresh-token lifetime (PRD §16: 7 days)
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl) {
}
