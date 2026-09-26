package com.onlinejudge.backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinejudge.backend.api.dto.AccessTokenResponse;
import com.onlinejudge.backend.api.dto.AuthTokensResponse;
import com.onlinejudge.backend.api.dto.LoginRequest;
import com.onlinejudge.backend.api.dto.RefreshTokenRequest;
import com.onlinejudge.backend.api.dto.RegisterRequest;
import com.onlinejudge.backend.api.dto.UserResponse;
import com.onlinejudge.backend.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Public endpoint — deliberately unauthenticated (PRD §15). */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Public endpoint — deliberately unauthenticated (PRD §15). */
    @PostMapping("/login")
    public AuthTokensResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Public endpoint — the refresh token itself is the credential (PRD §15). */
    @PostMapping("/refresh")
    public AccessTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void logout(Authentication authentication) {
        authService.logout(authentication.getName());
    }
}
