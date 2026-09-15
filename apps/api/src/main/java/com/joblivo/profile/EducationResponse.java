package com.joblivo.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public response model for education records.
 * Contains only safe, factual qualification information and excludes internal persistence details.
 */
public record EducationResponse(
        UUID id,
        String institutionName,
        String degree,
        String fieldOfStudy,
        EducationLevel educationLevel,
        LocalDate startDate,
        LocalDate endDate,
        boolean currentlyStudying,
        String grade,
        String location,
        String description,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {
    public static EducationResponse from(CareerProfileEducation education) {
        return new EducationResponse(
                education.getId(),
                education.getInstitutionName(),
                education.getDegree(),
                education.getFieldOfStudy(),
                education.getEducationLevel(),
                education.getStartDate(),
                education.getEndDate(),
                education.isCurrentlyStudying(),
                education.getGrade(),
                education.getLocation(),
                education.getDescription(),
                education.getDisplayOrder(),
                education.getCreatedAt(),
                education.getUpdatedAt()
        );
    }
}
