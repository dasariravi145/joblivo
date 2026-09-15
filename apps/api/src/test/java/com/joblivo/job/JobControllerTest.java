package com.joblivo.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joblivo.error.GlobalExceptionHandler;
import com.joblivo.job.exception.JobNotFoundException;
import com.joblivo.job.exception.JobValidationException;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobFreshnessStatus;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
import com.joblivo.job.service.JobResponse;
import com.joblivo.job.service.JobSearchCriteria;
import com.joblivo.job.service.JobSearchResponse;
import com.joblivo.job.service.JobSearchService;
import com.joblivo.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("JobController WebMvc Tests")
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobSearchService jobSearchService;

    private JobResponse sampleJobResponse(UUID id) {
        return sampleJobResponse(id, JobFreshnessStatus.ACTIVE);
    }

    private JobResponse sampleJobResponse(UUID id, JobFreshnessStatus freshnessStatus) {
        Instant now = Instant.now();
        return new JobResponse(
                id,
                JobSource.LINKEDIN,
                "ext-linkedin-101",
                "Principal Architect",
                "Enterprise Systems Inc",
                "Alice Headhunter",
                "Lead enterprise architecture transformation.",
                "Austin, TX",
                JobWorkMode.REMOTE,
                JobEmploymentType.FULL_TIME,
                8,
                15,
                BigDecimal.valueOf(190000),
                BigDecimal.valueOf(250000),
                "USD",
                SalaryPeriod.YEAR,
                "https://linkedin.com/jobs/view/101",
                "https://enterprisesystems.com",
                now.minus(3, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                now.minus(2, ChronoUnit.DAYS),
                now,
                JobApplicationMethod.ATS,
                now.minus(2, ChronoUnit.DAYS),
                now,
                freshnessStatus
        );
    }

    @Nested
    @DisplayName("GET /api/v1/jobs - Authentication Enforcement")
    class SearchAuthenticationTests {

        @Test
        @DisplayName("Unauthenticated request returns 401 Unauthorized")
        void searchUnauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithAnonymousUser
        @DisplayName("Explicit anonymousUser returns 401 Unauthorized")
        void searchAnonymous_Returns401() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(jobSearchService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/jobs - Authenticated Discovery & Search")
    class SearchExecutionTests {

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Authenticated search returns 200 OK and paginated JobSearchResponse")
        void searchAuthenticated_Success() throws Exception {
            UUID jobId = UUID.randomUUID();
            JobResponse job = sampleJobResponse(jobId);
            JobSearchResponse searchResponse = new JobSearchResponse(
                    List.of(job),
                    0,
                    20,
                    1L,
                    1,
                    false,
                    false
            );

            when(jobSearchService.search(any(JobSearchCriteria.class))).thenReturn(searchResponse);

            mockMvc.perform(get("/api/v1/jobs")
                            .param("keyword", "Architect")
                            .param("location", "Austin")
                            .param("workMode", "REMOTE")
                            .param("employmentType", "FULL_TIME")
                            .param("source", "LINKEDIN")
                            .param("minimumExperience", "8")
                            .param("maximumExperience", "15")
                            .param("minimumSalary", "150000")
                            .param("maximumSalary", "300000")
                            .param("page", "0")
                            .param("size", "20")
                            .param("sort", "newest")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobs", hasSize(1)))
                    .andExpect(jsonPath("$.jobs[0].id", is(jobId.toString())))
                    .andExpect(jsonPath("$.jobs[0].title", is("Principal Architect")))
                    .andExpect(jsonPath("$.jobs[0].companyName", is("Enterprise Systems Inc")))
                    .andExpect(jsonPath("$.jobs[0].workMode", is("REMOTE")))
                    .andExpect(jsonPath("$.jobs[0].employmentType", is("FULL_TIME")))
                    .andExpect(jsonPath("$.jobs[0].source", is("LINKEDIN")))
                    .andExpect(jsonPath("$.jobs[0].freshnessStatus", is("ACTIVE")))
                    .andExpect(jsonPath("$.page", is(0)))
                    .andExpect(jsonPath("$.size", is(20)))
                    .andExpect(jsonPath("$.totalElements", is(1)))
                    .andExpect(jsonPath("$.totalPages", is(1)))
                    .andExpect(jsonPath("$.hasNext", is(false)))
                    .andExpect(jsonPath("$.hasPrevious", is(false)));

            ArgumentCaptor<JobSearchCriteria> captor = ArgumentCaptor.forClass(JobSearchCriteria.class);
            verify(jobSearchService).search(captor.capture());
            JobSearchCriteria captured = captor.getValue();
            assertThat(captured.keyword()).isEqualTo("Architect");
            assertThat(captured.location()).isEqualTo("Austin");
            assertThat(captured.workMode()).isEqualTo(JobWorkMode.REMOTE);
            assertThat(captured.employmentType()).isEqualTo(JobEmploymentType.FULL_TIME);
            assertThat(captured.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(captured.minimumExperience()).isEqualTo(8);
            assertThat(captured.maximumExperience()).isEqualTo(15);
            assertThat(captured.minimumSalary()).isEqualByComparingTo(BigDecimal.valueOf(150000));
            assertThat(captured.maximumSalary()).isEqualByComparingTo(BigDecimal.valueOf(300000));
            assertThat(captured.page()).isEqualTo(0);
            assertThat(captured.size()).isEqualTo(20);
            assertThat(captured.sort()).isEqualTo("newest");
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Search with no query parameters applies standard defaults")
        void searchWithDefaults_Success() throws Exception {
            JobSearchResponse emptyResponse = new JobSearchResponse(
                    Collections.emptyList(),
                    0,
                    20,
                    0L,
                    0,
                    false,
                    false
            );
            when(jobSearchService.search(any(JobSearchCriteria.class))).thenReturn(emptyResponse);

            mockMvc.perform(get("/api/v1/jobs")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobs", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements", is(0)));

            ArgumentCaptor<JobSearchCriteria> captor = ArgumentCaptor.forClass(JobSearchCriteria.class);
            verify(jobSearchService).search(captor.capture());
            JobSearchCriteria captured = captor.getValue();
            assertThat(captured.keyword()).isNull();
            assertThat(captured.page()).isEqualTo(0);
            assertThat(captured.size()).isEqualTo(20);
            assertThat(captured.sort()).isEqualTo("newest");
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Search with sort=relevance captures sort parameter accurately")
        void searchWithSortRelevance_CapturesSortParameter() throws Exception {
            JobSearchResponse emptyResponse = new JobSearchResponse(
                    Collections.emptyList(), 0, 20, 0L, 0, false, false
            );
            when(jobSearchService.search(any(JobSearchCriteria.class))).thenReturn(emptyResponse);

            mockMvc.perform(get("/api/v1/jobs")
                            .param("keyword", "Java")
                            .param("sort", "relevance")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            ArgumentCaptor<JobSearchCriteria> captor = ArgumentCaptor.forClass(JobSearchCriteria.class);
            verify(jobSearchService).search(captor.capture());
            JobSearchCriteria captured = captor.getValue();
            assertThat(captured.keyword()).isEqualTo("Java");
            assertThat(captured.sort()).isEqualTo("relevance");
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Search with sort=freshness captures sort parameter accurately")
        void searchWithSortFreshness_CapturesSortParameter() throws Exception {
            JobSearchResponse emptyResponse = new JobSearchResponse(
                    Collections.emptyList(), 0, 20, 0L, 0, false, false
            );
            when(jobSearchService.search(any(JobSearchCriteria.class))).thenReturn(emptyResponse);

            mockMvc.perform(get("/api/v1/jobs")
                            .param("keyword", "Java")
                            .param("sort", "freshness")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            ArgumentCaptor<JobSearchCriteria> captor = ArgumentCaptor.forClass(JobSearchCriteria.class);
            verify(jobSearchService).search(captor.capture());
            JobSearchCriteria captured = captor.getValue();
            assertThat(captured.keyword()).isEqualTo("Java");
            assertThat(captured.sort()).isEqualTo("freshness");
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Search returns all lifecycle states (ACTIVE, STALE, EXPIRED, UNKNOWN) without dropping records")
        void searchReturnsMixedLifecycleStates() throws Exception {
            JobResponse active = sampleJobResponse(UUID.randomUUID(), JobFreshnessStatus.ACTIVE);
            JobResponse stale = sampleJobResponse(UUID.randomUUID(), JobFreshnessStatus.STALE);
            JobResponse expired = sampleJobResponse(UUID.randomUUID(), JobFreshnessStatus.EXPIRED);
            JobResponse unknown = sampleJobResponse(UUID.randomUUID(), JobFreshnessStatus.UNKNOWN);

            JobSearchResponse response = new JobSearchResponse(
                    List.of(active, stale, expired, unknown),
                    0,
                    20,
                    4L,
                    1,
                    false,
                    false
            );
            when(jobSearchService.search(any(JobSearchCriteria.class))).thenReturn(response);

            mockMvc.perform(get("/api/v1/jobs")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobs", hasSize(4)))
                    .andExpect(jsonPath("$.jobs[0].freshnessStatus", is("ACTIVE")))
                    .andExpect(jsonPath("$.jobs[1].freshnessStatus", is("STALE")))
                    .andExpect(jsonPath("$.jobs[2].freshnessStatus", is("EXPIRED")))
                    .andExpect(jsonPath("$.jobs[3].freshnessStatus", is("UNKNOWN")));
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("SQL-like keyword input remains a normal search value and is safely handled")
        void searchWithSqlLikeKeyword_IsTreatedAsSafePlainText() throws Exception {
            JobSearchResponse emptyResponse = new JobSearchResponse(
                    Collections.emptyList(), 0, 20, 0L, 0, false, false
            );
            when(jobSearchService.search(any(JobSearchCriteria.class))).thenReturn(emptyResponse);

            String malicious = "'; DROP TABLE jobs; SELECT * FROM users WHERE '1'='1";
            mockMvc.perform(get("/api/v1/jobs")
                            .param("keyword", malicious)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobs", hasSize(0)));

            ArgumentCaptor<JobSearchCriteria> captor = ArgumentCaptor.forClass(JobSearchCriteria.class);
            verify(jobSearchService).search(captor.capture());
            assertThat(captor.getValue().keyword()).isEqualTo(malicious);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Query parameters are normalized: surrounding whitespace trimmed and aliases resolved")
        void searchWithAliasesAndWhitespaceTrimming_Success() throws Exception {
            JobSearchResponse emptyResponse = new JobSearchResponse(
                    Collections.emptyList(), 0, 20, 0L, 0, false, false
            );
            when(jobSearchService.search(any(JobSearchCriteria.class))).thenReturn(emptyResponse);

            mockMvc.perform(get("/api/v1/jobs")
                            .param("keyword", "   Senior Java Developer   ")
                            .param("location", "   Remote, US   ")
                            .param("minExperienceYears", "3")
                            .param("maxExperienceYears", "7")
                            .param("minSalary", "90000")
                            .param("maxSalary", "150000")
                            .param("sort", "COMPANY")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            ArgumentCaptor<JobSearchCriteria> captor = ArgumentCaptor.forClass(JobSearchCriteria.class);
            verify(jobSearchService).search(captor.capture());
            JobSearchCriteria captured = captor.getValue();
            assertThat(captured.keyword()).isEqualTo("Senior Java Developer");
            assertThat(captured.location()).isEqualTo("Remote, US");
            assertThat(captured.minExperienceYears()).isEqualTo(3);
            assertThat(captured.minimumExperience()).isEqualTo(3);
            assertThat(captured.maxExperienceYears()).isEqualTo(7);
            assertThat(captured.maximumExperience()).isEqualTo(7);
            assertThat(captured.minSalary()).isEqualByComparingTo("90000");
            assertThat(captured.minimumSalary()).isEqualByComparingTo("90000");
            assertThat(captured.maxSalary()).isEqualByComparingTo("150000");
            assertThat(captured.maximumSalary()).isEqualByComparingTo("150000");
            assertThat(captured.sort()).isEqualTo("company");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/jobs - Validation Errors")
    class SearchValidationErrorsTests {

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Invalid sort parameter triggers 400 Bad Request")
        void invalidSort_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("sort", "popularity")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Invalid sort parameter 'popularity'")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Arbitrary sort / SQL injection attempt triggers 400 Bad Request")
        void arbitrarySortInjection_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("sort", "'; DROP TABLE jobs; --")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Invalid sort parameter")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Negative page index triggers 400 Bad Request")
        void negativePage_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("page", "-1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Page index cannot be negative")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Zero page size triggers 400 Bad Request")
        void zeroPageSize_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("size", "0")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Page size must be between 1 and 100")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Negative page size triggers 400 Bad Request")
        void negativePageSize_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("size", "-5")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Page size must be between 1 and 100")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Page size greater than 100 triggers 400 Bad Request")
        void invalidPageSize_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("size", "500")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Page size must be between 1 and 100")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Page size of 101 triggers 400 Bad Request (boundary + 1)")
        void size101_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("size", "101")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Page size must be between 1 and 100")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Keyword > 200 characters triggers 400 Bad Request")
        void keywordTooLong_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("keyword", "a".repeat(201))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Keyword length cannot exceed 200 characters")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Location > 200 characters triggers 400 Bad Request")
        void locationTooLong_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("location", "b".repeat(201))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Location length cannot exceed 200 characters")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Negative min experience triggers 400 Bad Request")
        void negativeMinExperience_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("minExperienceYears", "-1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Minimum experience cannot be negative")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Negative max experience triggers 400 Bad Request")
        void negativeMaxExperience_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("maxExperienceYears", "-1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Maximum experience cannot be negative")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Invalid experience range triggers 400 Bad Request")
        void invalidExperienceRange_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("minimumExperience", "10")
                            .param("maximumExperience", "5")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Minimum experience (10) cannot exceed maximum experience (5)")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Negative min salary triggers 400 Bad Request")
        void negativeMinSalary_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("minSalary", "-1000")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Minimum salary cannot be negative")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Negative max salary triggers 400 Bad Request")
        void negativeMaxSalary_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("maxSalary", "-1000")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Maximum salary cannot be negative")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Invalid salary range triggers 400 Bad Request")
        void invalidSalaryRange_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("minSalary", "200000")
                            .param("maxSalary", "100000")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Minimum salary (200000) cannot exceed maximum salary (100000)")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Invalid date range (postedAfter > postedBefore) triggers 400 Bad Request")
        void invalidDateRange_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("postedAfter", "2026-09-15T12:00:00Z")
                            .param("postedBefore", "2026-09-10T12:00:00Z")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("cannot be after postedBefore")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Malformed date triggers 400 Bad Request via MethodArgumentTypeMismatch")
        void malformedDate_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("postedAfter", "invalid-timestamp")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Invalid value 'invalid-timestamp' for parameter 'postedAfter'")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Invalid workMode enum string triggers 400 Bad Request via MethodArgumentTypeMismatch")
        void invalidWorkModeEnum_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("workMode", "COFFEE_SHOP")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Invalid value 'COFFEE_SHOP' for parameter 'workMode'")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Invalid employmentType enum string triggers 400 Bad Request via MethodArgumentTypeMismatch")
        void invalidEmploymentTypeEnum_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("employmentType", "SUPER_CONTRACT")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Invalid value 'SUPER_CONTRACT' for parameter 'employmentType'")));

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Invalid source enum string triggers 400 Bad Request via MethodArgumentTypeMismatch")
        void invalidSourceEnum_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .param("source", "DARK_WEB")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", containsString("Invalid value 'DARK_WEB' for parameter 'source'")));

            verifyNoInteractions(jobSearchService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/jobs/{id} - Single Job Retrieval")
    class SingleJobRetrievalTests {

        @Test
        @DisplayName("Unauthenticated retrieval returns 401 Unauthorized")
        void getByIdUnauthenticated_Returns401() throws Exception {
            UUID id = UUID.randomUUID();
            mockMvc.perform(get("/api/v1/jobs/" + id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(jobSearchService);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Authenticated retrieval of existing job returns 200 OK and complete canonical JobResponse")
        void getByIdExisting_Returns200() throws Exception {
            UUID id = UUID.randomUUID();
            JobResponse job = sampleJobResponse(id, JobFreshnessStatus.ACTIVE);
            when(jobSearchService.findById(id)).thenReturn(job);

            mockMvc.perform(get("/api/v1/jobs/" + id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.externalJobId", is("ext-linkedin-101")))
                    .andExpect(jsonPath("$.source", is("LINKEDIN")))
                    .andExpect(jsonPath("$.title", is("Principal Architect")))
                    .andExpect(jsonPath("$.companyName", is("Enterprise Systems Inc")))
                    .andExpect(jsonPath("$.recruiterName", is("Alice Headhunter")))
                    .andExpect(jsonPath("$.description", is("Lead enterprise architecture transformation.")))
                    .andExpect(jsonPath("$.location", is("Austin, TX")))
                    .andExpect(jsonPath("$.workMode", is("REMOTE")))
                    .andExpect(jsonPath("$.employmentType", is("FULL_TIME")))
                    .andExpect(jsonPath("$.experienceMinYears", is(8)))
                    .andExpect(jsonPath("$.experienceMaxYears", is(15)))
                    .andExpect(jsonPath("$.salaryMin", is(190000)))
                    .andExpect(jsonPath("$.salaryMax", is(250000)))
                    .andExpect(jsonPath("$.salaryCurrency", is("USD")))
                    .andExpect(jsonPath("$.salaryPeriod", is("YEAR")))
                    .andExpect(jsonPath("$.jobUrl", is("https://linkedin.com/jobs/view/101")))
                    .andExpect(jsonPath("$.companyUrl", is("https://enterprisesystems.com")))
                    .andExpect(jsonPath("$.postedAt").exists())
                    .andExpect(jsonPath("$.expiresAt").exists())
                    .andExpect(jsonPath("$.discoveredAt").exists())
                    .andExpect(jsonPath("$.lastSeenAt").exists())
                    .andExpect(jsonPath("$.applicationMethod", is("ATS")))
                    .andExpect(jsonPath("$.freshnessStatus", is("ACTIVE")))
                    // Confirm internal persistence/Hibernate details are NOT present
                    .andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist())
                    .andExpect(jsonPath("$.$$_hibernate").doesNotExist())
                    .andExpect(jsonPath("$.handler").doesNotExist())
                    .andExpect(jsonPath("$.ingestionRunId").doesNotExist())
                    .andExpect(jsonPath("$.providerToken").doesNotExist())
                    .andExpect(jsonPath("$.credentials").doesNotExist());
        }

        @ParameterizedTest
        @EnumSource(JobFreshnessStatus.class)
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Serialization correctly outputs each JobFreshnessStatus value in job response")
        void freshnessStatusSerializedCorrectly(JobFreshnessStatus status) throws Exception {
            UUID id = UUID.randomUUID();
            JobResponse job = sampleJobResponse(id, status);
            when(jobSearchService.findById(id)).thenReturn(job);

            mockMvc.perform(get("/api/v1/jobs/" + id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id.toString())))
                    .andExpect(jsonPath("$.freshnessStatus", is(status.name())));
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Sparse job detail retrieval preserves nulls without fabricated defaults")
        void getByIdSparseJob_PreservesNullsWithoutFabrication() throws Exception {
            UUID sparseId = UUID.randomUUID();
            Instant now = Instant.now();
            JobResponse sparseJob = new JobResponse(
                    sparseId,
                    JobSource.OTHER,
                    "ext-sparse-99",
                    "Junior Engineer",
                    "New Company",
                    null, null, null,
                    JobWorkMode.UNKNOWN,
                    JobEmploymentType.UNKNOWN,
                    null, null, null, null, null, null, null, null, null, null,
                    now, now,
                    JobApplicationMethod.UNKNOWN,
                    now, now,
                    JobFreshnessStatus.UNKNOWN
            );
            when(jobSearchService.findById(sparseId)).thenReturn(sparseJob);

            mockMvc.perform(get("/api/v1/jobs/" + sparseId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(sparseId.toString())))
                    .andExpect(jsonPath("$.title", is("Junior Engineer")))
                    .andExpect(jsonPath("$.companyName", is("New Company")))
                    .andExpect(jsonPath("$.recruiterName").doesNotExist())
                    .andExpect(jsonPath("$.description").doesNotExist())
                    .andExpect(jsonPath("$.location").doesNotExist())
                    .andExpect(jsonPath("$.experienceMinYears").doesNotExist())
                    .andExpect(jsonPath("$.experienceMaxYears").doesNotExist())
                    .andExpect(jsonPath("$.salaryMin").doesNotExist())
                    .andExpect(jsonPath("$.salaryMax").doesNotExist())
                    .andExpect(jsonPath("$.salaryCurrency").doesNotExist())
                    .andExpect(jsonPath("$.salaryPeriod").doesNotExist())
                    .andExpect(jsonPath("$.jobUrl").doesNotExist())
                    .andExpect(jsonPath("$.companyUrl").doesNotExist())
                    .andExpect(jsonPath("$.postedAt").doesNotExist())
                    .andExpect(jsonPath("$.expiresAt").doesNotExist())
                    .andExpect(jsonPath("$.freshnessStatus", is("UNKNOWN")));
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Authenticated retrieval of non-existent job returns 404 Not Found")
        void getByIdNotFound_Returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(jobSearchService.findById(id)).thenThrow(new JobNotFoundException(id));

            mockMvc.perform(get("/api/v1/jobs/" + id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.error", is("Not Found")))
                    .andExpect(jsonPath("$.message", containsString("Job not found with id: " + id)))
                    .andExpect(jsonPath("$.path", is("/api/v1/jobs/" + id)));
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Malformed UUID path variable returns 400 Bad Request with standardized error")
        void getByIdMalformedUuid_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/jobs/not-a-valid-uuid")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.error", is("Bad Request")))
                    .andExpect(jsonPath("$.message", containsString("Invalid value 'not-a-valid-uuid' for parameter 'jobId'")))
                    .andExpect(jsonPath("$.path", is("/api/v1/jobs/not-a-valid-uuid")));

            verifyNoInteractions(jobSearchService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/jobs - Canonical Read Representation & Decoupling")
    class CanonicalReadRepresentationTests {

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Endpoint returns canonical read model with all normalized job fields and camelCase naming")
        void searchReturnsCanonicalReadModelFields() throws Exception {
            UUID jobId = UUID.randomUUID();
            JobResponse fullJob = sampleJobResponse(jobId, JobFreshnessStatus.ACTIVE);
            JobSearchResponse response = new JobSearchResponse(List.of(fullJob), 0, 20, 1L, 1, false, false);
            when(jobSearchService.search(any(JobSearchCriteria.class))).thenReturn(response);

            mockMvc.perform(get("/api/v1/jobs")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobs[0].id", is(jobId.toString())))
                    .andExpect(jsonPath("$.jobs[0].externalJobId", is("ext-linkedin-101")))
                    .andExpect(jsonPath("$.jobs[0].source", is("LINKEDIN")))
                    .andExpect(jsonPath("$.jobs[0].title", is("Principal Architect")))
                    .andExpect(jsonPath("$.jobs[0].companyName", is("Enterprise Systems Inc")))
                    .andExpect(jsonPath("$.jobs[0].recruiterName", is("Alice Headhunter")))
                    .andExpect(jsonPath("$.jobs[0].description", is("Lead enterprise architecture transformation.")))
                    .andExpect(jsonPath("$.jobs[0].location", is("Austin, TX")))
                    .andExpect(jsonPath("$.jobs[0].workMode", is("REMOTE")))
                    .andExpect(jsonPath("$.jobs[0].employmentType", is("FULL_TIME")))
                    .andExpect(jsonPath("$.jobs[0].experienceMinYears", is(8)))
                    .andExpect(jsonPath("$.jobs[0].experienceMaxYears", is(15)))
                    .andExpect(jsonPath("$.jobs[0].salaryMin", is(190000)))
                    .andExpect(jsonPath("$.jobs[0].salaryMax", is(250000)))
                    .andExpect(jsonPath("$.jobs[0].salaryCurrency", is("USD")))
                    .andExpect(jsonPath("$.jobs[0].salaryPeriod", is("YEAR")))
                    .andExpect(jsonPath("$.jobs[0].jobUrl", is("https://linkedin.com/jobs/view/101")))
                    .andExpect(jsonPath("$.jobs[0].companyUrl", is("https://enterprisesystems.com")))
                    .andExpect(jsonPath("$.jobs[0].postedAt").exists())
                    .andExpect(jsonPath("$.jobs[0].expiresAt").exists())
                    .andExpect(jsonPath("$.jobs[0].discoveredAt").exists())
                    .andExpect(jsonPath("$.jobs[0].lastSeenAt").exists())
                    .andExpect(jsonPath("$.jobs[0].applicationMethod", is("ATS")))
                    .andExpect(jsonPath("$.jobs[0].freshnessStatus", is("ACTIVE")))
                    // Confirm internal persistence/Hibernate details are NOT present
                    .andExpect(jsonPath("$.jobs[0].hibernateLazyInitializer").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].$$_hibernate").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].handler").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].ingestionRunId").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].providerToken").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].credentials").doesNotExist());
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("Sparse job with null optional fields serializes nulls without fabricated defaults")
        void searchSparseJob_PreservesNullsWithoutFabrication() throws Exception {
            UUID sparseId = UUID.randomUUID();
            Instant now = Instant.now();
            JobResponse sparseJob = new JobResponse(
                    sparseId,
                    JobSource.OTHER,
                    "cust-sparse-404",
                    "Junior Engineer",
                    "New Company",
                    null, // recruiterName
                    null, // description
                    null, // location
                    JobWorkMode.UNKNOWN,
                    JobEmploymentType.UNKNOWN,
                    null, // experienceMinYears
                    null, // experienceMaxYears
                    null, // salaryMin
                    null, // salaryMax
                    null, // salaryCurrency
                    null, // salaryPeriod
                    null, // jobUrl
                    null, // companyUrl
                    null, // postedAt
                    null, // expiresAt
                    now,
                    now,
                    JobApplicationMethod.UNKNOWN,
                    now,
                    now,
                    JobFreshnessStatus.UNKNOWN
            );
            JobSearchResponse response = new JobSearchResponse(List.of(sparseJob), 0, 20, 1L, 1, false, false);
            when(jobSearchService.search(any(JobSearchCriteria.class))).thenReturn(response);

            mockMvc.perform(get("/api/v1/jobs")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobs[0].recruiterName").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].description").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].location").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].experienceMinYears").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].experienceMaxYears").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].salaryMin").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].salaryMax").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].salaryCurrency").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].salaryPeriod").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].jobUrl").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].companyUrl").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].postedAt").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].expiresAt").doesNotExist())
                    .andExpect(jsonPath("$.jobs[0].freshnessStatus", is("UNKNOWN")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/jobs - Source Provenance API Integrity (Requirements 21 - 29)")
    class SourceProvenanceApiTests {

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("21. GET /api/v1/jobs returns source provenance correctly")
        void getJobs_ReturnsSourceProvenanceCorrectly() throws Exception {
            UUID id = UUID.randomUUID();
            JobResponse sample = sampleJobResponse(id, JobFreshnessStatus.ACTIVE);
            JobSearchResponse response = new JobSearchResponse(List.of(sample), 0, 20, 1L, 1, false, false);
            when(jobSearchService.search(any(JobSearchCriteria.class))).thenReturn(response);

            mockMvc.perform(get("/api/v1/jobs")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobs[0].source", is("LINKEDIN")))
                    .andExpect(jsonPath("$.jobs[0].externalJobId", is("ext-linkedin-101")))
                    .andExpect(jsonPath("$.jobs[0].jobUrl", is("https://linkedin.com/jobs/view/101")))
                    .andExpect(jsonPath("$.jobs[0].companyUrl", is("https://enterprisesystems.com")))
                    .andExpect(jsonPath("$.jobs[0].discoveredAt").exists())
                    .andExpect(jsonPath("$.jobs[0].lastSeenAt").exists())
                    .andExpect(jsonPath("$.jobs[0].postedAt").exists())
                    .andExpect(jsonPath("$.jobs[0].expiresAt").exists())
                    .andExpect(jsonPath("$.jobs[0].applicationMethod", is("ATS")));
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("22. GET /api/v1/jobs/{jobId} returns source provenance correctly")
        void getJobById_ReturnsSourceProvenanceCorrectly() throws Exception {
            UUID id = UUID.randomUUID();
            JobResponse sample = sampleJobResponse(id, JobFreshnessStatus.ACTIVE);
            when(jobSearchService.findById(id)).thenReturn(sample);

            mockMvc.perform(get("/api/v1/jobs/" + id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.source", is("LINKEDIN")))
                    .andExpect(jsonPath("$.externalJobId", is("ext-linkedin-101")))
                    .andExpect(jsonPath("$.jobUrl", is("https://linkedin.com/jobs/view/101")))
                    .andExpect(jsonPath("$.companyUrl", is("https://enterprisesystems.com")))
                    .andExpect(jsonPath("$.discoveredAt").exists())
                    .andExpect(jsonPath("$.lastSeenAt").exists())
                    .andExpect(jsonPath("$.postedAt").exists())
                    .andExpect(jsonPath("$.expiresAt").exists())
                    .andExpect(jsonPath("$.applicationMethod", is("ATS")));
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("23. API does not expose persistence implementation details")
        void apiDoesNotExposePersistenceImplementationDetails() throws Exception {
            UUID id = UUID.randomUUID();
            JobResponse sample = sampleJobResponse(id, JobFreshnessStatus.ACTIVE);
            when(jobSearchService.findById(id)).thenReturn(sample);

            mockMvc.perform(get("/api/v1/jobs/" + id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist())
                    .andExpect(jsonPath("$.$$_hibernate").doesNotExist())
                    .andExpect(jsonPath("$.handler").doesNotExist())
                    .andExpect(jsonPath("$.entityState").doesNotExist());
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("24. API does not expose credentials or source adapter internals")
        void apiDoesNotExposeCredentialsOrAdapterInternals() throws Exception {
            UUID id = UUID.randomUUID();
            JobResponse sample = sampleJobResponse(id, JobFreshnessStatus.ACTIVE);
            when(jobSearchService.findById(id)).thenReturn(sample);

            mockMvc.perform(get("/api/v1/jobs/" + id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.credentials").doesNotExist())
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.token").doesNotExist())
                    .andExpect(jsonPath("$.accessToken").doesNotExist())
                    .andExpect(jsonPath("$.oauthToken").doesNotExist())
                    .andExpect(jsonPath("$.cookies").doesNotExist())
                    .andExpect(jsonPath("$.authorizationHeader").doesNotExist())
                    .andExpect(jsonPath("$.adapterClass").doesNotExist());
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("25. Existing pagination remains unchanged")
        void existingPaginationRemainsUnchanged() throws Exception {
            JobSearchResponse response = new JobSearchResponse(List.of(), 2, 10, 25L, 3, true, true);
            when(jobSearchService.search(any(JobSearchCriteria.class))).thenReturn(response);

            mockMvc.perform(get("/api/v1/jobs?page=2&size=10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page", is(2)))
                    .andExpect(jsonPath("$.size", is(10)))
                    .andExpect(jsonPath("$.totalElements", is(25)))
                    .andExpect(jsonPath("$.totalPages", is(3)))
                    .andExpect(jsonPath("$.hasNext", is(true)))
                    .andExpect(jsonPath("$.hasPrevious", is(true)));
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("26. Existing filters remain unchanged")
        void existingFiltersRemainUnchanged() throws Exception {
            JobSearchResponse response = new JobSearchResponse(List.of(), 0, 20, 0L, 0, false, false);
            when(jobSearchService.search(any(JobSearchCriteria.class))).thenReturn(response);

            mockMvc.perform(get("/api/v1/jobs")
                            .param("keyword", "Kubernetes")
                            .param("location", "Berlin")
                            .param("source", "LINKEDIN")
                            .param("workMode", "REMOTE")
                            .param("employmentType", "FULL_TIME")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            ArgumentCaptor<JobSearchCriteria> captor = ArgumentCaptor.forClass(JobSearchCriteria.class);
            verify(jobSearchService).search(captor.capture());
            JobSearchCriteria captured = captor.getValue();
            assertThat(captured.keyword()).isEqualTo("Kubernetes");
            assertThat(captured.location()).isEqualTo("Berlin");
            assertThat(captured.source()).isEqualTo(JobSource.LINKEDIN);
            assertThat(captured.workMode()).isEqualTo(JobWorkMode.REMOTE);
            assertThat(captured.employmentType()).isEqualTo(JobEmploymentType.FULL_TIME);
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("27. Existing sorting remains unchanged")
        void existingSortingRemainsUnchanged() throws Exception {
            JobSearchResponse response = new JobSearchResponse(List.of(), 0, 20, 0L, 0, false, false);
            when(jobSearchService.search(any(JobSearchCriteria.class))).thenReturn(response);

            mockMvc.perform(get("/api/v1/jobs?sort=company")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            ArgumentCaptor<JobSearchCriteria> captor = ArgumentCaptor.forClass(JobSearchCriteria.class);
            verify(jobSearchService).search(captor.capture());
            assertThat(captor.getValue().sort()).isEqualTo("company");
        }

        @Test
        @WithMockUser(username = "candidate@joblivo.com")
        @DisplayName("28. freshnessStatus remains unchanged")
        void freshnessStatusRemainsUnchanged() throws Exception {
            UUID id = UUID.randomUUID();
            JobResponse sample = sampleJobResponse(id, JobFreshnessStatus.EXPIRED);
            when(jobSearchService.findById(id)).thenReturn(sample);

            mockMvc.perform(get("/api/v1/jobs/" + id)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.freshnessStatus", is("EXPIRED")));
        }

        @Test
        @WithAnonymousUser
        @DisplayName("29. Authentication remains unchanged: unauthenticated requests return 401 Unauthorized")
        void authenticationRemainsUnchanged() throws Exception {
            mockMvc.perform(get("/api/v1/jobs")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/v1/jobs/" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }
}
