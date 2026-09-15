package com.joblivo.profile;

import com.joblivo.user.User;
import com.joblivo.user.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CareerProfileTest {

    @Test
    void testEntityMapsToCareerProfilesTable() {
        Entity entityAnnotation = CareerProfile.class.getAnnotation(Entity.class);
        assertNotNull(entityAnnotation, "CareerProfile must be annotated with @Entity");

        Table tableAnnotation = CareerProfile.class.getAnnotation(Table.class);
        assertNotNull(tableAnnotation, "CareerProfile must be annotated with @Table");
        assertEquals("career_profiles", tableAnnotation.name(),
                "Table name must be exactly 'career_profiles'");
    }

    @Test
    void testUuidIdMappingIsCorrect() throws NoSuchFieldException {
        Field idField = CareerProfile.class.getDeclaredField("id");
        assertEquals(UUID.class, idField.getType(), "ID field type must be java.util.UUID");

        Id idAnnotation = idField.getAnnotation(Id.class);
        assertNotNull(idAnnotation, "ID field must have @Id annotation");

        GeneratedValue generatedValue = idField.getAnnotation(GeneratedValue.class);
        assertNotNull(generatedValue, "ID field must have @GeneratedValue annotation");

        Column columnAnnotation = idField.getAnnotation(Column.class);
        assertNotNull(columnAnnotation, "ID field must have @Column annotation");
        assertEquals("id", columnAnnotation.name());
        assertFalse(columnAnnotation.nullable(), "id column must be NOT NULL");
        assertFalse(columnAnnotation.updatable(), "id column must not be updatable");
    }

    @Test
    void testUserRelationshipMappingIsCorrect() throws NoSuchFieldException {
        Field userField = CareerProfile.class.getDeclaredField("user");
        assertEquals(User.class, userField.getType(), "user field type must be User entity");

        OneToOne oneToOne = userField.getAnnotation(OneToOne.class);
        assertNotNull(oneToOne, "user field must have @OneToOne annotation");
        assertEquals(FetchType.LAZY, oneToOne.fetch(), "user relationship must be FetchType.LAZY");
        assertFalse(oneToOne.optional(), "user relationship must not be optional");

        JoinColumn joinColumn = userField.getAnnotation(JoinColumn.class);
        assertNotNull(joinColumn, "user field must have @JoinColumn annotation");
        assertEquals("user_id", joinColumn.name(), "Join column name must be 'user_id'");
        assertFalse(joinColumn.nullable(), "user_id column must be NOT NULL");
        assertTrue(joinColumn.unique(), "user_id column must be unique for 1-to-1 relationship");

        // Verify User entity remains clean and unpolluted
        Set<String> userFieldNames = Arrays.stream(User.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(userFieldNames.contains("careerProfile"),
                "User entity must remain unidirectional unless explicitly required");
    }

    @Test
    void testTimestampMappingsUseInstant() throws NoSuchFieldException {
        Field createdAtField = CareerProfile.class.getDeclaredField("createdAt");
        assertEquals(Instant.class, createdAtField.getType(), "createdAt must be java.time.Instant");
        Column createdAtCol = createdAtField.getAnnotation(Column.class);
        assertNotNull(createdAtCol);
        assertEquals("created_at", createdAtCol.name());
        assertFalse(createdAtCol.nullable());
        assertFalse(createdAtCol.updatable());

        Field updatedAtField = CareerProfile.class.getDeclaredField("updatedAt");
        assertEquals(Instant.class, updatedAtField.getType(), "updatedAt must be java.time.Instant");
        Column updatedAtCol = updatedAtField.getAnnotation(Column.class);
        assertNotNull(updatedAtCol);
        assertEquals("updated_at", updatedAtCol.name());
        assertFalse(updatedAtCol.nullable());
    }

    private User createPersistedUser(UUID id, String email, String displayName, UserStatus status) {
        try {
            User user = new User(email, displayName, status);
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);

            Field createdAtField = User.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(user, Instant.now());
            return user;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testEntityLifecycleCallbacks() {
        User user = new User("test@joblivo.com", "Test User");
        CareerProfile profile = new CareerProfile(user);

        assertNull(profile.getId());
        assertNull(profile.getCreatedAt());
        assertNull(profile.getUpdatedAt());

        profile.onCreate();

        assertNotNull(profile.getCreatedAt());
        assertNotNull(profile.getUpdatedAt());

        Instant initialUpdatedAt = profile.getUpdatedAt();
        profile.onUpdate();

        assertNotNull(profile.getUpdatedAt());
        assertFalse(profile.getUpdatedAt().isBefore(initialUpdatedAt),
                "updatedAt must be updated on onUpdate");
    }

    @Test
    void testConstructorsAndMutators() {
        User user = new User("user@joblivo.com", "Test User");
        CareerProfile profile = new CareerProfile(user);

        assertEquals(user, profile.getUser());

        User newUser = new User("other@joblivo.com", "Other User");
        profile.setUser(newUser);
        assertEquals(newUser, profile.getUser());

        assertThrows(NullPointerException.class, () -> new CareerProfile(null));
        assertThrows(NullPointerException.class, () -> profile.setUser(null));
    }

    @Test
    void testEqualsAndHashCodeBasedOnId() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        User user = new User("a@b.com", "User");
        Instant now = Instant.now();

        CareerProfile p1 = new CareerProfile(id1, user, now, now);
        CareerProfile p1Duplicate = new CareerProfile(id1, user, now, now);
        CareerProfile p2 = new CareerProfile(id2, user, now, now);

        assertEquals(p1, p1Duplicate, "Entities with identical IDs must be equal");
        assertNotEquals(p1, p2, "Entities with distinct IDs must not be equal");
        assertEquals(p1.hashCode(), p1Duplicate.hashCode());
    }

    @Test
    void testToStringSanitization() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = createPersistedUser(userId, "audit@joblivo.com", "Audit User", UserStatus.ACTIVE);

        CareerProfile profile = new CareerProfile(profileId, user, now, now);
        String stringRep = profile.toString();

        assertNotNull(stringRep);
        assertTrue(stringRep.contains(profileId.toString()));
        assertTrue(stringRep.contains(userId.toString()));
        assertFalse(stringRep.contains("audit@joblivo.com"), "toString should avoid unnecessary user field dumps");
    }

    @Test
    void testWorkModeEnumContainsExpectedValues() {
        WorkMode[] values = WorkMode.values();
        assertEquals(4, values.length);
        assertEquals(WorkMode.REMOTE, WorkMode.valueOf("REMOTE"));
        assertEquals(WorkMode.HYBRID, WorkMode.valueOf("HYBRID"));
        assertEquals(WorkMode.ONSITE, WorkMode.valueOf("ONSITE"));
        assertEquals(WorkMode.FLEXIBLE, WorkMode.valueOf("FLEXIBLE"));
    }

    @Test
    void testCoreDetailFieldMappings() throws NoSuchFieldException {
        Field headline = CareerProfile.class.getDeclaredField("professionalHeadline");
        assertEquals(String.class, headline.getType());
        Column headlineCol = headline.getAnnotation(Column.class);
        assertNotNull(headlineCol);
        assertEquals("professional_headline", headlineCol.name());
        assertEquals(200, headlineCol.length());

        Field title = CareerProfile.class.getDeclaredField("currentTitle");
        assertEquals(String.class, title.getType());
        Column titleCol = title.getAnnotation(Column.class);
        assertNotNull(titleCol);
        assertEquals("current_title", titleCol.name());
        assertEquals(100, titleCol.length());

        Field company = CareerProfile.class.getDeclaredField("currentCompany");
        assertEquals(String.class, company.getType());
        Column companyCol = company.getAnnotation(Column.class);
        assertNotNull(companyCol);
        assertEquals("current_company", companyCol.name());
        assertEquals(100, companyCol.length());

        Field exp = CareerProfile.class.getDeclaredField("totalExperienceMonths");
        assertEquals(Integer.class, exp.getType());
        Column expCol = exp.getAnnotation(Column.class);
        assertNotNull(expCol);
        assertEquals("total_experience_months", expCol.name());

        Field loc = CareerProfile.class.getDeclaredField("currentLocation");
        assertEquals(String.class, loc.getType());
        Column locCol = loc.getAnnotation(Column.class);
        assertNotNull(locCol);
        assertEquals("current_location", locCol.name());
        assertEquals(100, locCol.length());

        Field prefLoc = CareerProfile.class.getDeclaredField("preferredWorkLocation");
        assertEquals(String.class, prefLoc.getType());
        Column prefLocCol = prefLoc.getAnnotation(Column.class);
        assertNotNull(prefLocCol);
        assertEquals("preferred_work_location", prefLocCol.name());
        assertEquals(100, prefLocCol.length());

        Field mode = CareerProfile.class.getDeclaredField("preferredWorkMode");
        assertEquals(WorkMode.class, mode.getType());
        jakarta.persistence.Enumerated enumerated = mode.getAnnotation(jakarta.persistence.Enumerated.class);
        assertNotNull(enumerated);
        assertEquals(jakarta.persistence.EnumType.STRING, enumerated.value());
        Column modeCol = mode.getAnnotation(Column.class);
        assertNotNull(modeCol);
        assertEquals("preferred_work_mode", modeCol.name());
        assertEquals(20, modeCol.length());

        Field notice = CareerProfile.class.getDeclaredField("noticePeriodDays");
        assertEquals(Integer.class, notice.getType());
        Column noticeCol = notice.getAnnotation(Column.class);
        assertNotNull(noticeCol);
        assertEquals("notice_period_days", noticeCol.name());
    }

    @Test
    void testCoreDetailGettersAndSetters() {
        User user = new User("core@joblivo.com", "Core User");
        CareerProfile profile = new CareerProfile(user);

        profile.setProfessionalHeadline("Lead Distributed Systems Architect");
        profile.setCurrentTitle("Staff Engineer");
        profile.setCurrentCompany("Acme Corp");
        profile.setTotalExperienceMonths(96);
        profile.setCurrentLocation("San Francisco, CA");
        profile.setPreferredWorkLocation("Remote / California");
        profile.setPreferredWorkMode(WorkMode.REMOTE);
        profile.setNoticePeriodDays(30);

        assertEquals("Lead Distributed Systems Architect", profile.getProfessionalHeadline());
        assertEquals("Staff Engineer", profile.getCurrentTitle());
        assertEquals("Acme Corp", profile.getCurrentCompany());
        assertEquals(96, profile.getTotalExperienceMonths());
        assertEquals("San Francisco, CA", profile.getCurrentLocation());
        assertEquals("Remote / California", profile.getPreferredWorkLocation());
        assertEquals(WorkMode.REMOTE, profile.getPreferredWorkMode());
        assertEquals(30, profile.getNoticePeriodDays());
    }

    @Test
    void testScopeProtectionNoDetailFieldsInEntity() {
        List<String> prohibitedScopeFields = List.of(
                "skill", "education", "project", "certification",
                "achievement", "salary", "compensation", "evidence", "resume",
                "score", "completion"
        );

        for (Field field : CareerProfile.class.getDeclaredFields()) {
            String lowerName = field.getName().toLowerCase();
            for (String prohibited : prohibitedScopeFields) {
                assertFalse(lowerName.contains(prohibited),
                        "CareerProfile must not contain out-of-scope detail field: " + field.getName());
            }
        }
    }

    @Test
    void testRepositorySupportsFindByUserAndFindByUserId() throws NoSuchMethodException {
        java.lang.reflect.Method findByUser = CareerProfileRepository.class.getMethod("findByUser", User.class);
        assertNotNull(findByUser);
        assertEquals(java.util.Optional.class, findByUser.getReturnType());

        java.lang.reflect.Method findByUserId = CareerProfileRepository.class.getMethod("findByUserId", UUID.class);
        assertNotNull(findByUserId);
        assertEquals(java.util.Optional.class, findByUserId.getReturnType());
    }

    @Test
    void testRepositorySupportsExistsByUserAndExistsByUserId() throws NoSuchMethodException {
        java.lang.reflect.Method existsByUser = CareerProfileRepository.class.getMethod("existsByUser", User.class);
        assertNotNull(existsByUser);
        assertEquals(boolean.class, existsByUser.getReturnType());

        java.lang.reflect.Method existsByUserId = CareerProfileRepository.class.getMethod("existsByUserId", UUID.class);
        assertNotNull(existsByUserId);
        assertEquals(boolean.class, existsByUserId.getReturnType());
    }
}
