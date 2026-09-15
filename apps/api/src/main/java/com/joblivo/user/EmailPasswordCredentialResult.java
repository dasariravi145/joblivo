package com.joblivo.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe, immutable domain result returned upon successful credential creation.
 * Strictly isolates both the raw password and the stored password hash from callers.
 *
 * @param id             the unique identifier of the persisted credential record
 * @param authIdentityId the associated authentication identity identifier
 * @param createdAt      timestamp when the credential record was created
 * @param updatedAt      timestamp when the credential record was last updated
 */
public record EmailPasswordCredentialResult(
        UUID id,
        UUID authIdentityId,
        Instant createdAt,
        Instant updatedAt
) {
}
