package com.joblivo.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class UserAuthenticationEligibilityServiceTest {

    private UserAuthenticationEligibilityService eligibilityService;

    @BeforeEach
    void setUp() {
        eligibilityService = new UserAuthenticationEligibilityService();
    }

    @Test
    @DisplayName("ACTIVE status is eligible for authentication")
    void isStatusEligible_ActiveStatus_ReturnsTrue() {
        assertTrue(eligibilityService.isStatusEligible(UserStatus.ACTIVE));
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "DELETED"})
    @DisplayName("SUSPENDED and DELETED statuses are ineligible for authentication")
    void isStatusEligible_InactiveStatuses_ReturnFalse(UserStatus status) {
        assertFalse(eligibilityService.isStatusEligible(status));
    }

    @Test
    @DisplayName("Null status is ineligible for authentication")
    void isStatusEligible_NullStatus_ReturnsFalse() {
        assertFalse(eligibilityService.isStatusEligible(null));
    }

    @Test
    @DisplayName("User with ACTIVE status is eligible for authentication")
    void isEligibleForAuthentication_ActiveUser_ReturnsTrue() {
        User user = new User("ada@example.com", "Ada Lovelace", UserStatus.ACTIVE);
        assertTrue(eligibilityService.isEligibleForAuthentication(user));
    }

    @Test
    @DisplayName("User created with default constructor is ACTIVE and eligible for authentication")
    void isEligibleForAuthentication_DefaultUser_ReturnsTrue() {
        User user = new User("ada@example.com", "Ada Lovelace");
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(eligibilityService.isEligibleForAuthentication(user));
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "DELETED"})
    @DisplayName("User with SUSPENDED or DELETED status is ineligible for authentication")
    void isEligibleForAuthentication_InactiveUser_ReturnsFalse(UserStatus inactiveStatus) {
        User user = new User("ada@example.com", "Ada Lovelace", inactiveStatus);
        assertFalse(eligibilityService.isEligibleForAuthentication(user));
    }

    @Test
    @DisplayName("Null user is ineligible for authentication")
    void isEligibleForAuthentication_NullUser_ReturnsFalse() {
        assertFalse(eligibilityService.isEligibleForAuthentication(null));
    }
}
