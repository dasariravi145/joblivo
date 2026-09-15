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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CareerProfileSkillTest {

    @Test
    void testSkillCategoryEnumValues() {
        List<String> names = Arrays.stream(SkillCategory.values())
                .map(Enum::name)
                .toList();

        assertEquals(12, names.size());
        assertTrue(names.contains("PROGRAMMING_LANGUAGE"));
        assertTrue(names.contains("CLOUD"));
        assertTrue(names.contains("DEVOPS"));
        assertTrue(names.contains("DATABASE"));
        assertTrue(names.contains("FRAMEWORK"));
        assertTrue(names.contains("TOOL"));
        assertTrue(names.contains("PLATFORM"));
        assertTrue(names.contains("DATA"));
        assertTrue(names.contains("AI_ML"));
        assertTrue(names.contains("SECURITY"));
        assertTrue(names.contains("TESTING"));
        assertTrue(names.contains("OTHER"));
    }

    @Test
    void testSkillProficiencyEnumValues() {
        List<String> names = Arrays.stream(SkillProficiency.values())
                .map(Enum::name)
                .toList();

        assertEquals(4, names.size());
        assertTrue(names.contains("BEGINNER"));
        assertTrue(names.contains("INTERMEDIATE"));
        assertTrue(names.contains("ADVANCED"));
        assertTrue(names.contains("EXPERT"));
    }

    @Test
    void testEntityTableAndIdMapping() throws NoSuchFieldException {
        Table table = CareerProfileSkill.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("career_profile_skills", table.name());

        Field idField = CareerProfileSkill.class.getDeclaredField("id");
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
        Field profileField = CareerProfileSkill.class.getDeclaredField("careerProfile");
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
        Field nameField = CareerProfileSkill.class.getDeclaredField("name");
        Column nameCol = nameField.getAnnotation(Column.class);
        assertNotNull(nameCol);
        assertEquals("name", nameCol.name());
        assertEquals(100, nameCol.length());
        assertFalse(nameCol.nullable());

        Field categoryField = CareerProfileSkill.class.getDeclaredField("category");
        Column catCol = categoryField.getAnnotation(Column.class);
        assertNotNull(catCol);
        assertEquals("category", catCol.name());
        assertEquals(50, catCol.length());
        assertFalse(catCol.nullable());
        Enumerated catEnum = categoryField.getAnnotation(Enumerated.class);
        assertNotNull(catEnum);
        assertEquals(EnumType.STRING, catEnum.value());

        Field profField = CareerProfileSkill.class.getDeclaredField("proficiency");
        Column profCol = profField.getAnnotation(Column.class);
        assertNotNull(profCol);
        assertEquals("proficiency", profCol.name());
        assertEquals(30, profCol.length());
        assertFalse(profCol.nullable());
        Enumerated profEnum = profField.getAnnotation(Enumerated.class);
        assertNotNull(profEnum);
        assertEquals(EnumType.STRING, profEnum.value());

        Field yoeField = CareerProfileSkill.class.getDeclaredField("yearsOfExperience");
        Column yoeCol = yoeField.getAnnotation(Column.class);
        assertNotNull(yoeCol);
        assertEquals("years_of_experience", yoeCol.name());
        assertEquals(4, yoeCol.precision());
        assertEquals(1, yoeCol.scale());

        Field lastUsedField = CareerProfileSkill.class.getDeclaredField("lastUsedDate");
        Column lastUsedCol = lastUsedField.getAnnotation(Column.class);
        assertNotNull(lastUsedCol);
        assertEquals("last_used_date", lastUsedCol.name());

        Field orderField = CareerProfileSkill.class.getDeclaredField("displayOrder");
        Column orderCol = orderField.getAnnotation(Column.class);
        assertNotNull(orderCol);
        assertEquals("display_order", orderCol.name());
        assertFalse(orderCol.nullable());

        Field createdField = CareerProfileSkill.class.getDeclaredField("createdAt");
        Column createdCol = createdField.getAnnotation(Column.class);
        assertNotNull(createdCol);
        assertEquals("created_at", createdCol.name());
        assertFalse(createdCol.nullable());
        assertFalse(createdCol.updatable());

        Field updatedField = CareerProfileSkill.class.getDeclaredField("updatedAt");
        Column updatedCol = updatedField.getAnnotation(Column.class);
        assertNotNull(updatedCol);
        assertEquals("updated_at", updatedCol.name());
        assertFalse(updatedCol.nullable());
    }

    @Test
    void testGettersAndSetters() {
        User user = new User("skilluser@joblivo.com", "Skill User");
        CareerProfile profile = new CareerProfile(user);

        CareerProfileSkill skill = new CareerProfileSkill(
                profile,
                "  PostgreSQL  ",
                SkillCategory.DATABASE,
                SkillProficiency.ADVANCED,
                new BigDecimal("5.5"),
                LocalDate.of(2026, 8, 1),
                1
        );

        assertEquals(profile, skill.getCareerProfile());
        assertEquals("PostgreSQL", skill.getName()); // trimmed automatically
        assertEquals(SkillCategory.DATABASE, skill.getCategory());
        assertEquals(SkillProficiency.ADVANCED, skill.getProficiency());
        assertEquals(new BigDecimal("5.5"), skill.getYearsOfExperience());
        assertEquals(LocalDate.of(2026, 8, 1), skill.getLastUsedDate());
        assertEquals(1, skill.getDisplayOrder());

        // Update fields
        skill.setName("  Java 17  ");
        skill.setCategory(SkillCategory.PROGRAMMING_LANGUAGE);
        skill.setProficiency(SkillProficiency.EXPERT);
        skill.setYearsOfExperience(new BigDecimal("8.0"));
        skill.setLastUsedDate(LocalDate.of(2026, 9, 1));
        skill.setDisplayOrder(0);

        assertEquals("Java 17", skill.getName());
        assertEquals(SkillCategory.PROGRAMMING_LANGUAGE, skill.getCategory());
        assertEquals(SkillProficiency.EXPERT, skill.getProficiency());
        assertEquals(new BigDecimal("8.0"), skill.getYearsOfExperience());
        assertEquals(LocalDate.of(2026, 9, 1), skill.getLastUsedDate());
        assertEquals(0, skill.getDisplayOrder());
    }

    @Test
    void testLifecycleCallbacks() throws Exception {
        Method onCreate = CareerProfileSkill.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        Method onUpdate = CareerProfileSkill.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);

        assertNotNull(onCreate.getAnnotation(PrePersist.class));
        assertNotNull(onUpdate.getAnnotation(PreUpdate.class));

        User user = new User("skilluser@joblivo.com", "Skill User");
        CareerProfile profile = new CareerProfile(user);
        CareerProfileSkill skill = new CareerProfileSkill(
                profile, "AWS", SkillCategory.CLOUD, SkillProficiency.INTERMEDIATE,
                null, null, 0
        );

        assertNull(skill.getCreatedAt());
        assertNull(skill.getUpdatedAt());

        onCreate.invoke(skill);
        assertNotNull(skill.getCreatedAt());
        assertNotNull(skill.getUpdatedAt());

        Instant originalUpdatedAt = skill.getUpdatedAt();
        Thread.sleep(2);
        onUpdate.invoke(skill);
        assertTrue(skill.getUpdatedAt().isAfter(originalUpdatedAt) || skill.getUpdatedAt().equals(originalUpdatedAt));
    }

    @Test
    void testEqualsAndHashCode() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        User user = new User("skilluser@joblivo.com", "Skill User");
        CareerProfile profile = new CareerProfile(user);

        CareerProfileSkill skill1 = new CareerProfileSkill(
                id1, profile, "Kubernetes", SkillCategory.DEVOPS, SkillProficiency.ADVANCED,
                new BigDecimal("3.0"), null, 0, Instant.now(), Instant.now()
        );
        CareerProfileSkill skill1Same = new CareerProfileSkill(
                id1, profile, "Terraform", SkillCategory.TOOL, SkillProficiency.BEGINNER,
                null, null, 1, Instant.now(), Instant.now()
        );
        CareerProfileSkill skill2 = new CareerProfileSkill(
                id2, profile, "Kubernetes", SkillCategory.DEVOPS, SkillProficiency.ADVANCED,
                new BigDecimal("3.0"), null, 0, Instant.now(), Instant.now()
        );

        assertEquals(skill1, skill1Same);
        assertEquals(skill1.hashCode(), skill1Same.hashCode());
        assertNotEquals(skill1, skill2);
        assertNotEquals(skill1, null);
        assertNotEquals(skill1, new Object());
    }

    @Test
    void testRequestNormalization() {
        SkillRequest request = new SkillRequest(
                "   AWS   ",
                SkillCategory.CLOUD,
                SkillProficiency.ADVANCED,
                new BigDecimal("4.0"),
                LocalDate.of(2026, 1, 1),
                null
        );

        assertEquals("AWS", request.name());
        assertEquals(0, request.displayOrder());
    }

    @Test
    void testResponseMapping() {
        UUID id = UUID.randomUUID();
        User user = new User("skilluser@joblivo.com", "Skill User");
        CareerProfile profile = new CareerProfile(user);
        Instant now = Instant.now();

        CareerProfileSkill skill = new CareerProfileSkill(
                id, profile, "Docker", SkillCategory.DEVOPS, SkillProficiency.EXPERT,
                new BigDecimal("6.0"), LocalDate.of(2026, 5, 1), 2, now, now
        );

        SkillResponse response = SkillResponse.from(skill);
        assertEquals(id, response.id());
        assertEquals("Docker", response.name());
        assertEquals(SkillCategory.DEVOPS, response.category());
        assertEquals(SkillProficiency.EXPERT, response.proficiency());
        assertEquals(new BigDecimal("6.0"), response.yearsOfExperience());
        assertEquals(LocalDate.of(2026, 5, 1), response.lastUsedDate());
        assertEquals(2, response.displayOrder());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());
    }

    @Test
    void testScopeProtectionNoFuturePromptFields() {
        List<String> prohibitedScopeFields = List.of(
                "project", "education", "degree", "certification", "achievement",
                "evidence", "resume", "score", "salary", "compensation", "interview"
        );

        for (Field field : CareerProfileSkill.class.getDeclaredFields()) {
            String lowerName = field.getName().toLowerCase();
            for (String prohibited : prohibitedScopeFields) {
                assertFalse(
                        lowerName.contains(prohibited),
                        "CareerProfileSkill entity must not contain out-of-scope field: " + field.getName()
                );
            }
        }
    }
}
