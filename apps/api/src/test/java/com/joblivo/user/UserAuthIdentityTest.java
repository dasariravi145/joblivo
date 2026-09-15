package com.joblivo.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthIdentityTest {

    @Mock
    private UserAuthIdentityRepository userAuthIdentityRepository;

    @Test
    void testAuthProviderContainsExactlyEmailGooglePhone() {
        AuthProvider[] providers = AuthProvider.values();
        assertEquals(3, providers.length, "AuthProvider must contain exactly 3 values");

        Set<String> providerNames = Arrays.stream(providers)
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertTrue(providerNames.contains("EMAIL"), "Must contain EMAIL");
        assertTrue(providerNames.contains("GOOGLE"), "Must contain GOOGLE");
        assertTrue(providerNames.contains("PHONE"), "Must contain PHONE");

        assertEquals(AuthProvider.EMAIL, AuthProvider.valueOf("EMAIL"));
        assertEquals(AuthProvider.GOOGLE, AuthProvider.valueOf("GOOGLE"));
        assertEquals(AuthProvider.PHONE, AuthProvider.valueOf("PHONE"));
    }

    @Test
    void testAuthProviderPersistedAsStringRatherThanOrdinal() throws NoSuchFieldException {
        Field providerField = UserAuthIdentity.class.getDeclaredField("provider");
        Enumerated enumeratedAnnotation = providerField.getAnnotation(Enumerated.class);

        assertNotNull(enumeratedAnnotation, "provider field must have @Enumerated annotation");
        assertEquals(EnumType.STRING, enumeratedAnnotation.value(),
                "AuthProvider must be persisted as EnumType.STRING rather than ordinal");

        Column columnAnnotation = providerField.getAnnotation(Column.class);
        assertNotNull(columnAnnotation, "provider field must have @Column annotation");
        assertEquals("provider", columnAnnotation.name());
        assertFalse(columnAnnotation.nullable(), "provider column must be NOT NULL");
    }

    @Test
    void testUserAuthIdentityMapsToUserAuthIdentitiesTable() {
        Entity entityAnnotation = UserAuthIdentity.class.getAnnotation(Entity.class);
        assertNotNull(entityAnnotation, "UserAuthIdentity must be annotated with @Entity");

        Table tableAnnotation = UserAuthIdentity.class.getAnnotation(Table.class);
        assertNotNull(tableAnnotation, "UserAuthIdentity must be annotated with @Table");
        assertEquals("user_auth_identities", tableAnnotation.name(),
                "Table name must be exactly 'user_auth_identities'");
    }

    @Test
    void testUuidIdMappingIsCorrect() throws NoSuchFieldException {
        Field idField = UserAuthIdentity.class.getDeclaredField("id");
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
        Field userField = UserAuthIdentity.class.getDeclaredField("user");
        assertEquals(User.class, userField.getType(), "user field type must be User entity");

        ManyToOne manyToOne = userField.getAnnotation(ManyToOne.class);
        assertNotNull(manyToOne, "user field must have @ManyToOne annotation");
        assertEquals(FetchType.LAZY, manyToOne.fetch(), "user relationship must be FetchType.LAZY");
        assertFalse(manyToOne.optional(), "user relationship must not be optional");

        JoinColumn joinColumn = userField.getAnnotation(JoinColumn.class);
        assertNotNull(joinColumn, "user field must have @JoinColumn annotation");
        assertEquals("user_id", joinColumn.name(), "Join column must be 'user_id'");
        assertFalse(joinColumn.nullable(), "user_id column must be NOT NULL");

        // Verify User entity remains untouched and unidirectional
        Set<String> userFieldNames = Arrays.stream(User.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(userFieldNames.contains("authIdentities"),
                "User entity must not introduce a bidirectional collection unless required");
        assertFalse(userFieldNames.contains("provider"),
                "User entity must not contain provider-specific columns");
        assertFalse(userFieldNames.contains("password"),
                "User entity must not contain password columns");
    }

    @Test
    void testRepositorySupportsProviderAndProviderSubjectLookup() throws NoSuchMethodException {
        Method lookupMethod = UserAuthIdentityRepository.class.getMethod(
                "findByProviderAndProviderSubject", AuthProvider.class, String.class);
        assertNotNull(lookupMethod);
        assertEquals(Optional.class, lookupMethod.getReturnType());

        Method existsMethod = UserAuthIdentityRepository.class.getMethod(
                "existsByProviderAndProviderSubject", AuthProvider.class, String.class);
        assertNotNull(existsMethod);
        assertEquals(boolean.class, existsMethod.getReturnType());

        // Mock verification
        User mockUser = new User("dev@joblivo.com", "Dev User");
        UserAuthIdentity mockIdentity = new UserAuthIdentity(mockUser, AuthProvider.GOOGLE, "google-sub-12345");
        when(userAuthIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub-12345"))
                .thenReturn(Optional.of(mockIdentity));

        Optional<UserAuthIdentity> found = userAuthIdentityRepository
                .findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub-12345");

        assertTrue(found.isPresent());
        assertEquals(AuthProvider.GOOGLE, found.get().getProvider());
        assertEquals("google-sub-12345", found.get().getProviderSubject());
        verify(userAuthIdentityRepository).findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub-12345");
    }

    @Test
    void testRepositorySupportsUserIdLookup() throws NoSuchMethodException {
        Method lookupByUserId = UserAuthIdentityRepository.class.getMethod("findByUserId", UUID.class);
        assertNotNull(lookupByUserId);
        assertEquals(List.class, lookupByUserId.getReturnType());

        UUID userId = UUID.randomUUID();
        User mockUser = new User("dev@joblivo.com", "Dev User");
        UserAuthIdentity emailIdentity = new UserAuthIdentity(mockUser, AuthProvider.EMAIL, "dev@joblivo.com");
        UserAuthIdentity googleIdentity = new UserAuthIdentity(mockUser, AuthProvider.GOOGLE, "google-123");

        when(userAuthIdentityRepository.findByUserId(userId)).thenReturn(List.of(emailIdentity, googleIdentity));

        List<UserAuthIdentity> identities = userAuthIdentityRepository.findByUserId(userId);
        assertEquals(2, identities.size());
        verify(userAuthIdentityRepository).findByUserId(userId);
    }

    @Test
    void testRepositorySupportsUserIdAndProviderLookup() throws NoSuchMethodException {
        Method findMethod = UserAuthIdentityRepository.class.getMethod(
                "findByUserIdAndProvider", UUID.class, AuthProvider.class);
        assertNotNull(findMethod);
        assertEquals(Optional.class, findMethod.getReturnType());

        Method existsMethod = UserAuthIdentityRepository.class.getMethod(
                "existsByUserIdAndProvider", UUID.class, AuthProvider.class);
        assertNotNull(existsMethod);
        assertEquals(boolean.class, existsMethod.getReturnType());

        UUID userId = UUID.randomUUID();
        User mockUser = new User("dev@joblivo.com", "Dev User");
        UserAuthIdentity identity = new UserAuthIdentity(mockUser, AuthProvider.EMAIL, "dev@joblivo.com");

        when(userAuthIdentityRepository.findByUserIdAndProvider(userId, AuthProvider.EMAIL))
                .thenReturn(Optional.of(identity));
        when(userAuthIdentityRepository.existsByUserIdAndProvider(userId, AuthProvider.EMAIL))
                .thenReturn(true);

        Optional<UserAuthIdentity> result = userAuthIdentityRepository.findByUserIdAndProvider(userId, AuthProvider.EMAIL);
        assertTrue(result.isPresent());
        assertEquals(AuthProvider.EMAIL, result.get().getProvider());

        boolean exists = userAuthIdentityRepository.existsByUserIdAndProvider(userId, AuthProvider.EMAIL);
        assertTrue(exists);

        verify(userAuthIdentityRepository).findByUserIdAndProvider(userId, AuthProvider.EMAIL);
        verify(userAuthIdentityRepository).existsByUserIdAndProvider(userId, AuthProvider.EMAIL);
    }

    @Test
    void testEntityLifecycleAndTimestamps() {
        User user = new User("test@joblivo.com", "Test User");
        UserAuthIdentity identity = new UserAuthIdentity(user, AuthProvider.EMAIL, "test@joblivo.com");

        assertNull(identity.getId(), "ID must be null before persistence");
        assertNull(identity.getCreatedAt(), "createdAt must be null before persist callback");
        assertNull(identity.getUpdatedAt(), "updatedAt must be null before persist callback");

        identity.onCreate();

        assertNotNull(identity.getCreatedAt(), "createdAt must be populated after onCreate");
        assertNotNull(identity.getUpdatedAt(), "updatedAt must be populated after onCreate");

        Instant initialUpdatedAt = identity.getUpdatedAt();
        identity.onUpdate();

        assertNotNull(identity.getUpdatedAt());
        assertFalse(identity.getUpdatedAt().isBefore(initialUpdatedAt),
                "updatedAt must be updated on onUpdate");
    }

    @Test
    void testEntityConstructorsAndMutators() {
        User user = new User("test@joblivo.com", "Test User");
        UserAuthIdentity identity = new UserAuthIdentity(user, AuthProvider.PHONE, "+1234567890");

        assertEquals(user, identity.getUser());
        assertEquals(AuthProvider.PHONE, identity.getProvider());
        assertEquals("+1234567890", identity.getProviderSubject());

        User newUser = new User("new@joblivo.com", "New User");
        identity.setUser(newUser);
        identity.setProvider(AuthProvider.EMAIL);
        identity.setProviderSubject("new@joblivo.com");

        assertEquals(newUser, identity.getUser());
        assertEquals(AuthProvider.EMAIL, identity.getProvider());
        assertEquals("new@joblivo.com", identity.getProviderSubject());

        assertThrows(NullPointerException.class, () -> new UserAuthIdentity(null, AuthProvider.EMAIL, "sub"));
        assertThrows(NullPointerException.class, () -> new UserAuthIdentity(user, null, "sub"));
        assertThrows(NullPointerException.class, () -> new UserAuthIdentity(user, AuthProvider.EMAIL, null));
    }

    @Test
    void testEntityEqualsAndHashCodeContract() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        User user = new User("test@joblivo.com", "Test User");

        Instant now = Instant.now();
        UserAuthIdentity identity1 = new UserAuthIdentity(id1, user, AuthProvider.EMAIL, "a@b.com", now, now);
        UserAuthIdentity identity1Duplicate = new UserAuthIdentity(id1, user, AuthProvider.GOOGLE, "other", now, now);
        UserAuthIdentity identity2 = new UserAuthIdentity(id2, user, AuthProvider.EMAIL, "a@b.com", now, now);

        assertEquals(identity1, identity1Duplicate, "Entities with identical IDs must be equal");
        assertNotEquals(identity1, identity2, "Entities with different IDs must not be equal");
        assertEquals(identity1.hashCode(), identity1Duplicate.hashCode());
    }

    @Test
    void testSecurityReviewNoCredentialsStoredInEntity() {
        List<String> prohibitedTerms = List.of(
                "password", "hash", "secret", "token", "otp", "code", "pin", "credential", "jwt", "apikey"
        );

        for (Field field : UserAuthIdentity.class.getDeclaredFields()) {
            String lowerName = field.getName().toLowerCase();
            for (String prohibited : prohibitedTerms) {
                assertFalse(lowerName.contains(prohibited),
                        "UserAuthIdentity entity must never contain credential field: " + field.getName());
            }
        }
    }
}
