package com.joblivo.profile;

import com.joblivo.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CareerProfile} entities.
 */
@Repository
public interface CareerProfileRepository extends JpaRepository<CareerProfile, UUID> {

    /**
     * Finds a master career profile for the given user entity.
     *
     * @param user the owning user
     * @return Optional containing the career profile if found, empty otherwise
     */
    Optional<CareerProfile> findByUser(User user);

    /**
     * Finds a master career profile by user ID.
     *
     * @param userId the owning user UUID
     * @return Optional containing the career profile if found, empty otherwise
     */
    Optional<CareerProfile> findByUserId(UUID userId);

    /**
     * Checks if a master career profile exists for the given user entity.
     *
     * @param user the user to check
     * @return true if a career profile exists, false otherwise
     */
    boolean existsByUser(User user);

    /**
     * Checks if a master career profile exists for the given user ID.
     *
     * @param userId the user UUID to check
     * @return true if a career profile exists, false otherwise
     */
    boolean existsByUserId(UUID userId);
}
