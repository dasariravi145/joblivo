package com.joblivo.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for CareerProfileAchievement management.
 * Enforces profile isolation, display ordering, and normalized duplicate checks.
 */
@Repository
public interface CareerProfileAchievementRepository extends JpaRepository<CareerProfileAchievement, UUID> {

    List<CareerProfileAchievement> findByCareerProfileIdOrderByDisplayOrderAscAchievementDateDesc(UUID careerProfileId);

    Optional<CareerProfileAchievement> findByIdAndCareerProfileId(UUID id, UUID careerProfileId);

    @Query("""
            SELECT COUNT(a) > 0 FROM CareerProfileAchievement a
            WHERE a.careerProfile.id = :careerProfileId
            AND LOWER(TRIM(a.title)) = LOWER(TRIM(:title))
            AND a.achievementType = :achievementType
            AND ((a.achievementDate IS NULL AND :achievementDate IS NULL) OR (a.achievementDate = :achievementDate))
            AND ((a.issuingOrganization IS NULL AND :issuingOrganization IS NULL)
                 OR (TRIM(a.issuingOrganization) = '' AND :issuingOrganization IS NULL)
                 OR (a.issuingOrganization IS NULL AND TRIM(:issuingOrganization) = '')
                 OR (LOWER(TRIM(a.issuingOrganization)) = LOWER(TRIM(:issuingOrganization))))
            """)
    boolean existsByCompositeNormalized(
            @Param("careerProfileId") UUID careerProfileId,
            @Param("title") String title,
            @Param("achievementType") AchievementType achievementType,
            @Param("achievementDate") LocalDate achievementDate,
            @Param("issuingOrganization") String issuingOrganization
    );

    @Query("""
            SELECT COUNT(a) > 0 FROM CareerProfileAchievement a
            WHERE a.careerProfile.id = :careerProfileId
            AND a.id != :excludeId
            AND LOWER(TRIM(a.title)) = LOWER(TRIM(:title))
            AND a.achievementType = :achievementType
            AND ((a.achievementDate IS NULL AND :achievementDate IS NULL) OR (a.achievementDate = :achievementDate))
            AND ((a.issuingOrganization IS NULL AND :issuingOrganization IS NULL)
                 OR (TRIM(a.issuingOrganization) = '' AND :issuingOrganization IS NULL)
                 OR (a.issuingOrganization IS NULL AND TRIM(:issuingOrganization) = '')
                 OR (LOWER(TRIM(a.issuingOrganization)) = LOWER(TRIM(:issuingOrganization))))
            """)
    boolean existsByCompositeNormalizedExcludingId(
            @Param("careerProfileId") UUID careerProfileId,
            @Param("title") String title,
            @Param("achievementType") AchievementType achievementType,
            @Param("achievementDate") LocalDate achievementDate,
            @Param("issuingOrganization") String issuingOrganization,
            @Param("excludeId") UUID excludeId
    );
}
