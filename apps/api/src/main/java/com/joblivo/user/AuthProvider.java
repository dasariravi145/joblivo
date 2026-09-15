package com.joblivo.user;

/**
 * Supported authentication identity providers for Joblivo accounts.
 * Persisted as string representations in user_auth_identities.
 */
public enum AuthProvider {
    EMAIL,
    GOOGLE,
    PHONE
}
