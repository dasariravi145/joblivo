package com.joblivo.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserTest {

    @Test
    void testUserCreationDefaults() {
        User user = new User("test@example.com", "Test User");

        assertNull(user.getId(), "ID should be null before persistence");
        assertEquals("test@example.com", user.getEmail());
        assertEquals("Test User", user.getDisplayName());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNull(user.getCreatedAt());
        assertNull(user.getUpdatedAt());
    }

    @Test
    void testUserStatusEnumValues() {
        assertEquals("ACTIVE", UserStatus.ACTIVE.name());
        assertEquals("SUSPENDED", UserStatus.SUSPENDED.name());
        assertEquals("DELETED", UserStatus.DELETED.name());
        assertEquals(3, UserStatus.values().length);
    }

    @Test
    void testLifecycleCallbacks() {
        User user = new User("dev@joblivo.com", "Dev User");
        user.onCreate();

        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }
}
