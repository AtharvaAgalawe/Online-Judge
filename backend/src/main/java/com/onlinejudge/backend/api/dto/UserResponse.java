package com.onlinejudge.backend.api.dto;

import com.onlinejudge.common.entity.User;

/** Safe user projection — never exposes the password hash. */
public record UserResponse(Long id, String username, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail());
    }
}
