package com.joblivo.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void createUser_WithValidData_SavesAndReturnsActiveUser() {
        CreateUserRequest request = new CreateUserRequest("test@joblivo.com", "Test User");
        UUID generatedId = UUID.randomUUID();
        Instant now = Instant.now();
        User persistedUser = new User(generatedId, "test@joblivo.com", "Test User", UserStatus.ACTIVE, now, now);

        when(userRepository.existsByEmailIgnoreCase("test@joblivo.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(persistedUser);

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals(generatedId, response.id());
        assertEquals("test@joblivo.com", response.email());
        assertEquals("Test User", response.displayName());
        assertEquals(UserStatus.ACTIVE, response.status());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User userToSave = userCaptor.getValue();
        assertEquals("test@joblivo.com", userToSave.getEmail());
        assertEquals("Test User", userToSave.getDisplayName());
        assertEquals(UserStatus.ACTIVE, userToSave.getStatus());
        assertNull(userToSave.getId(), "Entity ID must be null prior to database-side generation");
    }

    @Test
    void createUser_NormalizesEmailAndTrimsDisplayName() {
        CreateUserRequest request = new CreateUserRequest("   MixedCase.User@Joblivo.COM   ", "   Trimmed Name   ");
        UUID generatedId = UUID.randomUUID();
        Instant now = Instant.now();
        User persistedUser = new User(generatedId, "mixedcase.user@joblivo.com", "Trimmed Name", UserStatus.ACTIVE, now, now);

        when(userRepository.existsByEmailIgnoreCase("mixedcase.user@joblivo.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(persistedUser);

        UserResponse response = userService.createUser(request);

        assertEquals("mixedcase.user@joblivo.com", response.email());
        assertEquals("Trimmed Name", response.displayName());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertEquals("mixedcase.user@joblivo.com", captor.getValue().getEmail());
        assertEquals("Trimmed Name", captor.getValue().getDisplayName());
    }

    @Test
    void createUser_WhenEmailAlreadyExists_ThrowsDuplicateEmailException() {
        CreateUserRequest request = new CreateUserRequest("existing@joblivo.com", "Existing User");
        when(userRepository.existsByEmailIgnoreCase("existing@joblivo.com")).thenReturn(true);

        DuplicateEmailException exception = assertThrows(DuplicateEmailException.class,
                () -> userService.createUser(request));

        assertTrue(exception.getMessage().contains("existing@joblivo.com"));
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createUser_WhenConcurrentDatabaseConflictOccurs_TranslatesToDuplicateEmailException() {
        CreateUserRequest request = new CreateUserRequest("race@joblivo.com", "Race User");
        when(userRepository.existsByEmailIgnoreCase("race@joblivo.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_users_email_lower\""));

        DuplicateEmailException exception = assertThrows(DuplicateEmailException.class,
                () -> userService.createUser(request));

        assertTrue(exception.getMessage().contains("race@joblivo.com"));
        assertNotNull(exception.getCause());
        assertInstanceOf(DataIntegrityViolationException.class, exception.getCause());
    }

    @Test
    void createUser_RequestModelCannotSupplyIdOrStatusOrTimestamps() {
        CreateUserRequest request = new CreateUserRequest("security@joblivo.com", "Security Check");
        assertEquals(2, CreateUserRequest.class.getRecordComponents().length);
        assertEquals("email", CreateUserRequest.class.getRecordComponents()[0].getName());
        assertEquals("displayName", CreateUserRequest.class.getRecordComponents()[1].getName());
    }

    @Test
    void createUser_WithNullOrBlankInputs_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> userService.createUser(null));
        assertThrows(IllegalArgumentException.class, () -> userService.createUser(new CreateUserRequest(null, "Name")));
        assertThrows(IllegalArgumentException.class, () -> userService.createUser(new CreateUserRequest("   ", "Name")));
        assertThrows(IllegalArgumentException.class, () -> userService.createUser(new CreateUserRequest("user@test.com", null)));
        assertThrows(IllegalArgumentException.class, () -> userService.createUser(new CreateUserRequest("user@test.com", "   ")));
    }
}
