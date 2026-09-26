package com.onlinejudge.backend.service;

import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinejudge.backend.api.dto.AccessTokenResponse;
import com.onlinejudge.backend.api.dto.AuthTokensResponse;
import com.onlinejudge.backend.api.dto.LoginRequest;
import com.onlinejudge.backend.api.dto.RefreshTokenRequest;
import com.onlinejudge.backend.api.dto.RegisterRequest;
import com.onlinejudge.backend.api.dto.UserResponse;
import com.onlinejudge.backend.domain.RefreshToken;
import com.onlinejudge.backend.exception.DuplicateResourceException;
import com.onlinejudge.backend.exception.InvalidRefreshTokenException;
import com.onlinejudge.backend.exception.ResourceNotFoundException;
import com.onlinejudge.backend.repository.RefreshTokenRepository;
import com.onlinejudge.backend.repository.RoleRepository;
import com.onlinejudge.backend.repository.UserRepository;
import com.onlinejudge.backend.security.JwtProperties;
import com.onlinejudge.backend.security.JwtTokenProvider;
import com.onlinejudge.common.entity.Role;
import com.onlinejudge.common.entity.User;
import com.onlinejudge.common.enums.AppRole;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;

    public AuthServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                           RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider, JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new DuplicateResourceException("Username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Email is already registered");
        }

        User user = new User(request.username(), request.email(), passwordEncoder.encode(request.password()));
        user.grantRole(roleByName(AppRole.ROLE_USER));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race between the pre-checks and the insert; the DB constraint is final.
            throw new DuplicateResourceException("Username or email is already taken");
        }
        return UserResponse.from(user);
    }

    @Override
    public AuthTokensResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String accessToken = tokenProvider.createAccessToken(user);
        JwtTokenProvider.IssuedRefreshToken refreshToken = tokenProvider.issueRefreshToken();
        refreshTokenRepository.save(new RefreshToken(user, refreshToken.tokenHash(), jwtProperties.refreshTokenTtl()));
        return new AuthTokensResponse(accessToken, refreshToken.rawToken(), tokenProvider.accessTokenTtlSeconds());
    }

    @Override
    @Transactional(readOnly = true)
    public AccessTokenResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(JwtTokenProvider.hash(request.refreshToken()))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!stored.isUsable() || !stored.getUser().isEnabled()) {
            throw new InvalidRefreshTokenException();
        }

        String accessToken = tokenProvider.createAccessToken(stored.getUser());
        return new AccessTokenResponse(accessToken, tokenProvider.accessTokenTtlSeconds());
    }

    @Override
    public void logout(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User does not exist"));
        refreshTokenRepository.revokeAllActiveForUser(user.getId(), Instant.now());
    }

    private Role roleByName(AppRole appRole) {
        return roleRepository.findByName(appRole.name())
                .orElseThrow(() -> new IllegalStateException(
                        "Seed role %s is missing — check Flyway migrations".formatted(appRole.name())));
    }
}
