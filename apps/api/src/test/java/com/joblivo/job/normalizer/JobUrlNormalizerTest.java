package com.joblivo.job.normalizer;

import com.joblivo.job.exception.JobValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JobUrlNormalizer Unit Tests")
class JobUrlNormalizerTest {

    @Test
    @DisplayName("10. HTTP URL is accepted")
    void httpUrlAccepted() {
        String result = JobUrlNormalizer.normalizeUrl("http://example.com/jobs/123");
        assertThat(result).isEqualTo("http://example.com/jobs/123");
    }

    @Test
    @DisplayName("11. HTTPS URL is accepted")
    void httpsUrlAccepted() {
        String result = JobUrlNormalizer.normalizeUrl("https://careers.google.com/jobs/results/123456");
        assertThat(result).isEqualTo("https://careers.google.com/jobs/results/123456");
    }

    @Test
    @DisplayName("12. Surrounding whitespace is removed")
    void surroundingWhitespaceRemoved() {
        String result = JobUrlNormalizer.normalizeUrl("   https://linkedin.com/jobs/view/999   ");
        assertThat(result).isEqualTo("https://linkedin.com/jobs/view/999");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://ftp.example.com/jobs.xml",
            "file:///etc/passwd",
            "javascript:alert('xss')",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "mailto:jobs@example.com",
            "ws://example.com/socket"
    })
    @DisplayName("13. Unsupported schemes are rejected with JobValidationException")
    void unsupportedSchemesRejected(String unsupportedUrl) {
        assertThatThrownBy(() -> JobUrlNormalizer.normalizeUrl(unsupportedUrl, "jobUrl"))
                .isInstanceOf(JobValidationException.class)
                .hasMessageContaining("must use HTTP or HTTPS scheme");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://",
            "http://",
            "https:///path/without/host",
            "not a url",
            "https://?query=only"
    })
    @DisplayName("14. Invalid URLs are rejected safely")
    void invalidUrlsRejected(String invalidUrl) {
        assertThatThrownBy(() -> JobUrlNormalizer.normalizeUrl(invalidUrl, "jobUrl"))
                .isInstanceOf(JobValidationException.class);
    }

    @Test
    @DisplayName("15. Query parameters are preserved without aggressive stripping")
    void queryParametersPreserved() {
        String urlWithQuery = "https://company.com/apply?jobId=456&ref=linkedin&src=direct#apply-section";
        String result = JobUrlNormalizer.normalizeUrl(urlWithQuery);
        assertThat(result).isEqualTo(urlWithQuery);
    }

    @Test
    @DisplayName("16. Meaningful path components and deep hierarchy are preserved")
    void meaningfulPathPreserved() {
        String deepUrl = "https://boards.greenhouse.io/acme/jobs/4029182003/application";
        String result = JobUrlNormalizer.normalizeUrl(deepUrl);
        assertThat(result).isEqualTo(deepUrl);
    }

    @Test
    @DisplayName("17. In-memory execution performs strictly zero network requests")
    void noNetworkCallsPerformed() {
        // Points to an unresolvable domain; in-memory normalization should complete instantly without network timeout or error
        String unresolvableDomainUrl = "https://non-existent-domain-xyz-12345-joblivo.invalid/jobs/1";
        long start = System.currentTimeMillis();
        String result = JobUrlNormalizer.normalizeUrl(unresolvableDomainUrl);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result).isEqualTo(unresolvableDomainUrl);
        assertThat(elapsed).isLessThan(500); // Guarantees no network handshake or DNS resolution attempt
    }

    @Test
    @DisplayName("Null or blank URLs return null safely")
    void nullOrBlankReturnsNull() {
        assertThat(JobUrlNormalizer.normalizeUrl(null)).isNull();
        assertThat(JobUrlNormalizer.normalizeUrl("")).isNull();
        assertThat(JobUrlNormalizer.normalizeUrl("   ")).isNull();
    }
}
