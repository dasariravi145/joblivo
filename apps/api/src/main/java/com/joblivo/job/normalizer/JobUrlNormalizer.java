package com.joblivo.job.normalizer;

import com.joblivo.job.exception.JobValidationException;

import java.net.URI;

/**
 * Canonical deterministic URL normalizer and validator for Job Discovery.
 * <p>
 * Enforces strict URL safety and determinism:
 * <ul>
 *     <li>Trims surrounding whitespace.</li>
 *     <li>Requires {@code http} or {@code https} scheme strictly; rejects unsupported schemes
 *         ({@code ftp://}, {@code javascript:}, {@code file://}, {@code data:}, etc.).</li>
 *     <li>Requires a valid, non-blank host.</li>
 *     <li>Preserves meaningful path components, query parameters, and fragments without aggressive stripping.</li>
 *     <li>Strictly zero external network calls: does NOT follow redirects, resolve DNS, fetch pages,
 *         or prove liveness. Eliminates SSRF risks entirely.</li>
 * </ul>
 */
public final class JobUrlNormalizer {

    private JobUrlNormalizer() {
        // utility class
    }

    /**
     * Normalizes and validates a URL deterministically.
     *
     * @param url       the raw URL string (may be null or blank)
     * @param fieldName the field name for contextual validation error messages
     * @return normalized URL string (trimmed, validated HTTP/HTTPS), or {@code null} if input is null or blank
     * @throws JobValidationException if URL uses an unsupported scheme, lacks a valid host, or is malformed
     */
    public static String normalizeUrl(String url, String fieldName) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new JobValidationException(fieldName + " must use HTTP or HTTPS scheme: " + trimmed);
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new JobValidationException(fieldName + " must specify a valid host: " + trimmed);
            }
            // Preserve path, query, and fragment verbatim without destructive stripping or network queries
            return trimmed;
        } catch (IllegalArgumentException ex) {
            throw new JobValidationException(fieldName + " is a malformed URL: " + trimmed);
        }
    }

    /**
     * Normalizes and validates a URL using default field name "url".
     *
     * @param url raw URL string
     * @return normalized URL or null
     */
    public static String normalizeUrl(String url) {
        return normalizeUrl(url, "url");
    }
}
