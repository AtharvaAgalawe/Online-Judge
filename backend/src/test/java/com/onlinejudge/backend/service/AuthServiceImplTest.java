package com.onlinejudge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.onlinejudge.backend.api.dto.AuthTokensResponse;
import com.onlinejudge.backend.api.dto.LoginRequest;
import com.onlinejudge.backend.api.dto.RefreshTokenRequest;
import com.onlinejudge.backend.api.dto.RegisterRequest;
import com.onlinejudge.backend.domain.RefreshToken;
import com.onlinejudge.backend.exception.DuplicateResourceException;
import com.onlinejudge.backend.exception.InvalidRefreshTokenException;
import com.onlinejudge.backend.repository.RefreshTokenRepository;
import com.onlinejudge.backend.repository.RoleRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.backend.security.JwtProperties;
import com.onlinejudge.backend.security.JwtTokenProvider;
import com.onlinejudge.common.entity.Role;
import com.onlinejudge.common.entity.User;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
            "dGVzdC1vbmx5LWp3dC1zZWNyZXQtMDEyMzQ1Njc4OWFiY2RlZg==",
            Duration.ofMinutes(15), Duration.ofDays(7));

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    private PasswordEncoder passwordEncoder;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        // Low cost factor for test speed; production bean uses 12 (PRD §16).
        passwordEncoder = new BCryptPasswordEncoder(4);
        authService = new AuthServiceImpl(userRepository, roleRepository, refreshTokenRepository,
                passwordEncoder, tokenProvider, JWT_PROPERTIES);
    }

    @Test
    void registerCreatesUserWithUserRoleAndEncodedPassword() {
        when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(new Role("ROLE_USER")));

        var response = authService.register(new RegisterRequest("alice", "alice@example.com", "password123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRoles()).extracting(Role::getName).containsExactly("ROLE_USER");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getValue().getPasswordHash())).isTrue();
        assertThat(response.username()).isEqualTo("alice");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "alice@example.com", "password123")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "alice@example.com", "password123")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void loginRejectsUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "password123")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = userWithPassword("password123");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginIssuesTokensAndPersistsOnlyTheRefreshTokenHash() {
        User user = userWithPassword("password123");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(tokenProvider.createAccessToken(user)).thenReturn("access-token");
        when(tokenProvider.issueRefreshToken())
                .thenReturn(new JwtTokenProvider.IssuedRefreshToken("raw-refresh", "hashed-refresh"));
        when(tokenProvider.accessTokenTtlSeconds()).thenReturn(900L);

        AuthTokensResponse response = authService.login(new LoginRequest("alice", "password123"));

        ArgumentCaptor<RefreshToken> stored = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(stored.capture());
        assertThat(stored.getValue().getTokenHash()).isEqualTo("hashed-refresh");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh");
        assertThat(response.expiresIn()).isEqualTo(900L);
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("nope")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshRejectsRevokedToken() {
        RefreshToken token = new RefreshToken(userWithPassword("password123"), "hash", Duration.ofDays(7));
        token.revoke();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("raw")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshRejectsExpiredToken() {
        RefreshToken token = new RefreshToken(userWithPassword("password123"), "hash", Duration.ofSeconds(-1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("raw")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesAllActiveTokensOfTheUser() {
        User user = userWithPassword("password123");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        authService.logout("alice");

        verify(refreshTokenRepository).revokeAllActiveForUser(any(), any());
    }

    private User userWithPassword(String rawPassword) {
        return new User("alice", "alice@example.com", passwordEncoder.encode(rawPassword));
    }
}
