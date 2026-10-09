package com.onlinejudge.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.onlinejudge.backend.repository.RoleRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Role;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.AppRole;

/**
 * Creates a single bootstrap admin on startup for local development, so a developer does
 * not have to hand-run SQL to get an admin account.
 *
 * <p><strong>Security:</strong> restricted to the {@code local} profile, so it can never
 * run in {@code test}/{@code docker}/{@code prod}. Credentials come from the environment
 * ({@code BOOTSTRAP_ADMIN_*}); with no password configured it does nothing rather than
 * falling back to a weak default. It is idempotent and never modifies an existing account
 * — if the username is already taken it logs and leaves it untouched. The password is
 * never logged.
 */
@Component
@Profile("local")
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(BootstrapAdminProperties properties, UserRepository userRepository,
                                     RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (!StringUtils.hasText(properties.username()) || !StringUtils.hasText(properties.password())) {
            log.warn("Bootstrap admin is enabled but username/password are not set; skipping creation");
            return;
        }

        String username = properties.username().trim();
        if (userRepository.findByUsername(username).isPresent()) {
            log.info("Bootstrap admin '{}' already exists; leaving it unchanged", username);
            return;
        }

        Role adminRole = roleRepository.findByName(AppRole.ROLE_ADMIN.name())
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN role is not seeded"));
        String email = StringUtils.hasText(properties.email())
                ? properties.email().trim()
                : username + "@localhost";

        User admin = new User(username, email, passwordEncoder.encode(properties.password()));
        admin.grantRole(adminRole);
        userRepository.saveAndFlush(admin);
        log.info("Created bootstrap admin '{}'", username);
    }
}
