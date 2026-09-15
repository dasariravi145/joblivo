package com.joblivo.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EmailPasswordCredential} entities.
 */
@Repository
public interface EmailPasswordCredentialRepository extends JpaRepository<EmailPasswordCredential, UUID> {

    /**
     * Finds the email/password credential associated with the specified authentication identity ID.
     *
     * @param authIdentityId the authentication identity UUID
     * @return Optional containing the credential if found
     */
    Optional<EmailPasswordCredential> findByAuthIdentityId(UUID authIdentityId);

    /**
     * Checks whether an email/password credential already exists for the given authentication identity ID.
     *
     * @param authIdentityId the authentication identity UUID
     * @return true if a credential exists, false otherwise
     */
    boolean existsByAuthIdentityId(UUID authIdentityId);
}
