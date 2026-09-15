package com.joblivo.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for CareerProfileCertification management.
 * Enforces profile isolation, display ordering, and normalized duplicate checks.
 */
@Repository
public interface CareerProfileCertificationRepository extends JpaRepository<CareerProfileCertification, UUID> {

    List<CareerProfileCertification> findByCareerProfileIdOrderByDisplayOrderAscIssueDateDesc(UUID careerProfileId);

    Optional<CareerProfileCertification> findByIdAndCareerProfileId(UUID id, UUID careerProfileId);

    @Query("""
            SELECT COUNT(c) > 0 FROM CareerProfileCertification c
            WHERE c.careerProfile.id = :careerProfileId
            AND c.credentialId IS NOT NULL
            AND LOWER(TRIM(c.credentialId)) = LOWER(TRIM(:credentialId))
            """)
    boolean existsByCareerProfileIdAndNormalizedCredentialId(
            @Param("careerProfileId") UUID careerProfileId,
            @Param("credentialId") String credentialId
    );

    @Query("""
            SELECT COUNT(c) > 0 FROM CareerProfileCertification c
            WHERE c.careerProfile.id = :careerProfileId
            AND c.credentialId IS NOT NULL
            AND LOWER(TRIM(c.credentialId)) = LOWER(TRIM(:credentialId))
            AND c.id != :excludeId
            """)
    boolean existsByCareerProfileIdAndNormalizedCredentialIdExcludingId(
            @Param("careerProfileId") UUID careerProfileId,
            @Param("credentialId") String credentialId,
            @Param("excludeId") UUID excludeId
    );

    @Query("""
            SELECT COUNT(c) > 0 FROM CareerProfileCertification c
            WHERE c.careerProfile.id = :careerProfileId
            AND LOWER(TRIM(c.certificationName)) = LOWER(TRIM(:certificationName))
            AND LOWER(TRIM(c.issuingOrganization)) = LOWER(TRIM(:issuingOrganization))
            AND (c.credentialId IS NULL OR TRIM(c.credentialId) = '')
            """)
    boolean existsByCareerProfileIdAndNormalizedNameAndOrganization(
            @Param("careerProfileId") UUID careerProfileId,
            @Param("certificationName") String certificationName,
            @Param("issuingOrganization") String issuingOrganization
    );

    @Query("""
            SELECT COUNT(c) > 0 FROM CareerProfileCertification c
            WHERE c.careerProfile.id = :careerProfileId
            AND LOWER(TRIM(c.certificationName)) = LOWER(TRIM(:certificationName))
            AND LOWER(TRIM(c.issuingOrganization)) = LOWER(TRIM(:issuingOrganization))
            AND (c.credentialId IS NULL OR TRIM(c.credentialId) = '')
            AND c.id != :excludeId
            """)
    boolean existsByCareerProfileIdAndNormalizedNameAndOrganizationExcludingId(
            @Param("careerProfileId") UUID careerProfileId,
            @Param("certificationName") String certificationName,
            @Param("issuingOrganization") String issuingOrganization,
            @Param("excludeId") UUID excludeId
    );
}
