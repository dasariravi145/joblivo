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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CareerProfileAchievementTest {

    @Test
    void testEntityTableAndIdMapping() throws NoSuchFieldException {
        Table table = CareerProfileAchievement.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("career_profile_achievements", table.name());

        Field idField = CareerProfileAchievement.class.getDeclaredField("id");
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
        Field profileField = CareerProfileAchievement.class.getDeclaredField("careerProfile");
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
        Field titleField = CareerProfileAchievement.class.getDeclaredField("title");
        Column titleCol = titleField.getAnnotation(Column.class);
        assertNotNull(titleCol);
        assertEquals("title", titleCol.name());
        assertEquals(150, titleCol.length());
        assertFalse(titleCol.nullable());

        Field typeField = CareerProfileAchievement.class.getDeclaredField("achievementType");
        Column typeCol = typeField.getAnnotation(Column.class);
        assertNotNull(typeCol);
        assertEquals("achievement_type", typeCol.name());
        assertEquals(50, typeCol.length());
        assertFalse(typeCol.nullable());
        Enumerated enumerated = typeField.getAnnotation(Enumerated.class);
        assertNotNull(enumerated);
        assertEquals(EnumType.STRING, enumerated.value());

        Field orgField = CareerProfileAchievement.class.getDeclaredField("issuingOrganization");
        Column orgCol = orgField.getAnnotation(Column.class);
        assertNotNull(orgCol);
        assertEquals("issuing_organization", orgCol.name());
        assertEquals(150, orgCol.length());

        Field dateField = CareerProfileAchievement.class.getDeclaredField("achievementDate");
        Column dateCol = dateField.getAnnotation(Column.class);
        assertNotNull(dateCol);
        assertEquals("achievement_date", dateCol.name());

        Field descField = CareerProfileAchievement.class.getDeclaredField("description");
        Column descCol = descField.getAnnotation(Column.class);
        assertNotNull(descCol);
        assertEquals("description", descCol.name());
        assertEquals("TEXT", descCol.columnDefinition());

        Field urlField = CareerProfileAchievement.class.getDeclaredField("url");
        Column urlCol = urlField.getAnnotation(Column.class);
        assertNotNull(urlCol);
        assertEquals("url", urlCol.name());
        assertEquals(500, urlCol.length());

        Field orderField = CareerProfileAchievement.class.getDeclaredField("displayOrder");
        Column orderCol = orderField.getAnnotation(Column.class);
        assertNotNull(orderCol);
        assertEquals("display_order", orderCol.name());
        assertFalse(orderCol.nullable());

        Field createdField = CareerProfileAchievement.class.getDeclaredField("createdAt");
        Column createdCol = createdField.getAnnotation(Column.class);
        assertNotNull(createdCol);
        assertEquals("created_at", createdCol.name());
        assertFalse(createdCol.nullable());
        assertFalse(createdCol.updatable());

        Field updatedField = CareerProfileAchievement.class.getDeclaredField("updatedAt");
        Column updatedCol = updatedField.getAnnotation(Column.class);
        assertNotNull(updatedCol);
        assertEquals("updated_at", updatedCol.name());
        assertFalse(updatedCol.nullable());
    }

    @Test
    void testAchievementTypeEnumValues() {
        assertEquals(12, AchievementType.values().length);
        assertNotNull(AchievementType.valueOf("AWARD"));
        assertNotNull(AchievementType.valueOf("RECOGNITION"));
        assertNotNull(AchievementType.valueOf("PROMOTION"));
        assertNotNull(AchievementType.valueOf("PERFORMANCE"));
        assertNotNull(AchievementType.valueOf("COMPETITION"));
        assertNotNull(AchievementType.valueOf("HACKATHON"));
        assertNotNull(AchievementType.valueOf("PUBLICATION"));
        assertNotNull(AchievementType.valueOf("PATENT"));
        assertNotNull(AchievementType.valueOf("LEADERSHIP"));
        assertNotNull(AchievementType.valueOf("ACADEMIC"));
        assertNotNull(AchievementType.valueOf("PROJECT"));
        assertNotNull(AchievementType.valueOf("OTHER"));
    }

    @Test
    void testGettersAndSetters() {
        User user = new User("achiever@joblivo.com", "Achiever User");
        CareerProfile profile = new CareerProfile(user);

        CareerProfileAchievement achievement = new CareerProfileAchievement(
                profile,
                "  Employee of the Year  ",
                AchievementType.AWARD,
                "  Acme Corp  ",
                LocalDate.of(2023, 12, 15),
                "  Recognized for exceptional leadership  ",
                "  https://acme.com/awards/2023  ",
                1
        );

        assertEquals(profile, achievement.getCareerProfile());
        assertEquals("Employee of the Year", achievement.getTitle());
        assertEquals(AchievementType.AWARD, achievement.getAchievementType());
        assertEquals("Acme Corp", achievement.getIssuingOrganization());
        assertEquals(LocalDate.of(2023, 12, 15), achievement.getAchievementDate());
        assertEquals("Recognized for exceptional leadership", achievement.getDescription());
        assertEquals("https://acme.com/awards/2023", achievement.getUrl());
        assertEquals(1, achievement.getDisplayOrder());

        // Update fields
        achievement.setTitle("  Promoted to Principal Engineer  ");
        achievement.setAchievementType(AchievementType.PROMOTION);
        achievement.setIssuingOrganization("  Acme Corporation  ");
        achievement.setAchievementDate(LocalDate.of(2024, 1, 1));
        achievement.setDescription(null);
        achievement.setUrl(null);
        achievement.setDisplayOrder(0);

        assertEquals("Promoted to Principal Engineer", achievement.getTitle());
        assertEquals(AchievementType.PROMOTION, achievement.getAchievementType());
        assertEquals("Acme Corporation", achievement.getIssuingOrganization());
        assertEquals(LocalDate.of(2024, 1, 1), achievement.getAchievementDate());
        assertNull(achievement.getDescription());
        assertNull(achievement.getUrl());
        assertEquals(0, achievement.getDisplayOrder());
    }

    @Test
    void testLifecycleCallbacks() throws Exception {
        Method onCreate = CareerProfileAchievement.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        Method onUpdate = CareerProfileAchievement.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);

        assertNotNull(onCreate.getAnnotation(PrePersist.class));
        assertNotNull(onUpdate.getAnnotation(PreUpdate.class));

        User user = new User("achiever@joblivo.com", "Achiever User");
        CareerProfile profile = new CareerProfile(user);
        CareerProfileAchievement achievement = new CareerProfileAchievement(
                profile, "Hackathon Winner", AchievementType.HACKATHON, null, null, null, null, 0
        );

        assertNull(achievement.getCreatedAt());
        assertNull(achievement.getUpdatedAt());

        onCreate.invoke(achievement);
        assertNotNull(achievement.getCreatedAt());
        assertNotNull(achievement.getUpdatedAt());

        Instant originalUpdatedAt = achievement.getUpdatedAt();
        Thread.sleep(2);
        onUpdate.invoke(achievement);
        assertTrue(achievement.getUpdatedAt().isAfter(originalUpdatedAt) || achievement.getUpdatedAt().equals(originalUpdatedAt));
    }

    @Test
    void testEqualsAndHashCode() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        User user = new User("achiever@joblivo.com", "Achiever User");
        CareerProfile profile = new CareerProfile(user);

        CareerProfileAchievement a1 = new CareerProfileAchievement(
                id1, profile, "Award 1", AchievementType.AWARD, "Org",
                LocalDate.of(2023, 1, 1), "Desc", "https://example.com", 0, Instant.now(), Instant.now()
        );
        CareerProfileAchievement a1Same = new CareerProfileAchievement(
                id1, profile, "Award 2", AchievementType.PATENT, "Different Org",
                LocalDate.of(2024, 1, 1), "Desc", "https://other.com", 1, Instant.now(), Instant.now()
        );
        CareerProfileAchievement a2 = new CareerProfileAchievement(
                id2, profile, "Award 1", AchievementType.AWARD, "Org",
                LocalDate.of(2023, 1, 1), "Desc", "https://example.com", 0, Instant.now(), Instant.now()
        );

        assertEquals(a1, a1Same);
        assertEquals(a1.hashCode(), a1Same.hashCode());
        assertNotEquals(a1, a2);
        assertNotEquals(a1, null);
        assertNotEquals(a1, new Object());
    }

    @Test
    void testRequestNormalization() {
        AchievementRequest request = new AchievementRequest(
                "   First Place Winner   ",
                AchievementType.COMPETITION,
                "   Global Hackathon Org   ",
                LocalDate.of(2023, 10, 1),
                "   Built innovative AI assistant   ",
                "   https://hackathon.org/results/1   ",
                null
        );

        assertEquals("First Place Winner", request.title());
        assertEquals(AchievementType.COMPETITION, request.achievementType());
        assertEquals("Global Hackathon Org", request.issuingOrganization());
        assertEquals(LocalDate.of(2023, 10, 1), request.achievementDate());
        assertEquals("Built innovative AI assistant", request.description());
        assertEquals("https://hackathon.org/results/1", request.url());
        assertEquals(0, request.displayOrder());
    }

    @Test
    void testResponseMapping() {
        UUID id = UUID.randomUUID();
        User user = new User("achiever@joblivo.com", "Achiever User");
        CareerProfile profile = new CareerProfile(user);
        Instant now = Instant.now();

        CareerProfileAchievement achievement = new CareerProfileAchievement(
                id, profile, "US Patent 9,999,999", AchievementType.PATENT,
                "USPTO", LocalDate.of(2022, 5, 10),
                "System and method for distributed indexing", "https://patents.google.com/patent/US9999999",
                2, now, now
        );

        AchievementResponse response = AchievementResponse.from(achievement);
        assertEquals(id, response.id());
        assertEquals("US Patent 9,999,999", response.title());
        assertEquals(AchievementType.PATENT, response.achievementType());
        assertEquals("USPTO", response.issuingOrganization());
        assertEquals(LocalDate.of(2022, 5, 10), response.achievementDate());
        assertEquals("System and method for distributed indexing", response.description());
        assertEquals("https://patents.google.com/patent/US9999999", response.url());
        assertEquals(2, response.displayOrder());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());
    }

    @Test
    void testScopeProtectionNoFuturePromptFields() {
        List<String> prohibitedScopeFields = List.of(
                "vault", "evidence", "verified", "verifier", "resume",
                "score", "salary", "compensation", "interview"
        );

        for (Field field : CareerProfileAchievement.class.getDeclaredFields()) {
            String lowerName = field.getName().toLowerCase();
            for (String prohibited : prohibitedScopeFields) {
                assertFalse(
                        lowerName.contains(prohibited),
                        "CareerProfileAchievement entity must not contain out-of-scope field: " + field.getName()
                );
            }
        }
    }
}
