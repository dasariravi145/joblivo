package com.joblivo.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CareerProfileProject} entities.
 */
@Repository
public interface CareerProfileProjectRepository extends JpaRepository<CareerProfileProject, UUID> {

    /**
     * Finds all projects belonging to a specific CareerProfile ID,
     * ordered by display order ascending, then start date descending (nulls last).
     */
    List<CareerProfileProject> findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(UUID careerProfileId);

    /**
     * Finds a single project by its ID and the owning CareerProfile ID,
     * enforcing tenant/profile isolation at the query level.
     */
    Optional<CareerProfileProject> findByIdAndCareerProfileId(UUID id, UUID careerProfileId);

    /**
     * Finds an existing project for a CareerProfile using normalized comparison (case-insensitive and trimmed).
     */
    @Query("SELECT p FROM CareerProfileProject p WHERE p.careerProfile.id = :careerProfileId AND LOWER(TRIM(p.projectName)) = LOWER(TRIM(:projectName))")
    Optional<CareerProfileProject> findByCareerProfileIdAndNormalizedName(
            @Param("careerProfileId") UUID careerProfileId,
            @Param("projectName") String projectName
    );

    /**
     * Checks if a project with the normalized name already exists within the given CareerProfile.
     */
    @Query("SELECT COUNT(p) > 0 FROM CareerProfileProject p WHERE p.careerProfile.id = :careerProfileId AND LOWER(TRIM(p.projectName)) = LOWER(TRIM(:projectName))")
    boolean existsByCareerProfileIdAndNormalizedName(
            @Param("careerProfileId") UUID careerProfileId,
            @Param("projectName") String projectName
    );

    /**
     * Checks if another project with the normalized name exists within the given CareerProfile, excluding a specific project ID.
     */
    @Query("SELECT COUNT(p) > 0 FROM CareerProfileProject p WHERE p.careerProfile.id = :careerProfileId AND LOWER(TRIM(p.projectName)) = LOWER(TRIM(:projectName)) AND p.id <> :excludeId")
    boolean existsByCareerProfileIdAndNormalizedNameExcludingId(
            @Param("careerProfileId") UUID careerProfileId,
            @Param("projectName") String projectName,
            @Param("excludeId") UUID excludeId
    );

    /**
     * Checks existence of a project by ID and owning CareerProfile ID.
     */
    boolean existsByIdAndCareerProfileId(UUID id, UUID careerProfileId);

    /**
     * Deletes a project by ID and owning CareerProfile ID.
     */
    void deleteByIdAndCareerProfileId(UUID id, UUID careerProfileId);
}
