package com.onlinejudge.backend.security;

import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * JWT (HS256) access tokens. The signing key is loaded from configuration only
 * (AGENTS.md §24) and must be at least 256 bits.
 */
@Configuration
public class JwtConfig {

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(hmacKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        return NimbusJwtDecoder.withSecretKey(hmacKey(properties))
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                .build();
    }

    /** Maps the token's {@code roles} claim (e.g. ROLE_ADMIN) to Spring authorities. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return List.of();
            }
            return roles.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
        });
        return converter;
    }

    private static SecretKey hmacKey(JwtProperties properties) {
        byte[] decoded = Base64.getDecoder().decode(properties.secret());
        if (decoded.length < 32) {
            throw new IllegalStateException(
                    "JWT signing key must decode to at least 32 bytes (256 bits) for HS256");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }
}
