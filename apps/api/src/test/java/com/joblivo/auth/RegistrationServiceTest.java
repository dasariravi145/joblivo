package com.joblivo.auth;

import com.joblivo.user.AuthProvider;
import com.joblivo.user.DuplicateAuthIdentityException;
import com.joblivo.user.DuplicateEmailException;
import com.joblivo.user.EmailPasswordCredentialResult;
import com.joblivo.user.EmailPasswordCredentialService;
import com.joblivo.user.InvalidPasswordException;
import com.joblivo.user.User;
import com.joblivo.user.UserAuthIdentity;
import com.joblivo.user.UserAuthIdentityService;
import com.joblivo.user.UserRepository;
import com.joblivo.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAuthIdentityService userAuthIdentityService;

    @Mock
    private EmailPasswordCredentialService emailPasswordCredentialService;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(
                userRepository,
                userAuthIdentityService,
                emailPasswordCredentialService
        );
    }

    private User createPersistedUser(UUID id, String email, String displayName, Instant timestamp) throws Exception {
        User user = new User(email, displayName);
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);

        Field createdAtField = User.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(user, timestamp);

        Field updatedAtField = User.class.getDeclaredField("updatedAt");
        updatedAtField.setAccessible(true);
        updatedAtField.set(user, timestamp);

        return user;
    }

    private UserAuthIdentity createPersistedIdentity(UUID id, User user, AuthProvider provider, String subject, Instant timestamp) throws Exception {
        UserAuthIdentity identity = new UserAuthIdentity(user, provider, subject);
        Field idField = UserAuthIdentity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(identity, id);

        Field createdAtField = UserAuthIdentity.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(identity, timestamp);

        Field updatedAtField = UserAuthIdentity.class.getDeclaredField("updatedAt");
        updatedAtField.setAccessible(true);
        updatedAtField.set(identity, timestamp);

        return identity;
    }

    @Test
    void register_WithValidRequest_CoordinatesUserIdentityAndCredentialCreation() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "newuser@joblivo.com",
                "New User",
                "P@ssword123!",
                "P@ssword123!"
        );

        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        Instant now = Instant.now();

        User persistedUser = createPersistedUser(userId, "newuser@joblivo.com", "New User", now);
        UserAuthIdentity persistedIdentity = createPersistedIdentity(
                identityId, persistedUser, AuthProvider.EMAIL, "newuser@joblivo.com", now
        );
        EmailPasswordCredentialResult credentialResult = new EmailPasswordCredentialResult(
                credentialId, identityId, now, now
        );

        when(userRepository.existsByEmailIgnoreCase("newuser@joblivo.com")).thenReturn(false);
        when(userAuthIdentityService.existsByProviderAndSubject(AuthProvider.EMAIL, "newuser@joblivo.com"))
                .thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(persistedUser);
        when(userAuthIdentityService.createIdentity(persistedUser, AuthProvider.EMAIL, "newuser@joblivo.com"))
                .thenReturn(persistedIdentity);
        when(emailPasswordCredentialService.createCredential(persistedIdentity, "P@ssword123!"))
                .thenReturn(credentialResult);

        RegisterResponse response = registrationService.register(request);

        assertNotNull(response);
        assertEquals(userId, response.id());
        assertEquals("newuser@joblivo.com", response.email());
        assertEquals("New User", response.displayName());
        assertEquals(UserStatus.ACTIVE, response.status());
        assertEquals(now, response.createdAt());

        // Verify User was saved with ACTIVE status
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertEquals("newuser@joblivo.com", capturedUser.getEmail());
        assertEquals("New User", capturedUser.getDisplayName());
        assertEquals(UserStatus.ACTIVE, capturedUser.getStatus());

        // Verify UserAuthIdentity was created via UserAuthIdentityService
        verify(userAuthIdentityService).createIdentity(persistedUser, AuthProvider.EMAIL, "newuser@joblivo.com");

        // Verify credential service was invoked with raw unhampered password
        verify(emailPasswordCredentialService).createCredential(persistedIdentity, "P@ssword123!");
    }

    @Test
    void register_NormalizesEmailAndTrimsDisplayName() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "   MixedCase.User@Joblivo.COM   ",
                "   Trimmed Name   ",
                "P@ssword123!",
                "P@ssword123!"
        );

        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        User persistedUser = createPersistedUser(userId, "mixedcase.user@joblivo.com", "Trimmed Name", now);
        UserAuthIdentity persistedIdentity = createPersistedIdentity(
                UUID.randomUUID(), persistedUser, AuthProvider.EMAIL, "mixedcase.user@joblivo.com", now
        );

        when(userRepository.existsByEmailIgnoreCase("mixedcase.user@joblivo.com")).thenReturn(false);
        when(userAuthIdentityService.existsByProviderAndSubject(AuthProvider.EMAIL, "mixedcase.user@joblivo.com"))
                .thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(persistedUser);
        when(userAuthIdentityService.createIdentity(persistedUser, AuthProvider.EMAIL, "mixedcase.user@joblivo.com"))
                .thenReturn(persistedIdentity);

        RegisterResponse response = registrationService.register(request);

        assertEquals("mixedcase.user@joblivo.com", response.email());
        assertEquals("Trimmed Name", response.displayName());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals("mixedcase.user@joblivo.com", userCaptor.getValue().getEmail());
        assertEquals("Trimmed Name", userCaptor.getValue().getDisplayName());

        verify(userAuthIdentityService).createIdentity(persistedUser, AuthProvider.EMAIL, "mixedcase.user@joblivo.com");
    }

    @Test
    void register_DoesNotTrimOrAlterRawPassword() throws Exception {
        // Password with intentional surrounding spaces
        String untrimmedPassword = "   LeadingTrailingSpaces!42   ";
        RegisterRequest request = new RegisterRequest(
                "untrimmed@joblivo.com",
                "Untrimmed User",
                untrimmedPassword,
                untrimmedPassword
        );

        User persistedUser = createPersistedUser(UUID.randomUUID(), "untrimmed@joblivo.com", "Untrimmed User", Instant.now());
        UserAuthIdentity persistedIdentity = createPersistedIdentity(
                UUID.randomUUID(), persistedUser, AuthProvider.EMAIL, "untrimmed@joblivo.com", Instant.now()
        );

        when(userRepository.existsByEmailIgnoreCase("untrimmed@joblivo.com")).thenReturn(false);
        when(userAuthIdentityService.existsByProviderAndSubject(AuthProvider.EMAIL, "untrimmed@joblivo.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any())).thenReturn(persistedUser);
        when(userAuthIdentityService.createIdentity(persistedUser, AuthProvider.EMAIL, "untrimmed@joblivo.com")).thenReturn(persistedIdentity);

        registrationService.register(request);

        // Raw password must be passed completely unmodified to credential service
        verify(emailPasswordCredentialService).createCredential(persistedIdentity, untrimmedPassword);
        verify(emailPasswordCredentialService, never()).createCredential(persistedIdentity, "LeadingTrailingSpaces!42");
    }

    @Test
    void register_WhenPasswordsMismatch_ThrowsPasswordMismatchException() {
        RegisterRequest request = new RegisterRequest(
                "mismatch@joblivo.com",
                "Mismatch User",
                "P@ssword123!",
                "DifferentP@ssword123!"
        );

        PasswordMismatchException ex = assertThrows(PasswordMismatchException.class,
                () -> registrationService.register(request));

        assertEquals("Passwords do not match", ex.getMessage());
        verifyNoInteractions(userRepository);
        verifyNoInteractions(userAuthIdentityService);
        verifyNoInteractions(emailPasswordCredentialService);
    }

    @Test
    void register_WhenEmailAlreadyExistsInUserRepository_ThrowsDuplicateEmailException() {
        RegisterRequest request = new RegisterRequest(
                "existing@joblivo.com",
                "Existing User",
                "P@ssword123!",
                "P@ssword123!"
        );

        when(userRepository.existsByEmailIgnoreCase("existing@joblivo.com")).thenReturn(true);

        DuplicateEmailException ex = assertThrows(DuplicateEmailException.class,
                () -> registrationService.register(request));

        assertTrue(ex.getMessage().contains("existing@joblivo.com"));
        verify(userRepository, never()).saveAndFlush(any());
        verify(userAuthIdentityService, never()).createIdentity(any(), any(), any());
        verify(emailPasswordCredentialService, never()).createCredential(any(), any());
    }

    @Test
    void register_WhenEmailExistsInUserAuthIdentityService_ThrowsDuplicateEmailException() {
        RegisterRequest request = new RegisterRequest(
                "identity-conflict@joblivo.com",
                "Conflict User",
                "P@ssword123!",
                "P@ssword123!"
        );

        when(userRepository.existsByEmailIgnoreCase("identity-conflict@joblivo.com")).thenReturn(false);
        when(userAuthIdentityService.existsByProviderAndSubject(AuthProvider.EMAIL, "identity-conflict@joblivo.com"))
                .thenReturn(true);

        DuplicateEmailException ex = assertThrows(DuplicateEmailException.class,
                () -> registrationService.register(request));

        assertTrue(ex.getMessage().contains("identity-conflict@joblivo.com"));
        verify(userRepository, never()).saveAndFlush(any());
        verify(userAuthIdentityService, never()).createIdentity(any(), any(), any());
        verify(emailPasswordCredentialService, never()).createCredential(any(), any());
    }

    @Test
    void register_WhenUserAuthIdentityServiceThrowsDuplicate_TranslatesToDuplicateEmailException() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "duplicate-identity@joblivo.com",
                "Duplicate User",
                "P@ssword123!",
                "P@ssword123!"
        );

        User persistedUser = createPersistedUser(UUID.randomUUID(), "duplicate-identity@joblivo.com", "Duplicate User", Instant.now());
        when(userRepository.existsByEmailIgnoreCase("duplicate-identity@joblivo.com")).thenReturn(false);
        when(userAuthIdentityService.existsByProviderAndSubject(AuthProvider.EMAIL, "duplicate-identity@joblivo.com"))
                .thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(persistedUser);
        when(userAuthIdentityService.createIdentity(persistedUser, AuthProvider.EMAIL, "duplicate-identity@joblivo.com"))
                .thenThrow(new DuplicateAuthIdentityException("Identity conflict"));

        DuplicateEmailException ex = assertThrows(DuplicateEmailException.class,
                () -> registrationService.register(request));

        assertTrue(ex.getMessage().contains("duplicate-identity@joblivo.com"));
    }

    @Test
    void register_WhenConcurrentRaceOccurs_TranslatesToDuplicateEmailException() {
        RegisterRequest request = new RegisterRequest(
                "race@joblivo.com",
                "Race User",
                "P@ssword123!",
                "P@ssword123!"
        );

        when(userRepository.existsByEmailIgnoreCase("race@joblivo.com")).thenReturn(false);
        when(userAuthIdentityService.existsByProviderAndSubject(AuthProvider.EMAIL, "race@joblivo.com"))
                .thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint uq_users_email_lower"));

        DuplicateEmailException ex = assertThrows(DuplicateEmailException.class,
                () -> registrationService.register(request));

        assertTrue(ex.getMessage().contains("race@joblivo.com"));
        assertNotNull(ex.getCause());
        assertInstanceOf(DataIntegrityViolationException.class, ex.getCause());
    }

    @Test
    void register_WhenPasswordValidationFails_ThrowsInvalidPasswordExceptionBeforePersistence() {
        RegisterRequest request = new RegisterRequest(
                "invalid-pw@joblivo.com",
                "Invalid PW",
                "short",
                "short"
        );

        doThrow(new InvalidPasswordException("Password must be at least 8 characters long"))
                .when(emailPasswordCredentialService).validatePassword("short");

        assertThrows(InvalidPasswordException.class, () -> registrationService.register(request));

        verifyNoInteractions(userRepository);
        verifyNoInteractions(userAuthIdentityService);
    }

    @Test
    void register_WithNullOrBlankInputs_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> registrationService.register(null));
        assertThrows(IllegalArgumentException.class, () -> registrationService.register(new RegisterRequest(null, "Name", "P@ss1234", "P@ss1234")));
        assertThrows(IllegalArgumentException.class, () -> registrationService.register(new RegisterRequest("   ", "Name", "P@ss1234", "P@ss1234")));
        assertThrows(IllegalArgumentException.class, () -> registrationService.register(new RegisterRequest("test@joblivo.com", null, "P@ss1234", "P@ss1234")));
        assertThrows(IllegalArgumentException.class, () -> registrationService.register(new RegisterRequest("test@joblivo.com", "   ", "P@ss1234", "P@ss1234")));
        assertThrows(IllegalArgumentException.class, () -> registrationService.register(new RegisterRequest("test@joblivo.com", "Name", null, "P@ss1234")));
        assertThrows(IllegalArgumentException.class, () -> registrationService.register(new RegisterRequest("test@joblivo.com", "Name", "   ", "P@ss1234")));
        assertThrows(IllegalArgumentException.class, () -> registrationService.register(new RegisterRequest("test@joblivo.com", "Name", "P@ss1234", null)));
        assertThrows(IllegalArgumentException.class, () -> registrationService.register(new RegisterRequest("test@joblivo.com", "Name", "P@ss1234", "   ")));
    }

    @Test
    void register_WhenCredentialCreationFails_PropagatesException() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "cred-fail@joblivo.com",
                "Fail User",
                "P@ssword123!",
                "P@ssword123!"
        );

        User persistedUser = createPersistedUser(UUID.randomUUID(), "cred-fail@joblivo.com", "Fail User", Instant.now());
        UserAuthIdentity persistedIdentity = createPersistedIdentity(
                UUID.randomUUID(), persistedUser, AuthProvider.EMAIL, "cred-fail@joblivo.com", Instant.now()
        );

        when(userRepository.existsByEmailIgnoreCase("cred-fail@joblivo.com")).thenReturn(false);
        when(userAuthIdentityService.existsByProviderAndSubject(AuthProvider.EMAIL, "cred-fail@joblivo.com"))
                .thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(persistedUser);
        when(userAuthIdentityService.createIdentity(persistedUser, AuthProvider.EMAIL, "cred-fail@joblivo.com"))
                .thenReturn(persistedIdentity);
        when(emailPasswordCredentialService.createCredential(persistedIdentity, "P@ssword123!"))
                .thenThrow(new IllegalArgumentException("Authentication identity must belong to a valid existing user"));

        assertThrows(IllegalArgumentException.class, () -> registrationService.register(request));
    }
}
