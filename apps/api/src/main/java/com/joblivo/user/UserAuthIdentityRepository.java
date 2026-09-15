package com.joblivo.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserAuthIdentity} entities.
 */
@Repository
public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, UUID> {

    /**
     * Finds an authentication identity matching the provider and external subject identifier.
     *
     * @param provider        the authentication provider (e.g., EMAIL, GOOGLE, PHONE)
     * @param providerSubject the unique subject supplied by the provider
     * @return Optional containing the identity if found
     */
    Optional<UserAuthIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

    /**
     * Checks if an authentication identity exists for the specified provider and external subject.
     *
     * @param provider        the authentication provider
     * @param providerSubject the unique subject supplied by the provider
     * @return true if an identity exists, false otherwise
     */
    boolean existsByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

    /**
     * Finds all authentication identities linked to a specific user ID.
     *
     * @param userId the user UUID
     * @return list of identities associated with the user
     */
    List<UserAuthIdentity> findByUserId(UUID userId);

    /**
     * Finds an authentication identity for a specific user ID and provider.
     *
     * @param userId   the user UUID
     * @param provider the authentication provider
     * @return Optional containing the identity if found
     */
    Optional<UserAuthIdentity> findByUserIdAndProvider(UUID userId, AuthProvider provider);

    /**
     * Checks if an authentication identity exists for a specific user ID and provider.
     *
     * @param userId   the user UUID
     * @param provider the authentication provider
     * @return true if an identity exists for this user and provider, false otherwise
     */
    boolean existsByUserIdAndProvider(UUID userId, AuthProvider provider);
}
