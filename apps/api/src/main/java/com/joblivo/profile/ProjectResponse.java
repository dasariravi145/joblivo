package com.joblivo.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public response model for project records.
 * Contains only safe, factual project information and excludes internal persistence details.
 */
public record ProjectResponse(
        UUID id,
        String projectName,
        ProjectType projectType,
        String role,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean currentlyActive,
        String projectUrl,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(CareerProfileProject project) {
        return new ProjectResponse(
                project.getId(),
                project.getProjectName(),
                project.getProjectType(),
                project.getRole(),
                project.getDescription(),
                project.getStartDate(),
                project.getEndDate(),
                project.isCurrentlyActive(),
                project.getProjectUrl(),
                project.getDisplayOrder(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
