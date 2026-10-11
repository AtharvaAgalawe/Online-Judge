package com.onlinejudge.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.onlinejudge.backend.repository.RoleRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.common.entity.Role;
import com.onlinejudge.common.entity.User;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void disabledDoesNothing() throws Exception {
        BootstrapAdminInitializer initializer = initializer(false, "admin", "secret123", "a@x.com");

        initializer.run(null);

        verifyNoInteractions(userRepository, roleRepository, passwordEncoder);
    }

    @Test
    void enabledWithoutCredentialsIsSkipped() throws Exception {
        BootstrapAdminInitializer initializer = initializer(true, "", "", "");

        initializer.run(null);

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(roleRepository, passwordEncoder);
    }

    @Test
    void enabledWithoutPasswordIsSkipped() throws Exception {
        BootstrapAdminInitializer initializer = initializer(true, "admin", "", "a@x.com");

        initializer.run(null);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createsAdminWhenAbsent() throws Exception {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(new Role("ROLE_ADMIN")));
        when(passwordEncoder.encode("secret123")).thenReturn("HASHED");
        BootstrapAdminInitializer initializer = initializer(true, "admin", "secret123", "");

        initializer.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.getUsername()).isEqualTo("admin");
        assertThat(user.getEmail()).isEqualTo("admin@localhost");
        assertThat(user.getPasswordHash()).isEqualTo("HASHED");
        assertThat(user.getRoles()).extracting(Role::getName).containsExactly("ROLE_ADMIN");
    }

    @Test
    void existingUserIsLeftUnchanged() throws Exception {
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(new User("admin", "a@x.com", "EXISTING")));
        BootstrapAdminInitializer initializer = initializer(true, "admin", "secret123", "a@x.com");

        initializer.run(null);

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(roleRepository, passwordEncoder);
    }

    private BootstrapAdminInitializer initializer(boolean enabled, String username, String password, String email) {
        return new BootstrapAdminInitializer(
                new BootstrapAdminProperties(enabled, username, email, password),
                userRepository, roleRepository, passwordEncoder);
    }
}
