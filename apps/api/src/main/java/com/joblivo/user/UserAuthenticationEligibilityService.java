package com.joblivo.user;

import org.springframework.stereotype.Service;

/**
 * Centralized domain service responsible for determining whether a User account
 * is eligible for authentication based on its status.
 */
@Service
public class UserAuthenticationEligibilityService {

    /**
     * Determines whether the given user is eligible for authentication.
     *
     * @param user the user account to evaluate
     * @return true if the user is non-null and has an eligible status; false otherwise
     */
    public boolean isEligibleForAuthentication(User user) {
        if (user == null) {
            return false;
        }
        return isStatusEligible(user.getStatus());
    }

    /**
     * Determines whether a specific UserStatus is eligible for authentication.
     * ACTIVE is eligible; SUSPENDED and DELETED are ineligible.
     *
     * @param status the status to evaluate
     * @return true if the status allows authentication; false otherwise
     */
    public boolean isStatusEligible(UserStatus status) {
        if (status == null) {
            return false;
        }
        return switch (status) {
            case ACTIVE -> true;
            case SUSPENDED, DELETED -> false;
        };
    }
}
