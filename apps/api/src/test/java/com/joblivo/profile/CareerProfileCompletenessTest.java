package com.joblivo.profile;

import com.joblivo.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Master Career Profile Completeness Unit Tests")
class CareerProfileCompletenessTest {

    private CareerProfile createProfileWithCoreFields(
            String headline,
            String title,
            String company,
            Integer expMonths,
            String location,
            String prefLocation,
            WorkMode workMode,
            Integer noticeDays
    ) {
        CareerProfile profile = new CareerProfile(new User("user@example.com", "Test User"));
        try {
            setField(profile, "id", UUID.randomUUID());
            setField(profile, "professionalHeadline", headline);
            setField(profile, "currentTitle", title);
            setField(profile, "currentCompany", company);
            setField(profile, "totalExperienceMonths", expMonths);
            setField(profile, "currentLocation", location);
            setField(profile, "preferredWorkLocation", prefLocation);
            setField(profile, "preferredWorkMode", workMode);
            setField(profile, "noticePeriodDays", noticeDays);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return profile;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("Empty profile with no core fields and zero child collections has 0% completeness")
    void emptyProfile_hasZeroPercentage() {
        CareerProfile emptyProfile = createProfileWithCoreFields(null, null, null, null, null, null, null, null);

        CareerProfileCompletenessResponse response = CareerProfileCompletenessResponse.calculate(
                emptyProfile, 0, 0, 0, 0, 0, 0
        );

        assertThat(response.completionPercentage()).isEqualTo(0);
        assertThat(response.coreDetails().completed()).isFalse();
        assertThat(response.coreDetails().itemCount()).isEqualTo(0);
        assertThat(response.workExperience().completed()).isFalse();
        assertThat(response.workExperience().itemCount()).isEqualTo(0);
        assertThat(response.skills().completed()).isFalse();
        assertThat(response.skills().itemCount()).isEqualTo(0);
        assertThat(response.projects().completed()).isFalse();
        assertThat(response.projects().itemCount()).isEqualTo(0);
        assertThat(response.education().completed()).isFalse();
        assertThat(response.education().itemCount()).isEqualTo(0);
        assertThat(response.certifications().completed()).isFalse();
        assertThat(response.certifications().itemCount()).isEqualTo(0);
        assertThat(response.achievements().completed()).isFalse();
        assertThat(response.achievements().itemCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Whitespace-only core fields are not counted as meaningful")
    void whitespaceOnlyCoreFields_notCounted() {
        CareerProfile profile = createProfileWithCoreFields("   ", "  \t ", "\n", null, " ", "  ", null, null);

        int count = CareerProfileCompletenessResponse.countMeaningfulCoreFields(profile);
        assertThat(count).isEqualTo(0);

        CareerProfileCompletenessResponse response = CareerProfileCompletenessResponse.calculate(
                profile, 0, 0, 0, 0, 0, 0
        );
        assertThat(response.coreDetails().completed()).isFalse();
        assertThat(response.coreDetails().itemCount()).isEqualTo(0);
        assertThat(response.completionPercentage()).isEqualTo(0);
    }

    @Test
    @DisplayName("Profile with all 8 core fields counts exactly 8 items and marks core completed")
    void allCoreFieldsPopulated_countedCorrectly() {
        CareerProfile profile = createProfileWithCoreFields(
                "Lead Architect",
                "Senior Principal Engineer",
                "Enterprise Corp",
                120,
                "San Francisco, CA",
                "Remote",
                WorkMode.REMOTE,
                30
        );

        int count = CareerProfileCompletenessResponse.countMeaningfulCoreFields(profile);
        assertThat(count).isEqualTo(8);

        CareerProfileCompletenessResponse response = CareerProfileCompletenessResponse.calculate(
                profile, 0, 0, 0, 0, 0, 0
        );
        assertThat(response.coreDetails().completed()).isTrue();
        assertThat(response.coreDetails().itemCount()).isEqualTo(8);
        assertThat(response.completionPercentage()).isEqualTo(14); // 1 of 7 = 14%
    }

    @ParameterizedTest(name = "completedSections={0} -> expectedPercentage={1}")
    @CsvSource({
            "0, 0",
            "1, 14",
            "2, 28",
            "3, 42",
            "4, 57",
            "5, 71",
            "6, 85",
            "7, 100"
    })
    @DisplayName("Deterministic percentage calculation matches formula exactly for 0 to 7 sections")
    void deterministicPercentageCalculation(int completedCount, int expectedPercentage) {
        CareerProfile profileWithCore = createProfileWithCoreFields("Headline", null, null, null, null, null, null, null);
        CareerProfile profileWithoutCore = createProfileWithCoreFields(null, null, null, null, null, null, null, null);

        // Based on completedCount, toggle sections
        CareerProfile profile = (completedCount > 0) ? profileWithCore : profileWithoutCore;
        int we = (completedCount >= 2) ? 2 : 0;
        int sk = (completedCount >= 3) ? 5 : 0;
        int pr = (completedCount >= 4) ? 1 : 0;
        int ed = (completedCount >= 5) ? 3 : 0;
        int ce = (completedCount >= 6) ? 1 : 0;
        int ac = (completedCount >= 7) ? 4 : 0;

        CareerProfileCompletenessResponse response = CareerProfileCompletenessResponse.calculate(
                profile, we, sk, pr, ed, ce, ac
        );

        assertThat(response.completionPercentage()).isEqualTo(expectedPercentage);
    }

    @Test
    @DisplayName("Fully populated profile has all sections complete and 100% completion percentage")
    void fullyPopulatedProfile_has100Percentage() {
        CareerProfile profile = createProfileWithCoreFields(
                "Staff Software Engineer",
                "Senior Engineer",
                "Tech Corp",
                60,
                "New York, NY",
                "New York, NY",
                WorkMode.HYBRID,
                15
        );

        CareerProfileCompletenessResponse response = CareerProfileCompletenessResponse.calculate(
                profile, 3, 10, 2, 1, 2, 3
        );

        assertThat(response.completionPercentage()).isEqualTo(100);
        assertThat(response.coreDetails().completed()).isTrue();
        assertThat(response.coreDetails().itemCount()).isEqualTo(8);
        assertThat(response.workExperience().completed()).isTrue();
        assertThat(response.workExperience().itemCount()).isEqualTo(3);
        assertThat(response.skills().completed()).isTrue();
        assertThat(response.skills().itemCount()).isEqualTo(10);
        assertThat(response.projects().completed()).isTrue();
        assertThat(response.projects().itemCount()).isEqualTo(2);
        assertThat(response.education().completed()).isTrue();
        assertThat(response.education().itemCount()).isEqualTo(1);
        assertThat(response.certifications().completed()).isTrue();
        assertThat(response.certifications().itemCount()).isEqualTo(2);
        assertThat(response.achievements().completed()).isTrue();
        assertThat(response.achievements().itemCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Null profile handled gracefully with zero core fields")
    void nullProfile_handledSafely() {
        int count = CareerProfileCompletenessResponse.countMeaningfulCoreFields(null);
        assertThat(count).isEqualTo(0);

        CareerProfileCompletenessResponse response = CareerProfileCompletenessResponse.calculate(
                null, 1, 1, 1, 0, 0, 0
        );

        assertThat(response.coreDetails().completed()).isFalse();
        assertThat(response.coreDetails().itemCount()).isEqualTo(0);
        assertThat(response.completionPercentage()).isEqualTo(42); // 3 of 7 = 42%
    }
}
