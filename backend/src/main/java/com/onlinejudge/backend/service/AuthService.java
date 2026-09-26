package com.onlinejudge.backend.service;

import com.onlinejudge.backend.api.dto.AccessTokenResponse;
import com.onlinejudge.backend.api.dto.AuthTokensResponse;
import com.onlinejudge.backend.api.dto.LoginRequest;
import com.onlinejudge.backend.api.dto.RefreshTokenRequest;
import com.onlinejudge.backend.api.dto.RegisterRequest;
import com.onlinejudge.backend.api.dto.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthTokensResponse login(LoginRequest request);

    AccessTokenResponse refresh(RefreshTokenRequest request);

    /** Revokes all active refresh tokens of the user (PRD §16). */
    void logout(String username);
}
