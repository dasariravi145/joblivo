package com.joblivo.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CareerProfileWorkExperience} entities.
 */
@Repository
public interface CareerProfileWorkExperienceRepository extends JpaRepository<CareerProfileWorkExperience, UUID> {

    /**
     * Finds all work experiences belonging to a specific CareerProfile ID,
     * ordered by display order ascending, then start date descending.
     */
    List<CareerProfileWorkExperience> findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(UUID careerProfileId);

    /**
     * Finds all work experiences belonging to a specific CareerProfile entity,
     * ordered by display order ascending, then start date descending.
     */
    List<CareerProfileWorkExperience> findByCareerProfileOrderByDisplayOrderAscStartDateDesc(CareerProfile careerProfile);

    /**
     * Finds a single work experience by its ID and the owning CareerProfile ID,
     * enforcing tenant/profile isolation at the query level.
     */
    Optional<CareerProfileWorkExperience> findByIdAndCareerProfileId(UUID id, UUID careerProfileId);

    /**
     * Checks existence of a work experience by ID and owning CareerProfile ID.
     */
    boolean existsByIdAndCareerProfileId(UUID id, UUID careerProfileId);

    /**
     * Deletes a work experience by ID and owning CareerProfile ID.
     */
    void deleteByIdAndCareerProfileId(UUID id, UUID careerProfileId);
}
