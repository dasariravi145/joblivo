package com.joblivo.user;

/**
 * Strongly typed user status enum aligned with the database chk_users_status constraint.
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED
}
