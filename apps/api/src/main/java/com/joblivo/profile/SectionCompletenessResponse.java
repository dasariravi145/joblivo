package com.joblivo.profile;

/**
 * Section-level completion descriptor for Master Career Profile sections.
 * Indicates whether the section meets completeness criteria and reports the count of items/fields.
 */
public record SectionCompletenessResponse(
        boolean completed,
        int itemCount
) {
    public static SectionCompletenessResponse of(boolean completed, int itemCount) {
        return new SectionCompletenessResponse(completed, itemCount);
    }
}
