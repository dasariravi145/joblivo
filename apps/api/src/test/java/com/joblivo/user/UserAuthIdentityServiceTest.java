package com.joblivo.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAuthIdentityServiceTest {

    @Mock
    private UserAuthIdentityRepository userAuthIdentityRepository;

    private UserAuthIdentityService userAuthIdentityService;

    @BeforeEach
    void setUp() {
        userAuthIdentityService = new UserAuthIdentityService(userAuthIdentityRepository);
    }

    private User createPersistedUser(UUID id, String email, String displayName) throws Exception {
        User user = new User(email, displayName);
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

    // =========================================================================
    // Retrieval & Provider Isolation
    // =========================================================================

    @Test
    @DisplayName("Finds EMAIL identity with canonical normalization (trim and lowercase)")
    void findIdentity_EmailProvider_NormalizesSubjectAndReturnsIdentity() {
        User user = new User("ada@example.com", "Ada Lovelace");
        UserAuthIdentity identity = new UserAuthIdentity(user, AuthProvider.EMAIL, "ada@example.com");

        when(userAuthIdentityRepository.findByProviderAndProviderSubject(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(Optional.of(identity));

        Optional<UserAuthIdentity> result = userAuthIdentityService.findIdentity(
                AuthProvider.EMAIL,
                "   Ada@EXAMPLE.com   "
        );

        assertTrue(result.isPresent(), "Expected identity to be found");
        assertEquals(identity, result.get());
        assertEquals(AuthProvider.EMAIL, result.get().getProvider());
        verify(userAuthIdentityRepository).findByProviderAndProviderSubject(AuthProvider.EMAIL, "ada@example.com");
    }

    @Test
    @DisplayName("Finds non-EMAIL provider identity with trimmed subject")
    void findIdentity_NonEmailProvider_TrimsSubjectAndQueriesExactProvider() {
        User user = new User("ada@example.com", "Ada Lovelace");
        UserAuthIdentity identity = new UserAuthIdentity(user, AuthProvider.GOOGLE, "google-oauth2|12345");

        when(userAuthIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-oauth2|12345"))
                .thenReturn(Optional.of(identity));

        Optional<UserAuthIdentity> result = userAuthIdentityService.findIdentity(
                AuthProvider.GOOGLE,
                "  google-oauth2|12345  "
        );

        assertTrue(result.isPresent());
        assertEquals(identity, result.get());
        assertEquals(AuthProvider.GOOGLE, result.get().getProvider());
        verify(userAuthIdentityRepository).findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-oauth2|12345");
    }

    @Test
    @DisplayName("Provider isolation: Identity from another provider with same subject is not returned for EMAIL")
    void findIdentity_DifferentProviderSameSubject_DoesNotReturnForEmail() {
        when(userAuthIdentityRepository.findByProviderAndProviderSubject(AuthProvider.EMAIL, "user@example.com"))
                .thenReturn(Optional.empty());

        Optional<UserAuthIdentity> result = userAuthIdentityService.findIdentity(
                AuthProvider.EMAIL,
                "user@example.com"
        );

        assertTrue(result.isEmpty(), "Must return empty when EMAIL identity does not exist");
        verify(userAuthIdentityRepository).findByProviderAndProviderSubject(AuthProvider.EMAIL, "user@example.com");
        verify(userAuthIdentityRepository, never()).findByProviderAndProviderSubject(eq(AuthProvider.GOOGLE), anyString());
    }

    @Test
    @DisplayName("Provider isolation defense: Filters out identity if repository returns mismatched provider")
    void findIdentity_DefensiveFilter_RejectsMismatchedProvider() {
        User user = new User("ada@example.com", "Ada Lovelace");
        UserAuthIdentity googleIdentity = new UserAuthIdentity(user, AuthProvider.GOOGLE, "ada@example.com");

        when(userAuthIdentityRepository.findByProviderAndProviderSubject(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(Optional.of(googleIdentity));

        Optional<UserAuthIdentity> result = userAuthIdentityService.findIdentity(
                AuthProvider.EMAIL,
                "ada@example.com"
        );

        assertTrue(result.isEmpty(), "Mismatched provider must be filtered out for strict isolation");
    }

    @Test
    @DisplayName("Returns empty when provider is null")
    void findIdentity_NullProvider_ReturnsEmptyWithoutQueryingRepository() {
        Optional<UserAuthIdentity> result = userAuthIdentityService.findIdentity(null, "user@example.com");

        assertTrue(result.isEmpty());
        verifyNoInteractions(userAuthIdentityRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Returns empty when providerSubject is null or blank")
    void findIdentity_NullOrBlankSubject_ReturnsEmptyWithoutQueryingRepository(String invalidSubject) {
        Optional<UserAuthIdentity> result = userAuthIdentityService.findIdentity(AuthProvider.EMAIL, invalidSubject);

        assertTrue(result.isEmpty());
        verifyNoInteractions(userAuthIdentityRepository);
    }

    @Test
    @DisplayName("findEmailIdentity delegates with AuthProvider.EMAIL")
    void findEmailIdentity_ValidEmail_DelegatesToFindIdentity() {
        User user = new User("ada@example.com", "Ada Lovelace");
        UserAuthIdentity identity = new UserAuthIdentity(user, AuthProvider.EMAIL, "ada@example.com");

        when(userAuthIdentityRepository.findByProviderAndProviderSubject(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(Optional.of(identity));

        Optional<UserAuthIdentity> result = userAuthIdentityService.findEmailIdentity("  ADA@EXAMPLE.COM  ");

        assertTrue(result.isPresent());
        assertEquals(identity, result.get());
        verify(userAuthIdentityRepository).findByProviderAndProviderSubject(AuthProvider.EMAIL, "ada@example.com");
    }

    @Test
    @DisplayName("existsByProviderAndSubject normalizes subject and returns existence status")
    void existsByProviderAndSubject_ValidInput_NormalizesAndChecks() {
        when(userAuthIdentityRepository.existsByProviderAndProviderSubject(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(true);

        boolean exists = userAuthIdentityService.existsByProviderAndSubject(
                AuthProvider.EMAIL,
                "  ADA@EXAMPLE.COM  "
        );

        assertTrue(exists);
        verify(userAuthIdentityRepository).existsByProviderAndProviderSubject(AuthProvider.EMAIL, "ada@example.com");
    }

    @Test
    @DisplayName("existsByProviderAndSubject returns false for null or blank inputs")
    void existsByProviderAndSubject_NullOrBlank_ReturnsFalse() {
        assertFalse(userAuthIdentityService.existsByProviderAndSubject(null, "subject"));
        assertFalse(userAuthIdentityService.existsByProviderAndSubject(AuthProvider.EMAIL, null));
        assertFalse(userAuthIdentityService.existsByProviderAndSubject(AuthProvider.EMAIL, "   "));
        verifyNoInteractions(userAuthIdentityRepository);
    }

    @Test
    @DisplayName("existsByEmail delegates to existsByProviderAndSubject with EMAIL provider")
    void existsByEmail_ValidEmail_DelegatesCorrectly() {
        when(userAuthIdentityRepository.existsByProviderAndProviderSubject(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(true);

        assertTrue(userAuthIdentityService.existsByEmail("ADA@EXAMPLE.COM"));
        verify(userAuthIdentityRepository).existsByProviderAndProviderSubject(AuthProvider.EMAIL, "ada@example.com");
    }

    // =========================================================================
    // Safe Identity Creation
    // =========================================================================

    @Test
    @DisplayName("createIdentity creates and persists EMAIL identity with normalized subject")
    void createIdentity_ValidEmailInput_CreatesAndPersistsNormalizedIdentity() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "ada@example.com", "Ada Lovelace");

        when(userAuthIdentityRepository.existsByProviderAndProviderSubject(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(false);
        when(userAuthIdentityRepository.saveAndFlush(any(UserAuthIdentity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAuthIdentity created = userAuthIdentityService.createIdentity(
                user,
                AuthProvider.EMAIL,
                "   ADA@EXAMPLE.COM   "
        );

        assertNotNull(created);
        assertEquals(user, created.getUser());
        assertEquals(AuthProvider.EMAIL, created.getProvider());
        assertEquals("ada@example.com", created.getProviderSubject());

        ArgumentCaptor<UserAuthIdentity> captor = ArgumentCaptor.forClass(UserAuthIdentity.class);
        verify(userAuthIdentityRepository).saveAndFlush(captor.capture());
        assertEquals("ada@example.com", captor.getValue().getProviderSubject());
    }

    @Test
    @DisplayName("createIdentity rejects null user or unpersisted user without ID")
    void createIdentity_NullOrUnpersistedUser_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> userAuthIdentityService.createIdentity(null, AuthProvider.EMAIL, "ada@example.com"));

        User unpersistedUser = new User("ada@example.com", "Ada Lovelace");
        assertThrows(IllegalArgumentException.class,
                () -> userAuthIdentityService.createIdentity(unpersistedUser, AuthProvider.EMAIL, "ada@example.com"));

        verifyNoInteractions(userAuthIdentityRepository);
    }

    @Test
    @DisplayName("createIdentity rejects null provider")
    void createIdentity_NullProvider_ThrowsIllegalArgumentException() throws Exception {
        User user = createPersistedUser(UUID.randomUUID(), "ada@example.com", "Ada Lovelace");

        assertThrows(IllegalArgumentException.class,
                () -> userAuthIdentityService.createIdentity(user, null, "ada@example.com"));

        verifyNoInteractions(userAuthIdentityRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("createIdentity rejects null or blank provider subject")
    void createIdentity_NullOrBlankSubject_ThrowsIllegalArgumentException(String invalidSubject) throws Exception {
        User user = createPersistedUser(UUID.randomUUID(), "ada@example.com", "Ada Lovelace");

        assertThrows(IllegalArgumentException.class,
                () -> userAuthIdentityService.createIdentity(user, AuthProvider.EMAIL, invalidSubject));

        verifyNoInteractions(userAuthIdentityRepository);
    }

    @Test
    @DisplayName("createIdentity throws DuplicateAuthIdentityException when identity already exists")
    void createIdentity_DuplicateIdentity_ThrowsDuplicateAuthIdentityException() throws Exception {
        User user = createPersistedUser(UUID.randomUUID(), "ada@example.com", "Ada Lovelace");

        when(userAuthIdentityRepository.existsByProviderAndProviderSubject(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(true);

        DuplicateAuthIdentityException ex = assertThrows(DuplicateAuthIdentityException.class,
                () -> userAuthIdentityService.createIdentity(user, AuthProvider.EMAIL, "ada@example.com"));

        assertTrue(ex.getMessage().contains("EMAIL"));
        assertTrue(ex.getMessage().contains("ada@example.com"));
        verify(userAuthIdentityRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("createIdentity translates DataIntegrityViolationException to DuplicateAuthIdentityException")
    void createIdentity_DataIntegrityViolation_ThrowsDuplicateAuthIdentityException() throws Exception {
        User user = createPersistedUser(UUID.randomUUID(), "ada@example.com", "Ada Lovelace");

        when(userAuthIdentityRepository.existsByProviderAndProviderSubject(AuthProvider.EMAIL, "ada@example.com"))
                .thenReturn(false);
        when(userAuthIdentityRepository.saveAndFlush(any(UserAuthIdentity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_user_auth_identities_provider_subject violated"));

        DuplicateAuthIdentityException ex = assertThrows(DuplicateAuthIdentityException.class,
                () -> userAuthIdentityService.createIdentity(user, AuthProvider.EMAIL, "ada@example.com"));

        assertNotNull(ex.getCause());
        assertTrue(ex.getMessage().contains("ada@example.com"));
    }

    // =========================================================================
    // User Ownership & User-Scoped Queries
    // =========================================================================

    @Test
    @DisplayName("findIdentityForUser returns identity when owned by the specified user")
    void findIdentityForUser_OwnedByUser_ReturnsIdentity() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = createPersistedUser(userId, "ada@example.com", "Ada Lovelace");
        UserAuthIdentity identity = createPersistedIdentity(UUID.randomUUID(), user, AuthProvider.EMAIL, "ada@example.com");

        when(userAuthIdentityRepository.findByUserIdAndProvider(userId, AuthProvider.EMAIL))
                .thenReturn(Optional.of(identity));

        Optional<UserAuthIdentity> result = userAuthIdentityService.findIdentityForUser(userId, AuthProvider.EMAIL);

        assertTrue(result.isPresent());
        assertEquals(identity, result.get());
        assertEquals(userId, result.get().getUser().getId());
    }

    @Test
    @DisplayName("findIdentityForUser returns empty when identity in repository belongs to a different user")
    void findIdentityForUser_BelongsToDifferentUser_ReturnsEmpty() throws Exception {
        UUID requestingUserId = UUID.randomUUID();
        UUID actualOwnerId = UUID.randomUUID();
        User actualOwner = createPersistedUser(actualOwnerId, "owner@example.com", "Actual Owner");
        UserAuthIdentity identity = createPersistedIdentity(UUID.randomUUID(), actualOwner, AuthProvider.EMAIL, "owner@example.com");

        when(userAuthIdentityRepository.findByUserIdAndProvider(requestingUserId, AuthProvider.EMAIL))
                .thenReturn(Optional.of(identity));

        Optional<UserAuthIdentity> result = userAuthIdentityService.findIdentityForUser(requestingUserId, AuthProvider.EMAIL);

        assertTrue(result.isEmpty(), "Must not return an identity belonging to another user");
    }

    @Test
    @DisplayName("findIdentityForUser with subject returns identity only when owned by the specified user")
    void findIdentityForUserWithSubject_EnforcesUserOwnership() throws Exception {
        UUID userAId = UUID.randomUUID();
        UUID userBId = UUID.randomUUID();
        User userA = createPersistedUser(userAId, "userA@example.com", "User A");
        UserAuthIdentity identityA = createPersistedIdentity(UUID.randomUUID(), userA, AuthProvider.EMAIL, "usera@example.com");

        when(userAuthIdentityRepository.findByProviderAndProviderSubject(AuthProvider.EMAIL, "usera@example.com"))
                .thenReturn(Optional.of(identityA));

        // User A querying their own identity -> present
        Optional<UserAuthIdentity> resultA = userAuthIdentityService.findIdentityForUser(
                userAId, AuthProvider.EMAIL, "userA@example.com"
        );
        assertTrue(resultA.isPresent());
        assertEquals(identityA, resultA.get());

        // User B querying User A's identity -> empty (strictly prevented from accessing User A's identity)
        Optional<UserAuthIdentity> resultB = userAuthIdentityService.findIdentityForUser(
                userBId, AuthProvider.EMAIL, "userA@example.com"
        );
        assertTrue(resultB.isEmpty(), "User B must not be able to retrieve User A's identity");
    }

    @Test
    @DisplayName("findIdentityForUser returns empty on null arguments")
    void findIdentityForUser_NullArguments_ReturnsEmpty() {
        assertTrue(userAuthIdentityService.findIdentityForUser(null, AuthProvider.EMAIL).isEmpty());
        assertTrue(userAuthIdentityService.findIdentityForUser(UUID.randomUUID(), null).isEmpty());
        assertTrue(userAuthIdentityService.findIdentityForUser(null, AuthProvider.EMAIL, "subject").isEmpty());
        assertTrue(userAuthIdentityService.findIdentityForUser(UUID.randomUUID(), null, "subject").isEmpty());
        assertTrue(userAuthIdentityService.findIdentityForUser(UUID.randomUUID(), AuthProvider.EMAIL, null).isEmpty());
        assertTrue(userAuthIdentityService.findIdentityForUser(UUID.randomUUID(), AuthProvider.EMAIL, "  ").isEmpty());
        verifyNoInteractions(userAuthIdentityRepository);
    }

    @Test
    @DisplayName("hasIdentityForProvider returns repository check result")
    void hasIdentityForProvider_ValidInput_ReturnsRepositoryValue() {
        UUID userId = UUID.randomUUID();
        when(userAuthIdentityRepository.existsByUserIdAndProvider(userId, AuthProvider.EMAIL)).thenReturn(true);

        assertTrue(userAuthIdentityService.hasIdentityForProvider(userId, AuthProvider.EMAIL));
        verify(userAuthIdentityRepository).existsByUserIdAndProvider(userId, AuthProvider.EMAIL);

        assertFalse(userAuthIdentityService.hasIdentityForProvider(null, AuthProvider.EMAIL));
        assertFalse(userAuthIdentityService.hasIdentityForProvider(userId, null));
    }

    // =========================================================================
    // Security & Credential Isolation
    // =========================================================================

    @Test
    @DisplayName("Security: UserAuthIdentityService methods never accept or expose raw passwords or hashes")
    void securityReview_NoCredentialExposureInServiceApi() {
        List<String> prohibitedTerms = List.of("password", "hash", "secret", "token", "otp", "pin");

        for (var method : UserAuthIdentityService.class.getDeclaredMethods()) {
            String methodName = method.getName().toLowerCase();
            for (String prohibited : prohibitedTerms) {
                assertFalse(methodName.contains(prohibited),
                        "UserAuthIdentityService method must not contain credential terms: " + method.getName());
            }
            for (Class<?> paramType : method.getParameterTypes()) {
                assertFalse(paramType.getName().toLowerCase().contains("password"),
                        "UserAuthIdentityService parameter must not be password type: " + paramType.getName());
            }
        }
    }
}
