package com.joblivo.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CareerProfileEducation} entities.
 */
@Repository
public interface CareerProfileEducationRepository extends JpaRepository<CareerProfileEducation, UUID> {

    /**
     * Finds all education records belonging to a specific CareerProfile ID,
     * ordered by display order ascending, then start date descending (nulls last).
     */
    List<CareerProfileEducation> findByCareerProfileIdOrderByDisplayOrderAscStartDateDesc(UUID careerProfileId);

    /**
     * Finds a single education record by its ID and the owning CareerProfile ID,
     * enforcing tenant/profile isolation at the query level.
     */
    Optional<CareerProfileEducation> findByIdAndCareerProfileId(UUID id, UUID careerProfileId);

    /**
     * Checks if an education record with the normalized composite (institution, degree, field of study)
     * already exists within the given CareerProfile.
     */
    @Query("SELECT COUNT(e) > 0 FROM CareerProfileEducation e WHERE e.careerProfile.id = :careerProfileId " +
            "AND LOWER(TRIM(e.institutionName)) = LOWER(TRIM(:institutionName)) " +
            "AND LOWER(TRIM(COALESCE(e.degree, ''))) = LOWER(TRIM(COALESCE(:degree, ''))) " +
            "AND LOWER(TRIM(COALESCE(e.fieldOfStudy, ''))) = LOWER(TRIM(COALESCE(:fieldOfStudy, '')))")
    boolean existsByCareerProfileIdAndNormalizedComposite(
            @Param("careerProfileId") UUID careerProfileId,
            @Param("institutionName") String institutionName,
            @Param("degree") String degree,
            @Param("fieldOfStudy") String fieldOfStudy
    );

    /**
     * Checks if another education record with the normalized composite exists within the given CareerProfile,
     * excluding a specific education record ID.
     */
    @Query("SELECT COUNT(e) > 0 FROM CareerProfileEducation e WHERE e.careerProfile.id = :careerProfileId " +
            "AND LOWER(TRIM(e.institutionName)) = LOWER(TRIM(:institutionName)) " +
            "AND LOWER(TRIM(COALESCE(e.degree, ''))) = LOWER(TRIM(COALESCE(:degree, ''))) " +
            "AND LOWER(TRIM(COALESCE(e.fieldOfStudy, ''))) = LOWER(TRIM(COALESCE(:fieldOfStudy, ''))) " +
            "AND e.id <> :excludeId")
    boolean existsByCareerProfileIdAndNormalizedCompositeExcludingId(
            @Param("careerProfileId") UUID careerProfileId,
            @Param("institutionName") String institutionName,
            @Param("degree") String degree,
            @Param("fieldOfStudy") String fieldOfStudy,
            @Param("excludeId") UUID excludeId
    );

    /**
     * Checks existence of an education record by ID and owning CareerProfile ID.
     */
    boolean existsByIdAndCareerProfileId(UUID id, UUID careerProfileId);

    /**
     * Deletes an education record by ID and owning CareerProfile ID.
     */
    void deleteByIdAndCareerProfileId(UUID id, UUID careerProfileId);
}
