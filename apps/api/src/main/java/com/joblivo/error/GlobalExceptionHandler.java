package com.joblivo.error;

import com.joblivo.auth.InvalidCredentialsException;
import com.joblivo.profile.DuplicateCareerProfileException;
import com.joblivo.user.DuplicateAuthIdentityException;
import com.joblivo.user.DuplicateCredentialException;
import com.joblivo.user.DuplicateEmailException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.joblivo.job.exception.JobNotFoundException;
import com.joblivo.job.exception.JobValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized exception translation translating domain and validation exceptions
 * into structured, client-safe API error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for request payload",
                request.getRequestURI(),
                validationErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Malformed JSON request body",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : "Invalid request payload or argument";
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler({
            com.joblivo.profile.CareerProfileNotFoundException.class,
            com.joblivo.profile.WorkExperienceNotFoundException.class,
            com.joblivo.profile.SkillNotFoundException.class,
            com.joblivo.profile.ProjectNotFoundException.class,
            com.joblivo.profile.EducationNotFoundException.class,
            com.joblivo.profile.CertificationNotFoundException.class,
            com.joblivo.profile.AchievementNotFoundException.class,
            JobNotFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handleResourceNotFoundException(
            RuntimeException ex, HttpServletRequest request) {
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(JobValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleJobValidationException(
            JobValidationException ex, HttpServletRequest request) {
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler({
            DuplicateEmailException.class,
            DuplicateCredentialException.class,
            DuplicateAuthIdentityException.class,
            DuplicateCareerProfileException.class,
            com.joblivo.profile.DuplicateSkillException.class,
            com.joblivo.profile.DuplicateProjectException.class,
            com.joblivo.profile.DuplicateEducationException.class,
            com.joblivo.profile.DuplicateCertificationException.class,
            com.joblivo.profile.DuplicateAchievementException.class
    })
    public ResponseEntity<ApiErrorResponse> handleDuplicateResourceException(
            RuntimeException ex, HttpServletRequest request) {
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Database constraint violation at {}: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "A database constraint was violated. The resource may already exist.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentialsException(
            InvalidCredentialsException ex, HttpServletRequest request) {
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                InvalidCredentialsException.DEFAULT_MESSAGE,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing request to: {}", request.getRequestURI(), ex);
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected internal error occurred. Please contact support if the issue persists.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
