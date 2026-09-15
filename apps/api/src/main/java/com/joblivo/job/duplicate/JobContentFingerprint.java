package com.joblivo.job.duplicate;

import com.joblivo.job.Job;
import com.joblivo.job.ingestion.JobIngestionCandidate;
import com.joblivo.job.normalizer.NormalizedJobCandidate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable canonical content fingerprint for cross-source job duplicate detection.
 * <p>
 * Evaluates core job description content to recognize identical opportunities even when source
 * identifiers, external job IDs, or job URLs differ:
 * <ul>
 *   <li>Included fields: {@code title}, {@code companyName}, {@code location}, {@code description}</li>
 *   <li>Strictly excluded fields: {@code source}, {@code externalJobId}, {@code jobUrl}, {@code companyUrl},
 *       {@code recruiterName}, {@code salary}, {@code postedAt}, {@code expiresAt}, {@code discoveredAt},
 *       {@code lastSeenAt}, {@code id}, {@code applicationMethod}</li>
 * </ul>
 * <p>
 * Uses Unicode NFC normalization, whitespace collapsing, case-insensitive normalization (Locale.ROOT),
 * explicit non-colliding field boundaries, and a 64-character lowercase SHA-256 hexadecimal digest.
 */
public record JobContentFingerprint(String hashHex) {

    public static final String NULL_SENTINEL = "<NULL>";
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    public JobContentFingerprint {
        Objects.requireNonNull(hashHex, "hashHex must not be null");
        if (hashHex.length() != 64) {
            throw new IllegalArgumentException("hashHex must be a 64-character SHA-256 hex string, received: " + hashHex);
        }
    }

    /**
     * Canonicalizes a single text attribute for fingerprinting:
     * <ul>
     *   <li>Null, blank, or empty strings are mapped to {@value #NULL_SENTINEL}.</li>
     *   <li>Applies Unicode NFC normalization.</li>
     *   <li>Trims leading and trailing whitespace.</li>
     *   <li>Collapses internal whitespace sequences to a single space.</li>
     *   <li>Converts to lowercase using {@link Locale#ROOT}.</li>
     * </ul>
     *
     * @param value raw text attribute
     * @return canonicalized string or {@value #NULL_SENTINEL}
     */
    public static String canonicalizeField(String value) {
        if (value == null || value.isBlank()) {
            return NULL_SENTINEL;
        }
        String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
        String stripped = nfc.strip();
        if (stripped.isEmpty()) {
            return NULL_SENTINEL;
        }
        String collapsed = MULTI_SPACE_PATTERN.matcher(stripped).replaceAll(" ");
        return collapsed.toLowerCase(Locale.ROOT);
    }

    /**
     * Constructs the deterministic canonical serialization with unambiguous field prefixes.
     * Prevents ambiguous concatenation boundaries (e.g. title="A" & company="BC" vs title="AB" & company="C").
     *
     * @param title       job title
     * @param companyName hiring organization name
     * @param location    job location
     * @param description job description
     * @return unambiguous formatted canonical string
     */
    public static String buildCanonicalSerialization(String title, String companyName, String location, String description) {
        return "title:" + canonicalizeField(title) + "\n"
                + "company:" + canonicalizeField(companyName) + "\n"
                + "location:" + canonicalizeField(location) + "\n"
                + "description:" + canonicalizeField(description);
    }

    /**
     * Computes the canonical SHA-256 fingerprint from discrete job content attributes.
     *
     * @param title       job title
     * @param companyName hiring organization name
     * @param location    job location
     * @param description job description
     * @return canonical JobContentFingerprint
     */
    public static JobContentFingerprint of(String title, String companyName, String location, String description) {
        String serialization = buildCanonicalSerialization(title, companyName, location, description);
        return new JobContentFingerprint(computeSha256Hex(serialization));
    }

    /**
     * Computes the canonical content fingerprint from a persisted or detached {@link Job} entity.
     *
     * @param job candidate Job entity
     * @return canonical JobContentFingerprint
     */
    public static JobContentFingerprint fromJob(Job job) {
        Objects.requireNonNull(job, "job must not be null");
        return of(job.getTitle(), job.getCompanyName(), job.getLocation(), job.getDescription());
    }

    /**
     * Computes the canonical content fingerprint from a raw {@link JobIngestionCandidate}.
     *
     * @param candidate candidate record
     * @return canonical JobContentFingerprint
     */
    public static JobContentFingerprint fromCandidate(JobIngestionCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        return of(candidate.title(), candidate.companyName(), candidate.location(), candidate.description());
    }

    /**
     * Computes the canonical content fingerprint from a {@link NormalizedJobCandidate}.
     *
     * @param candidate normalized candidate record
     * @return canonical JobContentFingerprint
     */
    public static JobContentFingerprint fromNormalizedCandidate(NormalizedJobCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        return of(candidate.title(), candidate.companyName(), candidate.location(), candidate.description());
    }

    /**
     * Computes the canonical content fingerprint from a {@link NormalizedJobCandidate}.
     *
     * @param candidate normalized candidate record
     * @return canonical JobContentFingerprint
     */
    public static JobContentFingerprint fromNormalized(NormalizedJobCandidate candidate) {
        return fromNormalizedCandidate(candidate);
    }

    private static String computeSha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm is not available in JVM", e);
        }
    }
}
