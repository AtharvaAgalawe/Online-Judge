package com.onlinejudge.backend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Explicit CORS allow-list (PRD §16: no wildcard, credentials mode off). */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
}
