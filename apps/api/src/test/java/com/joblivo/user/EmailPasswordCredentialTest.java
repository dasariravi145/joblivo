package com.joblivo.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailPasswordCredentialTest {

    @Mock
    private EmailPasswordCredentialRepository credentialRepository;

    @Test
    void testEntityMapsToEmailPasswordCredentialsTable() {
        Entity entityAnnotation = EmailPasswordCredential.class.getAnnotation(Entity.class);
        assertNotNull(entityAnnotation, "EmailPasswordCredential must be annotated with @Entity");

        Table tableAnnotation = EmailPasswordCredential.class.getAnnotation(Table.class);
        assertNotNull(tableAnnotation, "EmailPasswordCredential must be annotated with @Table");
        assertEquals("email_password_credentials", tableAnnotation.name(),
                "Table name must be exactly 'email_password_credentials'");
    }

    @Test
    void testUuidIdMappingIsCorrect() throws NoSuchFieldException {
        Field idField = EmailPasswordCredential.class.getDeclaredField("id");
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
    void testAuthIdentityRelationshipMappingIsCorrect() throws NoSuchFieldException {
        Field authIdentityField = EmailPasswordCredential.class.getDeclaredField("authIdentity");
        assertEquals(UserAuthIdentity.class, authIdentityField.getType(),
                "authIdentity field type must be UserAuthIdentity");

        OneToOne oneToOne = authIdentityField.getAnnotation(OneToOne.class);
        assertNotNull(oneToOne, "authIdentity field must have @OneToOne annotation");
        assertEquals(FetchType.LAZY, oneToOne.fetch(), "Relationship must be FetchType.LAZY");
        assertFalse(oneToOne.optional(), "Relationship must not be optional");

        JoinColumn joinColumn = authIdentityField.getAnnotation(JoinColumn.class);
        assertNotNull(joinColumn, "authIdentity field must have @JoinColumn annotation");
        assertEquals("auth_identity_id", joinColumn.name(), "Join column name must be 'auth_identity_id'");
        assertFalse(joinColumn.nullable(), "auth_identity_id column must be NOT NULL");
        assertTrue(joinColumn.unique(), "auth_identity_id column must be unique (1-to-1)");
    }

    @Test
    void testPasswordHashMappingIsCorrect() throws NoSuchFieldException {
        Field passwordHashField = EmailPasswordCredential.class.getDeclaredField("passwordHash");
        assertEquals(String.class, passwordHashField.getType(), "passwordHash field type must be String");

        Column columnAnnotation = passwordHashField.getAnnotation(Column.class);
        assertNotNull(columnAnnotation, "passwordHash field must have @Column annotation");
        assertEquals("password_hash", columnAnnotation.name());
        assertFalse(columnAnnotation.nullable(), "password_hash column must be NOT NULL");
        assertEquals(255, columnAnnotation.length(), "password_hash column length must accommodate modern hash formats");
    }

    @Test
    void testTimestampMappingsUseInstant() throws NoSuchFieldException {
        Field createdAtField = EmailPasswordCredential.class.getDeclaredField("createdAt");
        assertEquals(Instant.class, createdAtField.getType(), "createdAt must be java.time.Instant");
        Column createdAtCol = createdAtField.getAnnotation(Column.class);
        assertNotNull(createdAtCol);
        assertEquals("created_at", createdAtCol.name());
        assertFalse(createdAtCol.nullable());
        assertFalse(createdAtCol.updatable());

        Field updatedAtField = EmailPasswordCredential.class.getDeclaredField("updatedAt");
        assertEquals(Instant.class, updatedAtField.getType(), "updatedAt must be java.time.Instant");
        Column updatedAtCol = updatedAtField.getAnnotation(Column.class);
        assertNotNull(updatedAtCol);
        assertEquals("updated_at", updatedAtCol.name());
        assertFalse(updatedAtCol.nullable());
    }

    @Test
    void testEntityLifecycleCallbacks() {
        User user = new User("test@joblivo.com", "Test User");
        UserAuthIdentity authIdentity = new UserAuthIdentity(user, AuthProvider.EMAIL, "test@joblivo.com");
        EmailPasswordCredential credential = new EmailPasswordCredential(authIdentity, "$2a$10$hashedPasswordValue");

        assertNull(credential.getId());
        assertNull(credential.getCreatedAt());
        assertNull(credential.getUpdatedAt());

        credential.onCreate();

        assertNotNull(credential.getCreatedAt());
        assertNotNull(credential.getUpdatedAt());

        Instant firstUpdate = credential.getUpdatedAt();
        credential.onUpdate();

        assertNotNull(credential.getUpdatedAt());
        assertFalse(credential.getUpdatedAt().isBefore(firstUpdate));
    }

    @Test
    void testConstructorsAndMutators() {
        User user = new User("user@joblivo.com", "Test User");
        UserAuthIdentity identity = new UserAuthIdentity(user, AuthProvider.EMAIL, "user@joblivo.com");
        String fakeHash = "$2a$10$7EqJtq98hPqEX7fNZaFWoO";

        EmailPasswordCredential credential = new EmailPasswordCredential(identity, fakeHash);
        assertEquals(identity, credential.getAuthIdentity());
        assertEquals(fakeHash, credential.getPasswordHash());

        String newHash = "$2a$10$otherHashedPassword";
        credential.setPasswordHash(newHash);
        assertEquals(newHash, credential.getPasswordHash());

        assertThrows(NullPointerException.class, () -> new EmailPasswordCredential(null, fakeHash));
        assertThrows(NullPointerException.class, () -> new EmailPasswordCredential(identity, null));
        assertThrows(NullPointerException.class, () -> credential.setPasswordHash(null));
        assertThrows(NullPointerException.class, () -> credential.setAuthIdentity(null));
    }

    @Test
    void testConstructorAndSetterRejectNonEmailProvider() {
        User user = new User("google@joblivo.com", "Google User");
        UserAuthIdentity googleIdentity = new UserAuthIdentity(user, AuthProvider.GOOGLE, "google-12345");
        UserAuthIdentity phoneIdentity = new UserAuthIdentity(user, AuthProvider.PHONE, "+1234567890");

        assertThrows(IllegalArgumentException.class, () -> new EmailPasswordCredential(googleIdentity, "$2a$10$hash"));
        assertThrows(IllegalArgumentException.class, () -> new EmailPasswordCredential(phoneIdentity, "$2a$10$hash"));

        UserAuthIdentity emailIdentity = new UserAuthIdentity(user, AuthProvider.EMAIL, "email@joblivo.com");
        EmailPasswordCredential credential = new EmailPasswordCredential(emailIdentity, "$2a$10$hash");
        assertThrows(IllegalArgumentException.class, () -> credential.setAuthIdentity(googleIdentity));
        assertThrows(IllegalArgumentException.class, () -> credential.setAuthIdentity(phoneIdentity));
    }

    @Test
    void testEqualsAndHashCodeBasedOnId() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        User user = new User("a@b.com", "User");
        UserAuthIdentity identity = new UserAuthIdentity(user, AuthProvider.EMAIL, "a@b.com");
        Instant now = Instant.now();

        EmailPasswordCredential c1 = new EmailPasswordCredential(id1, identity, "hash1", now, now);
        EmailPasswordCredential c1Duplicate = new EmailPasswordCredential(id1, identity, "hash2", now, now);
        EmailPasswordCredential c2 = new EmailPasswordCredential(id2, identity, "hash1", now, now);

        assertEquals(c1, c1Duplicate, "Entities with identical IDs must be equal");
        assertNotEquals(c1, c2, "Entities with distinct IDs must not be equal");
        assertEquals(c1.hashCode(), c1Duplicate.hashCode());
    }

    @Test
    void testToStringExplicitlyExcludesPasswordHash() {
        User user = new User("audit@joblivo.com", "Audit User");
        UserAuthIdentity identity = new UserAuthIdentity(user, AuthProvider.EMAIL, "audit@joblivo.com");
        String sensitiveHash = "$2a$12$SUPER_SECRET_BCRYPT_HASH_VALUE";

        EmailPasswordCredential credential = new EmailPasswordCredential(identity, sensitiveHash);
        String stringRep = credential.toString();

        assertNotNull(stringRep);
        assertFalse(stringRep.contains(sensitiveHash),
                "toString() must never contain the password hash to prevent log leakage");
        assertFalse(stringRep.toLowerCase().contains("super_secret"),
                "toString() must never contain any portion of the secret hash");
    }

    @Test
    void testSecurityReviewNoRawPasswordFieldExistsInEntity() {
        List<String> prohibitedFieldNames = List.of(
                "password", "rawpassword", "plainpassword", "clearpassword", "secret", "token", "otp"
        );

        for (Field field : EmailPasswordCredential.class.getDeclaredFields()) {
            String lowerName = field.getName().toLowerCase();
            for (String prohibited : prohibitedFieldNames) {
                if (lowerName.equals(prohibited)) {
                    fail("EmailPasswordCredential must never have a raw password field: " + field.getName());
                }
            }
        }
    }

    @Test
    void testRepositorySupportsLookupByAuthIdentityId() throws NoSuchMethodException {
        Method findMethod = EmailPasswordCredentialRepository.class.getMethod(
                "findByAuthIdentityId", UUID.class
        );
        assertNotNull(findMethod);
        assertEquals(Optional.class, findMethod.getReturnType());

        Method existsMethod = EmailPasswordCredentialRepository.class.getMethod(
                "existsByAuthIdentityId", UUID.class
        );
        assertNotNull(existsMethod);
        assertEquals(boolean.class, existsMethod.getReturnType());

        UUID identityId = UUID.randomUUID();
        User user = new User("lookup@joblivo.com", "Lookup User");
        UserAuthIdentity identity = new UserAuthIdentity(user, AuthProvider.EMAIL, "lookup@joblivo.com");
        EmailPasswordCredential mockCredential = new EmailPasswordCredential(identity, "$2a$10$hash");

        when(credentialRepository.findByAuthIdentityId(identityId)).thenReturn(Optional.of(mockCredential));
        when(credentialRepository.existsByAuthIdentityId(identityId)).thenReturn(true);

        Optional<EmailPasswordCredential> found = credentialRepository.findByAuthIdentityId(identityId);
        assertTrue(found.isPresent());
        assertTrue(credentialRepository.existsByAuthIdentityId(identityId));

        verify(credentialRepository).findByAuthIdentityId(identityId);
        verify(credentialRepository).existsByAuthIdentityId(identityId);
    }
}
