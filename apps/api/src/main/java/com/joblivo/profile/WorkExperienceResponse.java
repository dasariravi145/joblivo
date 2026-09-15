package com.joblivo.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public response model for work experience records.
 * Contains only safe, factual career information and excludes internal persistence details.
 */
public record WorkExperienceResponse(
        UUID id,
        String companyName,
        String jobTitle,
        EmploymentType employmentType,
        LocalDate startDate,
        LocalDate endDate,
        boolean currentlyWorking,
        String location,
        String description,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkExperienceResponse from(CareerProfileWorkExperience experience) {
        return new WorkExperienceResponse(
                experience.getId(),
                experience.getCompanyName(),
                experience.getJobTitle(),
                experience.getEmploymentType(),
                experience.getStartDate(),
                experience.getEndDate(),
                experience.isCurrentlyWorking(),
                experience.getLocation(),
                experience.getDescription(),
                experience.getDisplayOrder(),
                experience.getCreatedAt(),
                experience.getUpdatedAt()
        );
    }
}
