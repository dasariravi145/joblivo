package com.joblivo.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standard machine-readable error payload representation for the Joblivo API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> validationErrors
) {
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ApiErrorResponse of(int status, String error, String message, String path, Map<String, String> validationErrors) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, validationErrors);
    }
}
