package com.joblivo.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CareerProfileSkill} entities.
 */
@Repository
public interface CareerProfileSkillRepository extends JpaRepository<CareerProfileSkill, UUID> {

    /**
     * Finds all skills belonging to a specific CareerProfile ID,
     * ordered by display order ascending, then name ascending.
     */
    List<CareerProfileSkill> findByCareerProfileIdOrderByDisplayOrderAscNameAsc(UUID careerProfileId);

    /**
     * Finds a single skill by its ID and the owning CareerProfile ID,
     * enforcing tenant/profile isolation at the query level.
     */
    Optional<CareerProfileSkill> findByIdAndCareerProfileId(UUID id, UUID careerProfileId);

    /**
     * Finds an existing skill for a CareerProfile using normalized comparison (case-insensitive and trimmed).
     */
    @Query("SELECT s FROM CareerProfileSkill s WHERE s.careerProfile.id = :careerProfileId AND LOWER(TRIM(s.name)) = LOWER(TRIM(:name))")
    Optional<CareerProfileSkill> findByCareerProfileIdAndNormalizedName(@Param("careerProfileId") UUID careerProfileId, @Param("name") String name);

    /**
     * Checks if a skill with the normalized name already exists within the given CareerProfile.
     */
    @Query("SELECT COUNT(s) > 0 FROM CareerProfileSkill s WHERE s.careerProfile.id = :careerProfileId AND LOWER(TRIM(s.name)) = LOWER(TRIM(:name))")
    boolean existsByCareerProfileIdAndNormalizedName(@Param("careerProfileId") UUID careerProfileId, @Param("name") String name);

    /**
     * Checks if another skill with the normalized name exists within the given CareerProfile, excluding a specific skill ID.
     */
    @Query("SELECT COUNT(s) > 0 FROM CareerProfileSkill s WHERE s.careerProfile.id = :careerProfileId AND LOWER(TRIM(s.name)) = LOWER(TRIM(:name)) AND s.id <> :excludeId")
    boolean existsByCareerProfileIdAndNormalizedNameExcludingId(
            @Param("careerProfileId") UUID careerProfileId,
            @Param("name") String name,
            @Param("excludeId") UUID excludeId
    );

    /**
     * Checks existence of a skill by ID and owning CareerProfile ID.
     */
    boolean existsByIdAndCareerProfileId(UUID id, UUID careerProfileId);

    /**
     * Deletes a skill by ID and owning CareerProfile ID.
     */
    void deleteByIdAndCareerProfileId(UUID id, UUID careerProfileId);
}
