package com.onlinejudge.common.enums;

/**
 * The application's roles (PRD §16). The {@code roles} table stores exactly these
 * names; this enum is the compile-time source of truth for them (AGENTS.md §6).
 */
public enum AppRole {
    ROLE_USER,
    ROLE_ADMIN
}
