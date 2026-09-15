package com.joblivo.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailPasswordCredentialServiceTest {

    @Mock
    private EmailPasswordCredentialRepository credentialRepository;

    @Mock
    private PasswordEncoder mockPasswordEncoder;

    private EmailPasswordCredentialService credentialService;

    @BeforeEach
    void setUp() {
        credentialService = new EmailPasswordCredentialService(credentialRepository, mockPasswordEncoder);
    }

    private User createPersistedUser(UUID id, String email, String displayName) {
        Instant now = Instant.now();
        return new User(id, email, displayName, UserStatus.ACTIVE, now, now);
    }

    private UserAuthIdentity createPersistedIdentity(UUID id, User user, AuthProvider provider, String subject) {
        Instant now = Instant.now();
        return new UserAuthIdentity(id, user, provider, subject, now, now);
    }

    @Test
    @DisplayName("Creates credential for valid persisted EMAIL identity with existing user")
    void createCredential_WithValidEmailIdentity_EncodesAndSavesOnlyHash() {
        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        Instant now = Instant.now();

        User user = createPersistedUser(userId, "secure@joblivo.com", "Secure User");
        UserAuthIdentity authIdentity = createPersistedIdentity(identityId, user, AuthProvider.EMAIL, "secure@joblivo.com");

        String rawPassword = "CorrectHorseBatteryStaple!42";
        String encodedHash = "$2a$10$SimulatedHashedValueString1234567890";

        when(credentialRepository.existsByAuthIdentityId(identityId)).thenReturn(false);
        when(mockPasswordEncoder.encode(rawPassword)).thenReturn(encodedHash);

        EmailPasswordCredential persisted = new EmailPasswordCredential(
                credentialId, authIdentity, encodedHash, now, now
        );
        when(credentialRepository.saveAndFlush(any(EmailPasswordCredential.class))).thenReturn(persisted);

        EmailPasswordCredentialResult result = credentialService.createCredential(authIdentity, rawPassword);

        // Verify result safely contains metadata but never raw password or hash
        assertNotNull(result);
        assertEquals(credentialId, result.id());
        assertEquals(identityId, result.authIdentityId());
        assertEquals(now, result.createdAt());
        assertEquals(now, result.updatedAt());

        // Verify PasswordEncoder was called with raw password
        verify(mockPasswordEncoder).encode(rawPassword);

        // Verify repository saved entity containing only the hash
        ArgumentCaptor<EmailPasswordCredential> captor = ArgumentCaptor.forClass(EmailPasswordCredential.class);
        verify(credentialRepository).saveAndFlush(captor.capture());
        EmailPasswordCredential saved = captor.getValue();
        assertEquals(encodedHash, saved.getPasswordHash());
        assertNotEquals(rawPassword, saved.getPasswordHash(), "Raw password must never be saved as hash");
        assertEquals(authIdentity, saved.getAuthIdentity());
    }

    @Test
    @DisplayName("Preserves leading/trailing spaces and mixed casing in raw password before encoding")
    void createCredential_DoesNotTrimOrModifyPasswordBeforeHashing() {
        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        User user = createPersistedUser(userId, "untrimmed@joblivo.com", "Untrimmed User");
        UserAuthIdentity authIdentity = createPersistedIdentity(identityId, user, AuthProvider.EMAIL, "untrimmed@joblivo.com");

        // Password with intentional leading and trailing spaces and mixed casing
        String passwordWithSpaces = "   SpacesArePreserved!99   ";
        when(credentialRepository.existsByAuthIdentityId(identityId)).thenReturn(false);
        when(mockPasswordEncoder.encode(passwordWithSpaces)).thenReturn("$2a$10$hash");
        when(credentialRepository.saveAndFlush(any(EmailPasswordCredential.class))).thenAnswer(inv -> inv.getArgument(0));

        credentialService.createCredential(authIdentity, passwordWithSpaces);

        // Crucial security requirement: raw password must NOT be trimmed or lowercase-normalized
        verify(mockPasswordEncoder).encode("   SpacesArePreserved!99   ");
        verify(mockPasswordEncoder, never()).encode("SpacesArePreserved!99");
    }

    @Test
    @DisplayName("Rejects unpersisted identity with null ID")
    void createCredential_UnpersistedIdentityWithoutId_ThrowsIllegalArgumentException() {
        User user = createPersistedUser(UUID.randomUUID(), "test@joblivo.com", "Test");
        UserAuthIdentity unpersistedIdentity = new UserAuthIdentity(user, AuthProvider.EMAIL, "test@joblivo.com");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> credentialService.createCredential(unpersistedIdentity, "ValidP@ssword123"));

        assertTrue(ex.getMessage().contains("persisted ID"));
        verifyNoInteractions(mockPasswordEncoder);
        verifyNoInteractions(credentialRepository);
    }

    @Test
    @DisplayName("Rejects identity with null user or unpersisted user without ID")
    void createCredential_IdentityWithMissingOrUnpersistedUser_ThrowsIllegalArgumentException() {
        UUID identityId = UUID.randomUUID();

        // Identity with unpersisted user (id is null)
        User unpersistedUser = new User("test@joblivo.com", "Test");
        UserAuthIdentity identityWithUnpersistedUser = new UserAuthIdentity(
                identityId, unpersistedUser, AuthProvider.EMAIL, "test@joblivo.com", Instant.now(), Instant.now()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> credentialService.createCredential(identityWithUnpersistedUser, "ValidP@ssword123"));

        assertTrue(ex.getMessage().contains("valid existing user"));
        verifyNoInteractions(mockPasswordEncoder);
        verifyNoInteractions(credentialRepository);
    }

    @Test
    @DisplayName("Rejects non-EMAIL provider identities (e.g. GOOGLE or PHONE)")
    void createCredential_WhenProviderIsNotEmail_ThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "oauth@joblivo.com", "OAuth User");

        UserAuthIdentity googleIdentity = createPersistedIdentity(
                UUID.randomUUID(), user, AuthProvider.GOOGLE, "google-oauth2|12345"
        );
        UserAuthIdentity phoneIdentity = createPersistedIdentity(
                UUID.randomUUID(), user, AuthProvider.PHONE, "+1234567890"
        );

        IllegalArgumentException googleEx = assertThrows(IllegalArgumentException.class,
                () -> credentialService.createCredential(googleIdentity, "ValidPassword123"));
        assertTrue(googleEx.getMessage().contains("EMAIL"));

        IllegalArgumentException phoneEx = assertThrows(IllegalArgumentException.class,
                () -> credentialService.createCredential(phoneIdentity, "ValidPassword123"));
        assertTrue(phoneEx.getMessage().contains("EMAIL"));

        verifyNoInteractions(mockPasswordEncoder);
        verifyNoInteractions(credentialRepository);
    }

    @Test
    @DisplayName("Rejects duplicate credential when identity already has a credential record")
    void createCredential_WhenCredentialAlreadyExists_ThrowsDuplicateCredentialException() {
        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        User user = createPersistedUser(userId, "existing@joblivo.com", "Existing");
        UserAuthIdentity identity = createPersistedIdentity(
                identityId, user, AuthProvider.EMAIL, "existing@joblivo.com"
        );

        when(credentialRepository.existsByAuthIdentityId(identityId)).thenReturn(true);

        DuplicateCredentialException ex = assertThrows(DuplicateCredentialException.class,
                () -> credentialService.createCredential(identity, "ValidP@ssword123"));

        assertTrue(ex.getMessage().contains("already exists"));
        verify(credentialRepository, never()).saveAndFlush(any());
        verifyNoInteractions(mockPasswordEncoder);
    }

    @Test
    @DisplayName("Translates database uniqueness violation on save to DuplicateCredentialException")
    void createCredential_DatabaseConstraintViolation_ThrowsDuplicateCredentialException() {
        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        User user = createPersistedUser(userId, "race@joblivo.com", "Race User");
        UserAuthIdentity identity = createPersistedIdentity(
                identityId, user, AuthProvider.EMAIL, "race@joblivo.com"
        );

        when(credentialRepository.existsByAuthIdentityId(identityId)).thenReturn(false);
        when(mockPasswordEncoder.encode(anyString())).thenReturn("$2a$10$encodedHash");
        when(credentialRepository.saveAndFlush(any(EmailPasswordCredential.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint uq_email_password_credentials_auth_identity"));

        DuplicateCredentialException ex = assertThrows(DuplicateCredentialException.class,
                () -> credentialService.createCredential(identity, "ValidP@ssword123"));

        assertTrue(ex.getMessage().contains(identityId.toString()));
        assertNotNull(ex.getCause());
        assertInstanceOf(DataIntegrityViolationException.class, ex.getCause());
    }

    @Test
    @DisplayName("Rejects null identity with NullPointerException")
    void createCredential_WhenIdentityIsNull_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> credentialService.createCredential(null, "ValidPassword123"));
    }

    @Test
    @DisplayName("Rejects password shorter than minimum length")
    void createCredential_RejectsTooShortPassword() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "test@joblivo.com", "Test");
        UserAuthIdentity identity = createPersistedIdentity(UUID.randomUUID(), user, AuthProvider.EMAIL, "test@joblivo.com");

        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class,
                () -> credentialService.createCredential(identity, "short12"));

        assertTrue(ex.getMessage().contains("at least 8 characters"));
        verifyNoInteractions(mockPasswordEncoder);
        verifyNoInteractions(credentialRepository);
    }

    @Test
    @DisplayName("Rejects password exceeding maximum length")
    void createCredential_RejectsExcessivelyLongPassword() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "test@joblivo.com", "Test");
        UserAuthIdentity identity = createPersistedIdentity(UUID.randomUUID(), user, AuthProvider.EMAIL, "test@joblivo.com");

        String longPassword = "x".repeat(129);
        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class,
                () -> credentialService.createCredential(identity, longPassword));

        assertTrue(ex.getMessage().contains("must not exceed 128 characters"));
        verifyNoInteractions(mockPasswordEncoder);
        verifyNoInteractions(credentialRepository);
    }

    @Test
    @DisplayName("Rejects blank or null password")
    void createCredential_RejectsBlankOrNullPassword() {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "test@joblivo.com", "Test");
        UserAuthIdentity identity = createPersistedIdentity(UUID.randomUUID(), user, AuthProvider.EMAIL, "test@joblivo.com");

        assertThrows(InvalidPasswordException.class, () -> credentialService.createCredential(identity, null));
        assertThrows(InvalidPasswordException.class, () -> credentialService.createCredential(identity, ""));
        assertThrows(InvalidPasswordException.class, () -> credentialService.createCredential(identity, "       "));

        verifyNoInteractions(mockPasswordEncoder);
        verifyNoInteractions(credentialRepository);
    }

    @Test
    @DisplayName("Accepts passwords at exact minimum and maximum length boundaries")
    void createCredential_AcceptsBoundaryLengthPasswords() {
        assertDoesNotThrow(() -> credentialService.validatePassword("12345678"));
        assertDoesNotThrow(() -> credentialService.validatePassword("a".repeat(128)));
    }

    @Test
    @DisplayName("Real PasswordEncoder integration verifies matching without exposing hash in result")
    void realPasswordEncoderIntegration_VerifiesPasswordMatchingWithoutExposingHash() {
        PasswordEncoder realEncoder = new BCryptPasswordEncoder();
        EmailPasswordCredentialService realService = new EmailPasswordCredentialService(
                credentialRepository, realEncoder
        );

        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        User user = createPersistedUser(userId, "bcrypt@joblivo.com", "BCrypt User");
        UserAuthIdentity identity = createPersistedIdentity(identityId, user, AuthProvider.EMAIL, "bcrypt@joblivo.com");

        when(credentialRepository.existsByAuthIdentityId(identityId)).thenReturn(false);
        when(credentialRepository.saveAndFlush(any(EmailPasswordCredential.class))).thenAnswer(inv -> inv.getArgument(0));

        String rawPassword = "Complex!Secure!P@ssw0rd99";
        EmailPasswordCredentialResult result = realService.createCredential(identity, rawPassword);

        assertNotNull(result);
        assertEquals(identityId, result.authIdentityId());

        ArgumentCaptor<EmailPasswordCredential> captor = ArgumentCaptor.forClass(EmailPasswordCredential.class);
        verify(credentialRepository).saveAndFlush(captor.capture());

        EmailPasswordCredential captured = captor.getValue();
        assertNotNull(captured.getPasswordHash());
        assertTrue(captured.getPasswordHash().startsWith("$2a$") || captured.getPasswordHash().startsWith("$2b$"));

        assertTrue(realEncoder.matches(rawPassword, captured.getPasswordHash()));
        assertFalse(realEncoder.matches("WrongPassword!00", captured.getPasswordHash()));
        assertFalse(realEncoder.matches(rawPassword.toLowerCase(), captured.getPasswordHash()));
    }

    @Test
    @DisplayName("Security: EmailPasswordCredentialResult record exposes no credential/password fields")
    void securityReview_ResultRecordExposesNoCredentialFields() {
        for (Field field : EmailPasswordCredentialResult.class.getDeclaredFields()) {
            String lower = field.getName().toLowerCase();
            assertFalse(lower.contains("password"), "Result record must not contain password field");
            assertFalse(lower.contains("hash"), "Result record must not contain hash field");
            assertFalse(lower.contains("secret"), "Result record must not contain secret field");
        }
    }
}
