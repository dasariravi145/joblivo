package com.joblivo.profile;

import com.joblivo.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CareerProfileWorkExperienceTest {

    @Test
    void testEmploymentTypeEnumValues() {
        List<String> names = Arrays.stream(EmploymentType.values())
                .map(Enum::name)
                .toList();

        assertEquals(7, names.size());
        assertTrue(names.contains("FULL_TIME"));
        assertTrue(names.contains("PART_TIME"));
        assertTrue(names.contains("CONTRACT"));
        assertTrue(names.contains("INTERNSHIP"));
        assertTrue(names.contains("FREELANCE"));
        assertTrue(names.contains("TEMPORARY"));
        assertTrue(names.contains("OTHER"));
    }

    @Test
    void testEntityTableAndIdMapping() throws NoSuchFieldException {
        Table table = CareerProfileWorkExperience.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("career_profile_work_experiences", table.name());

        Field idField = CareerProfileWorkExperience.class.getDeclaredField("id");
        assertNotNull(idField.getAnnotation(Id.class));
        assertNotNull(idField.getAnnotation(GeneratedValue.class));

        Column idColumn = idField.getAnnotation(Column.class);
        assertNotNull(idColumn);
        assertEquals("id", idColumn.name());
        assertFalse(idColumn.nullable());
        assertFalse(idColumn.updatable());
    }

    @Test
    void testCareerProfileRelationshipMapping() throws NoSuchFieldException {
        Field profileField = CareerProfileWorkExperience.class.getDeclaredField("careerProfile");
        ManyToOne manyToOne = profileField.getAnnotation(ManyToOne.class);
        assertNotNull(manyToOne);
        assertEquals(FetchType.LAZY, manyToOne.fetch());
        assertFalse(manyToOne.optional());

        JoinColumn joinColumn = profileField.getAnnotation(JoinColumn.class);
        assertNotNull(joinColumn);
        assertEquals("career_profile_id", joinColumn.name());
        assertFalse(joinColumn.nullable());
    }

    @Test
    void testColumnMappingsAndLengths() throws NoSuchFieldException {
        Field companyField = CareerProfileWorkExperience.class.getDeclaredField("companyName");
        Column companyCol = companyField.getAnnotation(Column.class);
        assertNotNull(companyCol);
        assertEquals("company_name", companyCol.name());
        assertEquals(100, companyCol.length());
        assertFalse(companyCol.nullable());

        Field titleField = CareerProfileWorkExperience.class.getDeclaredField("jobTitle");
        Column titleCol = titleField.getAnnotation(Column.class);
        assertNotNull(titleCol);
        assertEquals("job_title", titleCol.name());
        assertEquals(100, titleCol.length());
        assertFalse(titleCol.nullable());

        Field typeField = CareerProfileWorkExperience.class.getDeclaredField("employmentType");
        Column typeCol = typeField.getAnnotation(Column.class);
        assertNotNull(typeCol);
        assertEquals("employment_type", typeCol.name());
        assertEquals(30, typeCol.length());
        assertFalse(typeCol.nullable());

        Enumerated enumerated = typeField.getAnnotation(Enumerated.class);
        assertNotNull(enumerated);
        assertEquals(EnumType.STRING, enumerated.value());

        Field startField = CareerProfileWorkExperience.class.getDeclaredField("startDate");
        Column startCol = startField.getAnnotation(Column.class);
        assertNotNull(startCol);
        assertEquals("start_date", startCol.name());
        assertFalse(startCol.nullable());

        Field endField = CareerProfileWorkExperience.class.getDeclaredField("endDate");
        Column endCol = endField.getAnnotation(Column.class);
        assertNotNull(endCol);
        assertEquals("end_date", endCol.name());
        assertTrue(endCol.nullable());

        Field currentField = CareerProfileWorkExperience.class.getDeclaredField("currentlyWorking");
        Column currentCol = currentField.getAnnotation(Column.class);
        assertNotNull(currentCol);
        assertEquals("currently_working", currentCol.name());
        assertFalse(currentCol.nullable());

        Field locationField = CareerProfileWorkExperience.class.getDeclaredField("location");
        Column locationCol = locationField.getAnnotation(Column.class);
        assertNotNull(locationCol);
        assertEquals("location", locationCol.name());
        assertEquals(100, locationCol.length());

        Field descField = CareerProfileWorkExperience.class.getDeclaredField("description");
        Column descCol = descField.getAnnotation(Column.class);
        assertNotNull(descCol);
        assertEquals("description", descCol.name());
        assertEquals("TEXT", descCol.columnDefinition());

        Field orderField = CareerProfileWorkExperience.class.getDeclaredField("displayOrder");
        Column orderCol = orderField.getAnnotation(Column.class);
        assertNotNull(orderCol);
        assertEquals("display_order", orderCol.name());
        assertFalse(orderCol.nullable());

        Field createdField = CareerProfileWorkExperience.class.getDeclaredField("createdAt");
        Column createdCol = createdField.getAnnotation(Column.class);
        assertNotNull(createdCol);
        assertEquals("created_at", createdCol.name());
        assertFalse(createdCol.nullable());
        assertFalse(createdCol.updatable());

        Field updatedField = CareerProfileWorkExperience.class.getDeclaredField("updatedAt");
        Column updatedCol = updatedField.getAnnotation(Column.class);
        assertNotNull(updatedCol);
        assertEquals("updated_at", updatedCol.name());
        assertFalse(updatedCol.nullable());
    }

    @Test
    void testGettersAndSetters() {
        User user = new User("workuser@joblivo.com", "Work User");
        CareerProfile profile = new CareerProfile(user);

        CareerProfileWorkExperience experience = new CareerProfileWorkExperience(
                profile,
                "Acme Corp",
                "Software Engineer",
                EmploymentType.FULL_TIME,
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2023, 6, 30),
                false,
                "New York, NY",
                "Built distributed services",
                1
        );

        assertEquals(profile, experience.getCareerProfile());
        assertEquals("Acme Corp", experience.getCompanyName());
        assertEquals("Software Engineer", experience.getJobTitle());
        assertEquals(EmploymentType.FULL_TIME, experience.getEmploymentType());
        assertEquals(LocalDate.of(2022, 1, 1), experience.getStartDate());
        assertEquals(LocalDate.of(2023, 6, 30), experience.getEndDate());
        assertFalse(experience.isCurrentlyWorking());
        assertEquals("New York, NY", experience.getLocation());
        assertEquals("Built distributed services", experience.getDescription());
        assertEquals(1, experience.getDisplayOrder());

        // Update fields
        experience.setCompanyName("Beta Tech");
        experience.setJobTitle("Senior Engineer");
        experience.setEmploymentType(EmploymentType.CONTRACT);
        experience.setStartDate(LocalDate.of(2023, 7, 1));
        experience.setEndDate(null);
        experience.setCurrentlyWorking(true);
        experience.setLocation("Remote");
        experience.setDescription("Cloud architecture");
        experience.setDisplayOrder(0);

        assertEquals("Beta Tech", experience.getCompanyName());
        assertEquals("Senior Engineer", experience.getJobTitle());
        assertEquals(EmploymentType.CONTRACT, experience.getEmploymentType());
        assertEquals(LocalDate.of(2023, 7, 1), experience.getStartDate());
        assertNull(experience.getEndDate());
        assertTrue(experience.isCurrentlyWorking());
        assertEquals("Remote", experience.getLocation());
        assertEquals("Cloud architecture", experience.getDescription());
        assertEquals(0, experience.getDisplayOrder());
    }

    @Test
    void testLifecycleCallbacks() throws Exception {
        Method onCreate = CareerProfileWorkExperience.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        Method onUpdate = CareerProfileWorkExperience.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);

        assertNotNull(onCreate.getAnnotation(PrePersist.class));
        assertNotNull(onUpdate.getAnnotation(PreUpdate.class));

        User user = new User("workuser@joblivo.com", "Work User");
        CareerProfile profile = new CareerProfile(user);
        CareerProfileWorkExperience experience = new CareerProfileWorkExperience(
                profile, "Company", "Title", EmploymentType.FULL_TIME,
                LocalDate.of(2020, 1, 1), null, true, null, null, 0
        );

        assertNull(experience.getCreatedAt());
        assertNull(experience.getUpdatedAt());

        onCreate.invoke(experience);
        assertNotNull(experience.getCreatedAt());
        assertNotNull(experience.getUpdatedAt());

        Instant originalUpdatedAt = experience.getUpdatedAt();
        Thread.sleep(2);
        onUpdate.invoke(experience);
        assertTrue(experience.getUpdatedAt().isAfter(originalUpdatedAt) || experience.getUpdatedAt().equals(originalUpdatedAt));
    }

    @Test
    void testEqualsAndHashCode() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        User user = new User("workuser@joblivo.com", "Work User");
        CareerProfile profile = new CareerProfile(user);

        CareerProfileWorkExperience exp1 = new CareerProfileWorkExperience(
                id1, profile, "Company", "Title", EmploymentType.FULL_TIME,
                LocalDate.of(2020, 1, 1), null, true, null, null, 0, Instant.now(), Instant.now()
        );
        CareerProfileWorkExperience exp1Same = new CareerProfileWorkExperience(
                id1, profile, "Other Company", "Other Title", EmploymentType.CONTRACT,
                LocalDate.of(2021, 1, 1), null, true, null, null, 0, Instant.now(), Instant.now()
        );
        CareerProfileWorkExperience exp2 = new CareerProfileWorkExperience(
                id2, profile, "Company", "Title", EmploymentType.FULL_TIME,
                LocalDate.of(2020, 1, 1), null, true, null, null, 0, Instant.now(), Instant.now()
        );

        assertEquals(exp1, exp1Same);
        assertEquals(exp1.hashCode(), exp1Same.hashCode());
        assertNotEquals(exp1, exp2);
        assertNotEquals(exp1, null);
        assertNotEquals(exp1, new Object());
    }

    @Test
    void testRequestNormalizationAndValidation() {
        WorkExperienceRequest request = new WorkExperienceRequest(
                "  Acme Corp  ",
                "  Software Engineer  ",
                EmploymentType.FULL_TIME,
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2023, 1, 1),
                null,
                "   ",
                "   ",
                null
        );

        assertEquals("Acme Corp", request.companyName());
        assertEquals("Software Engineer", request.jobTitle());
        assertNull(request.location());
        assertNull(request.description());
        assertFalse(request.currentlyWorking());
        assertEquals(0, request.displayOrder());
        assertTrue(request.isDateRangeValid());
        assertTrue(request.isCurrentlyWorkingValid());
    }

    @Test
    void testDateRangeValidationRule() {
        // endDate before startDate -> invalid
        WorkExperienceRequest invalidDates = new WorkExperienceRequest(
                "Company", "Title", EmploymentType.FULL_TIME,
                LocalDate.of(2023, 5, 1),
                LocalDate.of(2022, 5, 1),
                false, null, null, 0
        );
        assertFalse(invalidDates.isDateRangeValid());

        // endDate equals startDate -> valid
        WorkExperienceRequest sameDates = new WorkExperienceRequest(
                "Company", "Title", EmploymentType.FULL_TIME,
                LocalDate.of(2023, 5, 1),
                LocalDate.of(2023, 5, 1),
                false, null, null, 0
        );
        assertTrue(sameDates.isDateRangeValid());

        // endDate after startDate -> valid
        WorkExperienceRequest validDates = new WorkExperienceRequest(
                "Company", "Title", EmploymentType.FULL_TIME,
                LocalDate.of(2022, 5, 1),
                LocalDate.of(2023, 5, 1),
                false, null, null, 0
        );
        assertTrue(validDates.isDateRangeValid());

        // endDate null -> valid
        WorkExperienceRequest nullEnd = new WorkExperienceRequest(
                "Company", "Title", EmploymentType.FULL_TIME,
                LocalDate.of(2022, 5, 1),
                null,
                true, null, null, 0
        );
        assertTrue(nullEnd.isDateRangeValid());
    }

    @Test
    void testCurrentlyWorkingConsistencyRule() {
        // currentlyWorking = true with endDate != null -> invalid
        WorkExperienceRequest invalidCurrent = new WorkExperienceRequest(
                "Company", "Title", EmploymentType.FULL_TIME,
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2023, 1, 1),
                true, null, null, 0
        );
        assertFalse(invalidCurrent.isCurrentlyWorkingValid());

        // currentlyWorking = true with endDate == null -> valid
        WorkExperienceRequest validCurrent = new WorkExperienceRequest(
                "Company", "Title", EmploymentType.FULL_TIME,
                LocalDate.of(2022, 1, 1),
                null,
                true, null, null, 0
        );
        assertTrue(validCurrent.isCurrentlyWorkingValid());

        // currentlyWorking = false with endDate != null -> valid
        WorkExperienceRequest validPastWithDate = new WorkExperienceRequest(
                "Company", "Title", EmploymentType.FULL_TIME,
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2023, 1, 1),
                false, null, null, 0
        );
        assertTrue(validPastWithDate.isCurrentlyWorkingValid());

        // currentlyWorking = false with endDate == null -> valid (unknown/open past history)
        WorkExperienceRequest validPastWithoutDate = new WorkExperienceRequest(
                "Company", "Title", EmploymentType.FULL_TIME,
                LocalDate.of(2022, 1, 1),
                null,
                false, null, null, 0
        );
        assertTrue(validPastWithoutDate.isCurrentlyWorkingValid());
    }

    @Test
    void testScopeProtectionNoFuturePromptFields() {
        List<String> prohibitedScopeFields = List.of(
                "skill", "technology", "project", "education", "degree",
                "certification", "achievement", "evidence", "resume",
                "score", "salary", "compensation", "interview"
        );

        for (Field field : CareerProfileWorkExperience.class.getDeclaredFields()) {
            String lowerName = field.getName().toLowerCase();
            for (String prohibited : prohibitedScopeFields) {
                assertFalse(
                        lowerName.contains(prohibited),
                        "CareerProfileWorkExperience entity must not contain out-of-scope field: " + field.getName()
                );
            }
        }
    }
}
