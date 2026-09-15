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

class CareerProfileProjectTest {

    @Test
    void testProjectTypeEnumValues() {
        List<String> names = Arrays.stream(ProjectType.values())
                .map(Enum::name)
                .toList();

        assertEquals(6, names.size());
        assertTrue(names.contains("PROFESSIONAL"));
        assertTrue(names.contains("PERSONAL"));
        assertTrue(names.contains("ACADEMIC"));
        assertTrue(names.contains("OPEN_SOURCE"));
        assertTrue(names.contains("FREELANCE"));
        assertTrue(names.contains("OTHER"));
    }

    @Test
    void testEntityTableAndIdMapping() throws NoSuchFieldException {
        Table table = CareerProfileProject.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("career_profile_projects", table.name());

        Field idField = CareerProfileProject.class.getDeclaredField("id");
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
        Field profileField = CareerProfileProject.class.getDeclaredField("careerProfile");
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
        Field nameField = CareerProfileProject.class.getDeclaredField("projectName");
        Column nameCol = nameField.getAnnotation(Column.class);
        assertNotNull(nameCol);
        assertEquals("project_name", nameCol.name());
        assertEquals(100, nameCol.length());
        assertFalse(nameCol.nullable());

        Field typeField = CareerProfileProject.class.getDeclaredField("projectType");
        Column typeCol = typeField.getAnnotation(Column.class);
        assertNotNull(typeCol);
        assertEquals("project_type", typeCol.name());
        assertEquals(30, typeCol.length());
        assertFalse(typeCol.nullable());
        Enumerated typeEnum = typeField.getAnnotation(Enumerated.class);
        assertNotNull(typeEnum);
        assertEquals(EnumType.STRING, typeEnum.value());

        Field roleField = CareerProfileProject.class.getDeclaredField("role");
        Column roleCol = roleField.getAnnotation(Column.class);
        assertNotNull(roleCol);
        assertEquals("role", roleCol.name());
        assertEquals(100, roleCol.length());

        Field descField = CareerProfileProject.class.getDeclaredField("description");
        Column descCol = descField.getAnnotation(Column.class);
        assertNotNull(descCol);
        assertEquals("description", descCol.name());
        assertEquals("TEXT", descCol.columnDefinition());

        Field startField = CareerProfileProject.class.getDeclaredField("startDate");
        Column startCol = startField.getAnnotation(Column.class);
        assertNotNull(startCol);
        assertEquals("start_date", startCol.name());

        Field endField = CareerProfileProject.class.getDeclaredField("endDate");
        Column endCol = endField.getAnnotation(Column.class);
        assertNotNull(endCol);
        assertEquals("end_date", endCol.name());

        Field activeField = CareerProfileProject.class.getDeclaredField("currentlyActive");
        Column activeCol = activeField.getAnnotation(Column.class);
        assertNotNull(activeCol);
        assertEquals("currently_active", activeCol.name());
        assertFalse(activeCol.nullable());

        Field urlField = CareerProfileProject.class.getDeclaredField("projectUrl");
        Column urlCol = urlField.getAnnotation(Column.class);
        assertNotNull(urlCol);
        assertEquals("project_url", urlCol.name());
        assertEquals(500, urlCol.length());

        Field orderField = CareerProfileProject.class.getDeclaredField("displayOrder");
        Column orderCol = orderField.getAnnotation(Column.class);
        assertNotNull(orderCol);
        assertEquals("display_order", orderCol.name());
        assertFalse(orderCol.nullable());

        Field createdField = CareerProfileProject.class.getDeclaredField("createdAt");
        Column createdCol = createdField.getAnnotation(Column.class);
        assertNotNull(createdCol);
        assertEquals("created_at", createdCol.name());
        assertFalse(createdCol.nullable());
        assertFalse(createdCol.updatable());

        Field updatedField = CareerProfileProject.class.getDeclaredField("updatedAt");
        Column updatedCol = updatedField.getAnnotation(Column.class);
        assertNotNull(updatedCol);
        assertEquals("updated_at", updatedCol.name());
        assertFalse(updatedCol.nullable());
    }

    @Test
    void testGettersAndSetters() {
        User user = new User("projectuser@joblivo.com", "Project User");
        CareerProfile profile = new CareerProfile(user);

        CareerProfileProject project = new CareerProfileProject(
                profile,
                "  AI Career Engine  ",
                ProjectType.PROFESSIONAL,
                "  Lead Architect  ",
                "  Built matching algorithms  ",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 1, 1),
                false,
                "  https://joblivo.com  ",
                1
        );

        assertEquals(profile, project.getCareerProfile());
        assertEquals("AI Career Engine", project.getProjectName());
        assertEquals(ProjectType.PROFESSIONAL, project.getProjectType());
        assertEquals("Lead Architect", project.getRole());
        assertEquals("Built matching algorithms", project.getDescription());
        assertEquals(LocalDate.of(2024, 1, 1), project.getStartDate());
        assertEquals(LocalDate.of(2025, 1, 1), project.getEndDate());
        assertFalse(project.isCurrentlyActive());
        assertEquals("https://joblivo.com", project.getProjectUrl());
        assertEquals(1, project.getDisplayOrder());

        // Update fields
        project.setProjectName("  Joblivo Platform  ");
        project.setProjectType(ProjectType.OPEN_SOURCE);
        project.setRole("  Maintainer  ");
        project.setDescription("  Open core platform  ");
        project.setStartDate(LocalDate.of(2025, 2, 1));
        project.setEndDate(null);
        project.setCurrentlyActive(true);
        project.setProjectUrl(null);
        project.setDisplayOrder(0);

        assertEquals("Joblivo Platform", project.getProjectName());
        assertEquals(ProjectType.OPEN_SOURCE, project.getProjectType());
        assertEquals("Maintainer", project.getRole());
        assertEquals("Open core platform", project.getDescription());
        assertEquals(LocalDate.of(2025, 2, 1), project.getStartDate());
        assertNull(project.getEndDate());
        assertTrue(project.isCurrentlyActive());
        assertNull(project.getProjectUrl());
        assertEquals(0, project.getDisplayOrder());
    }

    @Test
    void testLifecycleCallbacks() throws Exception {
        Method onCreate = CareerProfileProject.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        Method onUpdate = CareerProfileProject.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);

        assertNotNull(onCreate.getAnnotation(PrePersist.class));
        assertNotNull(onUpdate.getAnnotation(PreUpdate.class));

        User user = new User("projectuser@joblivo.com", "Project User");
        CareerProfile profile = new CareerProfile(user);
        CareerProfileProject project = new CareerProfileProject(
                profile, "Joblivo", ProjectType.PERSONAL, null, null, null, null, true, null, 0
        );

        assertNull(project.getCreatedAt());
        assertNull(project.getUpdatedAt());

        onCreate.invoke(project);
        assertNotNull(project.getCreatedAt());
        assertNotNull(project.getUpdatedAt());

        Instant originalUpdatedAt = project.getUpdatedAt();
        Thread.sleep(2);
        onUpdate.invoke(project);
        assertTrue(project.getUpdatedAt().isAfter(originalUpdatedAt) || project.getUpdatedAt().equals(originalUpdatedAt));
    }

    @Test
    void testEqualsAndHashCode() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        User user = new User("projectuser@joblivo.com", "Project User");
        CareerProfile profile = new CareerProfile(user);

        CareerProfileProject p1 = new CareerProfileProject(
                id1, profile, "Project Alpha", ProjectType.PROFESSIONAL, "Dev", "Desc",
                LocalDate.of(2024, 1, 1), null, true, null, 0, Instant.now(), Instant.now()
        );
        CareerProfileProject p1Same = new CareerProfileProject(
                id1, profile, "Project Beta", ProjectType.PERSONAL, "Lead", "Desc",
                LocalDate.of(2024, 1, 1), null, true, null, 0, Instant.now(), Instant.now()
        );
        CareerProfileProject p2 = new CareerProfileProject(
                id2, profile, "Project Alpha", ProjectType.PROFESSIONAL, "Dev", "Desc",
                LocalDate.of(2024, 1, 1), null, true, null, 0, Instant.now(), Instant.now()
        );

        assertEquals(p1, p1Same);
        assertEquals(p1.hashCode(), p1Same.hashCode());
        assertNotEquals(p1, p2);
        assertNotEquals(p1, null);
        assertNotEquals(p1, new Object());
    }

    @Test
    void testRequestNormalization() {
        ProjectRequest request = new ProjectRequest(
                "   Joblivo Platform   ",
                ProjectType.PERSONAL,
                "   Creator   ",
                "   Career management tools   ",
                LocalDate.of(2025, 1, 1),
                null,
                null,
                "   https://joblivo.com   ",
                null
        );

        assertEquals("Joblivo Platform", request.projectName());
        assertEquals("Creator", request.role());
        assertEquals("Career management tools", request.description());
        assertEquals("https://joblivo.com", request.projectUrl());
        assertFalse(request.currentlyActive());
        assertEquals(0, request.displayOrder());
    }

    @Test
    void testDateRangeValidationRule() {
        ProjectRequest invalidDates = new ProjectRequest(
                "Project", ProjectType.PROFESSIONAL, null, null,
                LocalDate.of(2025, 5, 1),
                LocalDate.of(2024, 5, 1),
                false, null, 0
        );
        assertFalse(invalidDates.isDateRangeValid());

        ProjectRequest sameDates = new ProjectRequest(
                "Project", ProjectType.PROFESSIONAL, null, null,
                LocalDate.of(2025, 5, 1),
                LocalDate.of(2025, 5, 1),
                false, null, 0
        );
        assertTrue(sameDates.isDateRangeValid());

        ProjectRequest validDates = new ProjectRequest(
                "Project", ProjectType.PROFESSIONAL, null, null,
                LocalDate.of(2024, 5, 1),
                LocalDate.of(2025, 5, 1),
                false, null, 0
        );
        assertTrue(validDates.isDateRangeValid());

        ProjectRequest nullDates = new ProjectRequest(
                "Project", ProjectType.PROFESSIONAL, null, null,
                null, null, false, null, 0
        );
        assertTrue(nullDates.isDateRangeValid());
    }

    @Test
    void testCurrentlyActiveConsistencyRule() {
        ProjectRequest invalidActive = new ProjectRequest(
                "Project", ProjectType.PROFESSIONAL, null, null,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 1, 1),
                true, null, 0
        );
        assertFalse(invalidActive.isCurrentlyActiveValid());

        ProjectRequest validActive = new ProjectRequest(
                "Project", ProjectType.PROFESSIONAL, null, null,
                LocalDate.of(2024, 1, 1),
                null,
                true, null, 0
        );
        assertTrue(validActive.isCurrentlyActiveValid());

        ProjectRequest validInactive = new ProjectRequest(
                "Project", ProjectType.PROFESSIONAL, null, null,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 1, 1),
                false, null, 0
        );
        assertTrue(validInactive.isCurrentlyActiveValid());
    }

    @Test
    void testResponseMapping() {
        UUID id = UUID.randomUUID();
        User user = new User("projectuser@joblivo.com", "Project User");
        CareerProfile profile = new CareerProfile(user);
        Instant now = Instant.now();

        CareerProfileProject project = new CareerProfileProject(
                id, profile, "Joblivo", ProjectType.OPEN_SOURCE, "Author",
                "Career Platform", LocalDate.of(2025, 1, 1), null, true,
                "https://joblivo.com", 1, now, now
        );

        ProjectResponse response = ProjectResponse.from(project);
        assertEquals(id, response.id());
        assertEquals("Joblivo", response.projectName());
        assertEquals(ProjectType.OPEN_SOURCE, response.projectType());
        assertEquals("Author", response.role());
        assertEquals("Career Platform", response.description());
        assertEquals(LocalDate.of(2025, 1, 1), response.startDate());
        assertNull(response.endDate());
        assertTrue(response.currentlyActive());
        assertEquals("https://joblivo.com", response.projectUrl());
        assertEquals(1, response.displayOrder());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());
    }

    @Test
    void testScopeProtectionNoFuturePromptFields() {
        List<String> prohibitedScopeFields = List.of(
                "technology", "technologies", "skill", "skills", "achievement", "evidence",
                "resume", "score", "salary", "compensation", "interview"
        );

        for (Field field : CareerProfileProject.class.getDeclaredFields()) {
            String lowerName = field.getName().toLowerCase();
            for (String prohibited : prohibitedScopeFields) {
                assertFalse(
                        lowerName.contains(prohibited),
                        "CareerProfileProject entity must not contain out-of-scope field: " + field.getName()
                );
            }
        }
    }
}
