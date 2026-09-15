package com.joblivo.auth;

import com.joblivo.user.AuthProvider;
import com.joblivo.user.EmailPasswordCredential;
import com.joblivo.user.EmailPasswordCredentialRepository;
import com.joblivo.user.User;
import com.joblivo.user.UserAuthIdentity;
import com.joblivo.user.UserAuthIdentityService;
import com.joblivo.user.UserAuthenticationEligibilityService;
import com.joblivo.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserAuthIdentityService authIdentityService;

    @Mock
    private EmailPasswordCredentialRepository credentialRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserAuthenticationEligibilityService eligibilityService;
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        eligibilityService = new UserAuthenticationEligibilityService();
        authenticationService = new AuthenticationService(
                authIdentityService,
                credentialRepository,
                passwordEncoder,
                eligibilityService
        );
    }

    private User createPersistedUser(UUID id, String email, String displayName, UserStatus status) throws Exception {
        User user = new User(email, displayName, status);
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);

        Field createdAtField = User.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(user, Instant.now());
        return user;
    }

    private UserAuthIdentity createPersistedIdentity(UUID id, User user, AuthProvider provider, String subject) throws Exception {
        UserAuthIdentity identity = new UserAuthIdentity(user, provider, subject);
        Field idField = UserAuthIdentity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(identity, id);

        Field createdAtField = UserAuthIdentity.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(identity, Instant.now());
        return identity;
    }

    @Test
    @DisplayName("Successfully authenticates valid active user with matching password")
    void authenticate_ValidCredentials_ReturnsLoginResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        User user = createPersistedUser(userId, "ada@example.com", "Ada Lovelace", UserStatus.ACTIVE);
        UserAuthIdentity identity = createPersistedIdentity(identityId, user, AuthProvider.EMAIL, "ada@example.com");
        EmailPasswordCredential credential = new EmailPasswordCredential(identity, "$2a$10$hashedPasswordValue");

        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(Optional.of(identity));
        when(credentialRepository.findByAuthIdentityId(identityId))
                .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("ValidPassword123!", "$2a$10$hashedPasswordValue"))
                .thenReturn(true);

        LoginRequest request = new LoginRequest("ada@example.com", "ValidPassword123!");
        LoginResponse response = authenticationService.authenticate(request);

        assertNotNull(response);
        assertEquals(userId, response.id());
        assertEquals("ada@example.com", response.email());
        assertEquals("Ada Lovelace", response.displayName());
        assertEquals(AuthProvider.EMAIL, response.provider());
        assertEquals(UserStatus.ACTIVE, response.status());

        verify(authIdentityService).findIdentity(AuthProvider.EMAIL, "ada@example.com");
        verify(credentialRepository).findByAuthIdentityId(identityId);
        verify(passwordEncoder).matches("ValidPassword123!", "$2a$10$hashedPasswordValue");
    }

    @Test
    @DisplayName("Normalizes email with surrounding whitespace and mixed case")
    void authenticate_NormalizesEmail_BeforeLookup() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        User user = createPersistedUser(userId, "ada@example.com", "Ada Lovelace", UserStatus.ACTIVE);
        UserAuthIdentity identity = createPersistedIdentity(identityId, user, AuthProvider.EMAIL, "ada@example.com");
        EmailPasswordCredential credential = new EmailPasswordCredential(identity, "$2a$10$hashedPasswordValue");

        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(Optional.of(identity));
        when(credentialRepository.findByAuthIdentityId(identityId))
                .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("ValidPassword123!", "$2a$10$hashedPasswordValue"))
                .thenReturn(true);

        LoginRequest request = new LoginRequest("  ADA@Example.COM  ", "ValidPassword123!");
        LoginResponse response = authenticationService.authenticate(request);

        assertNotNull(response);
        assertEquals("ada@example.com", response.email());
        verify(authIdentityService).findIdentity(AuthProvider.EMAIL, "ada@example.com");
    }

    @Test
    @DisplayName("Preserves case sensitivity of password without trimming")
    void authenticate_PreservesPasswordCaseAndSpaces() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        User user = createPersistedUser(userId, "ada@example.com", "Ada Lovelace", UserStatus.ACTIVE);
        UserAuthIdentity identity = createPersistedIdentity(identityId, user, AuthProvider.EMAIL, "ada@example.com");
        EmailPasswordCredential credential = new EmailPasswordCredential(identity, "$2a$10$hashedPasswordValue");

        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(Optional.of(identity));
        when(credentialRepository.findByAuthIdentityId(identityId))
                .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("  LeadingTrailingPassword123!  ", "$2a$10$hashedPasswordValue"))
                .thenReturn(true);

        LoginRequest request = new LoginRequest("ada@example.com", "  LeadingTrailingPassword123!  ");
        LoginResponse response = authenticationService.authenticate(request);

        assertNotNull(response);
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).matches(passwordCaptor.capture(), eq("$2a$10$hashedPasswordValue"));
        assertEquals("  LeadingTrailingPassword123!  ", passwordCaptor.getValue());
    }

    @Test
    @DisplayName("Fails safely with generic message when identity not found")
    void authenticate_IdentityNotFound_ThrowsInvalidCredentialsException() {
        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "unknown@example.com"))
                .thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("unknown@example.com", "Password123!");
        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(request));

        assertEquals("Invalid email or password", ex.getMessage());
        verify(credentialRepository, never()).findByAuthIdentityId(any());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("Fails safely when identity provider is not EMAIL")
    void authenticate_NonEmailProvider_ThrowsInvalidCredentialsException() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        User user = createPersistedUser(userId, "ada@example.com", "Ada Lovelace", UserStatus.ACTIVE);
        UserAuthIdentity identity = createPersistedIdentity(identityId, user, AuthProvider.GOOGLE, "google-sub-123");

        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(Optional.of(identity));

        LoginRequest request = new LoginRequest("ada@example.com", "Password123!");
        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(request));

        assertEquals("Invalid email or password", ex.getMessage());
        verify(credentialRepository, never()).findByAuthIdentityId(any());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "DELETED"})
    @DisplayName("Fails safely when user status is not ACTIVE")
    void authenticate_InactiveUserStatus_ThrowsInvalidCredentialsException(UserStatus inactiveStatus) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        User user = createPersistedUser(userId, "ada@example.com", "Ada Lovelace", inactiveStatus);
        UserAuthIdentity identity = createPersistedIdentity(identityId, user, AuthProvider.EMAIL, "ada@example.com");

        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(Optional.of(identity));

        LoginRequest request = new LoginRequest("ada@example.com", "Password123!");
        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(request));

        assertEquals("Invalid email or password", ex.getMessage());
        verify(credentialRepository, never()).findByAuthIdentityId(any());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("Fails safely when credential is not found for identity")
    void authenticate_CredentialNotFound_ThrowsInvalidCredentialsException() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        User user = createPersistedUser(userId, "ada@example.com", "Ada Lovelace", UserStatus.ACTIVE);
        UserAuthIdentity identity = createPersistedIdentity(identityId, user, AuthProvider.EMAIL, "ada@example.com");

        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(Optional.of(identity));
        when(credentialRepository.findByAuthIdentityId(identityId))
                .thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("ada@example.com", "Password123!");
        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(request));

        assertEquals("Invalid email or password", ex.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("Fails safely when password encoder matches returns false")
    void authenticate_PasswordMismatch_ThrowsInvalidCredentialsException() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        User user = createPersistedUser(userId, "ada@example.com", "Ada Lovelace", UserStatus.ACTIVE);
        UserAuthIdentity identity = createPersistedIdentity(identityId, user, AuthProvider.EMAIL, "ada@example.com");
        EmailPasswordCredential credential = new EmailPasswordCredential(identity, "$2a$10$hashedPasswordValue");

        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(Optional.of(identity));
        when(credentialRepository.findByAuthIdentityId(identityId))
                .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("WrongPassword123!", "$2a$10$hashedPasswordValue"))
                .thenReturn(false);

        LoginRequest request = new LoginRequest("ada@example.com", "WrongPassword123!");
        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(request));

        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    @DisplayName("Fails safely when request or its fields are null")
    void authenticate_NullRequestOrFields_ThrowsInvalidCredentialsException() {
        assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(null));
        assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(new LoginRequest(null, "Password123!")));
        assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(new LoginRequest("ada@example.com", null)));
    }

    @Test
    @DisplayName("All failure modes produce identical generic exception without leaking account existence")
    void authenticate_AllFailureModes_ProduceIdenticalGenericException() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();

        // 1. Unknown email
        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "unknown@example.com"))
                .thenReturn(Optional.empty());
        InvalidCredentialsException ex1 = assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(new LoginRequest("unknown@example.com", "Secret123!")));
        assertEquals(InvalidCredentialsException.DEFAULT_MESSAGE, ex1.getMessage());

        // 2. Suspended user
        User suspendedUser = createPersistedUser(userId, "suspended@example.com", "Suspended", UserStatus.SUSPENDED);
        UserAuthIdentity suspendedIdentity = createPersistedIdentity(identityId, suspendedUser, AuthProvider.EMAIL, "suspended@example.com");
        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "suspended@example.com"))
                .thenReturn(Optional.of(suspendedIdentity));
        InvalidCredentialsException ex2 = assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(new LoginRequest("suspended@example.com", "Secret123!")));
        assertEquals(InvalidCredentialsException.DEFAULT_MESSAGE, ex2.getMessage());

        // 3. Deleted user
        User deletedUser = createPersistedUser(userId, "deleted@example.com", "Deleted", UserStatus.DELETED);
        UserAuthIdentity deletedIdentity = createPersistedIdentity(identityId, deletedUser, AuthProvider.EMAIL, "deleted@example.com");
        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "deleted@example.com"))
                .thenReturn(Optional.of(deletedIdentity));
        InvalidCredentialsException ex3 = assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(new LoginRequest("deleted@example.com", "Secret123!")));
        assertEquals(InvalidCredentialsException.DEFAULT_MESSAGE, ex3.getMessage());

        // 4. Incorrect password
        User activeUser = createPersistedUser(userId, "active@example.com", "Active", UserStatus.ACTIVE);
        UserAuthIdentity activeIdentity = createPersistedIdentity(identityId, activeUser, AuthProvider.EMAIL, "active@example.com");
        EmailPasswordCredential credential = new EmailPasswordCredential(activeIdentity, "$2a$10$hash");
        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "active@example.com"))
                .thenReturn(Optional.of(activeIdentity));
        when(credentialRepository.findByAuthIdentityId(identityId))
                .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("WrongPass!", "$2a$10$hash"))
                .thenReturn(false);

        InvalidCredentialsException ex4 = assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(new LoginRequest("active@example.com", "WrongPass!")));
        assertEquals(InvalidCredentialsException.DEFAULT_MESSAGE, ex4.getMessage());

        // Assert all failure messages are exactly identical
        assertEquals(ex1.getMessage(), ex2.getMessage());
        assertEquals(ex2.getMessage(), ex3.getMessage());
        assertEquals(ex3.getMessage(), ex4.getMessage());
    }

    @Test
    @DisplayName("SUSPENDED or DELETED user with correct password cannot authenticate and does not leak status")
    void authenticate_InactiveUserWithCorrectPassword_FailsGenerically() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();

        // Suspended user
        User suspendedUser = createPersistedUser(userId, "suspended@example.com", "Suspended", UserStatus.SUSPENDED);
        UserAuthIdentity suspendedIdentity = createPersistedIdentity(identityId, suspendedUser, AuthProvider.EMAIL, "suspended@example.com");
        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "suspended@example.com"))
                .thenReturn(Optional.of(suspendedIdentity));

        InvalidCredentialsException ex1 = assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(new LoginRequest("suspended@example.com", "CorrectPassword123!")));

        assertEquals("Invalid email or password", ex1.getMessage());
        assertFalse(ex1.getMessage().contains("SUSPENDED"));
        verify(passwordEncoder, never()).matches(any(), any());
        verify(credentialRepository, never()).findByAuthIdentityId(any());

        // Deleted user
        User deletedUser = createPersistedUser(userId, "deleted@example.com", "Deleted", UserStatus.DELETED);
        UserAuthIdentity deletedIdentity = createPersistedIdentity(identityId, deletedUser, AuthProvider.EMAIL, "deleted@example.com");
        when(authIdentityService.findIdentity(AuthProvider.EMAIL, "deleted@example.com"))
                .thenReturn(Optional.of(deletedIdentity));

        InvalidCredentialsException ex2 = assertThrows(InvalidCredentialsException.class,
                () -> authenticationService.authenticate(new LoginRequest("deleted@example.com", "CorrectPassword123!")));

        assertEquals("Invalid email or password", ex2.getMessage());
        assertFalse(ex2.getMessage().contains("DELETED"));
        verify(passwordEncoder, never()).matches(any(), any());
    }
}
