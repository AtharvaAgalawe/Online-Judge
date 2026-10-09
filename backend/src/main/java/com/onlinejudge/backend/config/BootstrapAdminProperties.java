package com.onlinejudge.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Dev-only bootstrap admin settings (AGENTS.md §7/§24: grouped config, secrets from the
 * environment). Active only under the {@code local} profile; a production start never
 * creates an admin even if {@code enabled} is true.
 *
 * @param enabled  whether to attempt creating the bootstrap admin at startup
 * @param username the admin username (blank disables creation)
 * @param email    the admin email; when blank, {@code <username>@localhost} is used
 * @param password the admin password (blank disables creation); never logged
 */
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record BootstrapAdminProperties(
        @DefaultValue("false") boolean enabled,
        String username,
        String email,
        String password) {
}
