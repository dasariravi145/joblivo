package com.joblivo.job.intelligence;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable representation of an extracted section from a job description.
 *
 * @param sectionType the canonical classification of the section
 * @param rawHeading  the original detected heading string (or null/blank if unclassified)
 * @param lines       the individual text lines or bullet points in the section
 * @param rawContent  the unedited raw content of the section
 */
public record JobDescriptionSection(
        JobDescriptionSectionType sectionType,
        String rawHeading,
        List<String> lines,
        String rawContent
) {
    public JobDescriptionSection {
        Objects.requireNonNull(sectionType, "sectionType must not be null");
        lines = lines != null ? List.copyOf(lines) : Collections.emptyList();
        rawHeading = rawHeading != null ? rawHeading.trim() : "";
        rawContent = rawContent != null ? rawContent : "";
    }
}
