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

class CareerProfileEducationTest {

    @Test
    void testEducationLevelEnumValues() {
        List<String> names = Arrays.stream(EducationLevel.values())
                .map(Enum::name)
                .toList();

        assertEquals(7, names.size());
        assertTrue(names.contains("HIGH_SCHOOL"));
        assertTrue(names.contains("DIPLOMA"));
        assertTrue(names.contains("UNDERGRADUATE"));
        assertTrue(names.contains("POSTGRADUATE"));
        assertTrue(names.contains("DOCTORATE"));
        assertTrue(names.contains("PROFESSIONAL"));
        assertTrue(names.contains("OTHER"));
    }

    @Test
    void testEntityTableAndIdMapping() throws NoSuchFieldException {
        Table table = CareerProfileEducation.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("career_profile_education", table.name());

        Field idField = CareerProfileEducation.class.getDeclaredField("id");
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
        Field profileField = CareerProfileEducation.class.getDeclaredField("careerProfile");
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
    void testColumnMappingsAndConstraints() throws NoSuchFieldException {
        Field instField = CareerProfileEducation.class.getDeclaredField("institutionName");
        Column instCol = instField.getAnnotation(Column.class);
        assertNotNull(instCol);
        assertEquals("institution_name", instCol.name());
        assertEquals(150, instCol.length());
        assertFalse(instCol.nullable());

        Field degField = CareerProfileEducation.class.getDeclaredField("degree");
        Column degCol = degField.getAnnotation(Column.class);
        assertNotNull(degCol);
        assertEquals("degree", degCol.name());
        assertEquals(100, degCol.length());

        Field fosField = CareerProfileEducation.class.getDeclaredField("fieldOfStudy");
        Column fosCol = fosField.getAnnotation(Column.class);
        assertNotNull(fosCol);
        assertEquals("field_of_study", fosCol.name());
        assertEquals(100, fosCol.length());

        Field lvlField = CareerProfileEducation.class.getDeclaredField("educationLevel");
        Column lvlCol = lvlField.getAnnotation(Column.class);
        assertNotNull(lvlCol);
        assertEquals("education_level", lvlCol.name());
        assertEquals(50, lvlCol.length());
        assertFalse(lvlCol.nullable());
        Enumerated lvlEnum = lvlField.getAnnotation(Enumerated.class);
        assertNotNull(lvlEnum);
        assertEquals(EnumType.STRING, lvlEnum.value());

        Field startField = CareerProfileEducation.class.getDeclaredField("startDate");
        Column startCol = startField.getAnnotation(Column.class);
        assertNotNull(startCol);
        assertEquals("start_date", startCol.name());

        Field endField = CareerProfileEducation.class.getDeclaredField("endDate");
        Column endCol = endField.getAnnotation(Column.class);
        assertNotNull(endCol);
        assertEquals("end_date", endCol.name());

        Field currField = CareerProfileEducation.class.getDeclaredField("currentlyStudying");
        Column currCol = currField.getAnnotation(Column.class);
        assertNotNull(currCol);
        assertEquals("currently_studying", currCol.name());
        assertFalse(currCol.nullable());

        Field gradeField = CareerProfileEducation.class.getDeclaredField("grade");
        Column gradeCol = gradeField.getAnnotation(Column.class);
        assertNotNull(gradeCol);
        assertEquals("grade", gradeCol.name());
        assertEquals(50, gradeCol.length());

        Field locField = CareerProfileEducation.class.getDeclaredField("location");
        Column locCol = locField.getAnnotation(Column.class);
        assertNotNull(locCol);
        assertEquals("location", locCol.name());
        assertEquals(150, locCol.length());

        Field descField = CareerProfileEducation.class.getDeclaredField("description");
        Column descCol = descField.getAnnotation(Column.class);
        assertNotNull(descCol);
        assertEquals("description", descCol.name());
        assertEquals("TEXT", descCol.columnDefinition());

        Field orderField = CareerProfileEducation.class.getDeclaredField("displayOrder");
        Column orderCol = orderField.getAnnotation(Column.class);
        assertNotNull(orderCol);
        assertEquals("display_order", orderCol.name());
        assertFalse(orderCol.nullable());

        Field createdField = CareerProfileEducation.class.getDeclaredField("createdAt");
        Column createdCol = createdField.getAnnotation(Column.class);
        assertNotNull(createdCol);
        assertEquals("created_at", createdCol.name());
        assertFalse(createdCol.nullable());
        assertFalse(createdCol.updatable());

        Field updatedField = CareerProfileEducation.class.getDeclaredField("updatedAt");
        Column updatedCol = updatedField.getAnnotation(Column.class);
        assertNotNull(updatedCol);
        assertEquals("updated_at", updatedCol.name());
        assertFalse(updatedCol.nullable());
    }

    @Test
    void testGettersAndSetters() {
        User user = new User("eduuser@joblivo.com", "Edu User");
        CareerProfile profile = new CareerProfile(user);

        CareerProfileEducation education = new CareerProfileEducation(
                profile,
                "  Stanford University  ",
                "  B.S.  ",
                "  Computer Science  ",
                EducationLevel.UNDERGRADUATE,
                LocalDate.of(2018, 9, 1),
                LocalDate.of(2022, 6, 15),
                false,
                "  3.9 GPA  ",
                "  Stanford, CA  ",
                "  Dean's Honor List  ",
                1
        );

        assertEquals(profile, education.getCareerProfile());
        assertEquals("Stanford University", education.getInstitutionName());
        assertEquals("B.S.", education.getDegree());
        assertEquals("Computer Science", education.getFieldOfStudy());
        assertEquals(EducationLevel.UNDERGRADUATE, education.getEducationLevel());
        assertEquals(LocalDate.of(2018, 9, 1), education.getStartDate());
        assertEquals(LocalDate.of(2022, 6, 15), education.getEndDate());
        assertFalse(education.isCurrentlyStudying());
        assertEquals("3.9 GPA", education.getGrade());
        assertEquals("Stanford, CA", education.getLocation());
        assertEquals("Dean's Honor List", education.getDescription());
        assertEquals(1, education.getDisplayOrder());

        // Update fields
        education.setInstitutionName("  MIT  ");
        education.setDegree("  M.Eng.  ");
        education.setFieldOfStudy("  EECS  ");
        education.setEducationLevel(EducationLevel.POSTGRADUATE);
        education.setStartDate(LocalDate.of(2022, 9, 1));
        education.setEndDate(null);
        education.setCurrentlyStudying(true);
        education.setGrade(null);
        education.setLocation("  Cambridge, MA  ");
        education.setDescription(null);
        education.setDisplayOrder(0);

        assertEquals("MIT", education.getInstitutionName());
        assertEquals("M.Eng.", education.getDegree());
        assertEquals("EECS", education.getFieldOfStudy());
        assertEquals(EducationLevel.POSTGRADUATE, education.getEducationLevel());
        assertEquals(LocalDate.of(2022, 9, 1), education.getStartDate());
        assertNull(education.getEndDate());
        assertTrue(education.isCurrentlyStudying());
        assertNull(education.getGrade());
        assertEquals("Cambridge, MA", education.getLocation());
        assertNull(education.getDescription());
        assertEquals(0, education.getDisplayOrder());
    }

    @Test
    void testLifecycleCallbacks() throws Exception {
        Method onCreate = CareerProfileEducation.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        Method onUpdate = CareerProfileEducation.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);

        assertNotNull(onCreate.getAnnotation(PrePersist.class));
        assertNotNull(onUpdate.getAnnotation(PreUpdate.class));

        User user = new User("eduuser@joblivo.com", "Edu User");
        CareerProfile profile = new CareerProfile(user);
        CareerProfileEducation education = new CareerProfileEducation(
                profile, "Harvard", "A.B.", "Economics", EducationLevel.UNDERGRADUATE,
                null, null, true, null, null, null, 0
        );

        assertNull(education.getCreatedAt());
        assertNull(education.getUpdatedAt());

        onCreate.invoke(education);
        assertNotNull(education.getCreatedAt());
        assertNotNull(education.getUpdatedAt());

        Instant originalUpdatedAt = education.getUpdatedAt();
        Thread.sleep(2);
        onUpdate.invoke(education);
        assertTrue(education.getUpdatedAt().isAfter(originalUpdatedAt) || education.getUpdatedAt().equals(originalUpdatedAt));
    }

    @Test
    void testEqualsAndHashCode() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        User user = new User("eduuser@joblivo.com", "Edu User");
        CareerProfile profile = new CareerProfile(user);

        CareerProfileEducation e1 = new CareerProfileEducation(
                id1, profile, "Harvard", "A.B.", "Economics", EducationLevel.UNDERGRADUATE,
                LocalDate.of(2016, 9, 1), LocalDate.of(2020, 5, 1), false, "3.8", "Cambridge", "Desc",
                0, Instant.now(), Instant.now()
        );
        CareerProfileEducation e1Same = new CareerProfileEducation(
                id1, profile, "Yale", "B.S.", "Physics", EducationLevel.UNDERGRADUATE,
                LocalDate.of(2016, 9, 1), LocalDate.of(2020, 5, 1), false, "3.8", "New Haven", "Desc",
                0, Instant.now(), Instant.now()
        );
        CareerProfileEducation e2 = new CareerProfileEducation(
                id2, profile, "Harvard", "A.B.", "Economics", EducationLevel.UNDERGRADUATE,
                LocalDate.of(2016, 9, 1), LocalDate.of(2020, 5, 1), false, "3.8", "Cambridge", "Desc",
                0, Instant.now(), Instant.now()
        );

        assertEquals(e1, e1Same);
        assertEquals(e1.hashCode(), e1Same.hashCode());
        assertNotEquals(e1, e2);
        assertNotEquals(e1, null);
        assertNotEquals(e1, new Object());
    }

    @Test
    void testRequestNormalization() {
        EducationRequest request = new EducationRequest(
                "   Indian Institute of Technology   ",
                "   B.Tech   ",
                "   Computer Science   ",
                EducationLevel.UNDERGRADUATE,
                LocalDate.of(2020, 8, 1),
                LocalDate.of(2024, 5, 1),
                null,
                "   8.9 CGPA   ",
                "   Delhi, India   ",
                "   Specialization in Systems   ",
                null
        );

        assertEquals("Indian Institute of Technology", request.institutionName());
        assertEquals("B.Tech", request.degree());
        assertEquals("Computer Science", request.fieldOfStudy());
        assertEquals(EducationLevel.UNDERGRADUATE, request.educationLevel());
        assertEquals("8.9 CGPA", request.grade());
        assertEquals("Delhi, India", request.location());
        assertEquals("Specialization in Systems", request.description());
        assertFalse(request.currentlyStudying());
        assertEquals(0, request.displayOrder());
    }

    @Test
    void testDateRangeValidationRule() {
        EducationRequest invalidDates = new EducationRequest(
                "College", null, null, EducationLevel.UNDERGRADUATE,
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2022, 6, 1),
                false, null, null, null, 0
        );
        assertFalse(invalidDates.isDateRangeValid());

        EducationRequest sameDates = new EducationRequest(
                "College", null, null, EducationLevel.UNDERGRADUATE,
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2024, 6, 1),
                false, null, null, null, 0
        );
        assertTrue(sameDates.isDateRangeValid());

        EducationRequest validDates = new EducationRequest(
                "College", null, null, EducationLevel.UNDERGRADUATE,
                LocalDate.of(2020, 6, 1),
                LocalDate.of(2024, 6, 1),
                false, null, null, null, 0
        );
        assertTrue(validDates.isDateRangeValid());

        EducationRequest nullDates = new EducationRequest(
                "College", null, null, EducationLevel.UNDERGRADUATE,
                null, null, false, null, null, null, 0
        );
        assertTrue(nullDates.isDateRangeValid());
    }

    @Test
    void testCurrentlyStudyingConsistencyRule() {
        EducationRequest invalidStudying = new EducationRequest(
                "College", null, null, EducationLevel.UNDERGRADUATE,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2024, 1, 1),
                true, null, null, null, 0
        );
        assertFalse(invalidStudying.isCurrentlyStudyingValid());

        EducationRequest validStudying = new EducationRequest(
                "College", null, null, EducationLevel.UNDERGRADUATE,
                LocalDate.of(2020, 1, 1),
                null,
                true, null, null, null, 0
        );
        assertTrue(validStudying.isCurrentlyStudyingValid());

        EducationRequest validNotStudying = new EducationRequest(
                "College", null, null, EducationLevel.UNDERGRADUATE,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2024, 1, 1),
                false, null, null, null, 0
        );
        assertTrue(validNotStudying.isCurrentlyStudyingValid());
    }

    @Test
    void testResponseMapping() {
        UUID id = UUID.randomUUID();
        User user = new User("eduuser@joblivo.com", "Edu User");
        CareerProfile profile = new CareerProfile(user);
        Instant now = Instant.now();

        CareerProfileEducation education = new CareerProfileEducation(
                id, profile, "Oxford", "M.Sc.", "Data Science", EducationLevel.POSTGRADUATE,
                LocalDate.of(2023, 10, 1), LocalDate.of(2024, 9, 30), false, "Distinction",
                "Oxford, UK", "Graduated with honors", 2, now, now
        );

        EducationResponse response = EducationResponse.from(education);
        assertEquals(id, response.id());
        assertEquals("Oxford", response.institutionName());
        assertEquals("M.Sc.", response.degree());
        assertEquals("Data Science", response.fieldOfStudy());
        assertEquals(EducationLevel.POSTGRADUATE, response.educationLevel());
        assertEquals(LocalDate.of(2023, 10, 1), response.startDate());
        assertEquals(LocalDate.of(2024, 9, 30), response.endDate());
        assertFalse(response.currentlyStudying());
        assertEquals("Distinction", response.grade());
        assertEquals("Oxford, UK", response.location());
        assertEquals("Graduated with honors", response.description());
        assertEquals(2, response.displayOrder());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());
    }

    @Test
    void testScopeProtectionNoFuturePromptFields() {
        List<String> prohibitedScopeFields = List.of(
                "certificate", "certification", "achievement", "evidence",
                "resume", "score", "salary", "compensation", "interview", "tailoring"
        );

        for (Field field : CareerProfileEducation.class.getDeclaredFields()) {
            String lowerName = field.getName().toLowerCase();
            for (String prohibited : prohibitedScopeFields) {
                assertFalse(
                        lowerName.contains(prohibited),
                        "CareerProfileEducation entity must not contain out-of-scope field: " + field.getName()
                );
            }
        }
    }
}
