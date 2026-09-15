package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.model.JobApplicationMethod;
import com.joblivo.job.model.JobEmploymentType;
import com.joblivo.job.model.JobSource;
import com.joblivo.job.model.JobWorkMode;
import com.joblivo.job.model.SalaryPeriod;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("JobSearchRelevanceOrder Unit Tests")
class JobSearchRelevanceOrderTest {

    private static Job createJob(
            UUID id,
            String title,
            String companyName,
            String location,
            String description,
            Instant postedAt
    ) {
        try {
            Job job = new Job(JobSource.LINKEDIN, "ext-" + id, title, companyName);
            job.setLocation(location);
            job.setDescription(description);
            job.setPostedAt(postedAt);
            job.setWorkMode(JobWorkMode.REMOTE);
            job.setEmploymentType(JobEmploymentType.FULL_TIME);
            job.setApplicationMethod(JobApplicationMethod.EXTERNAL_COMPANY_SITE);
            job.setSalaryMin(BigDecimal.valueOf(100000));
            job.setSalaryMax(BigDecimal.valueOf(150000));
            job.setSalaryCurrency("USD");
            job.setSalaryPeriod(SalaryPeriod.YEAR);

            Field idField = Job.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(job, id);

            return job;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("1-7 Signal Precedence Rules")
    class SignalPrecedenceTests {

        private final Instant fixedTime = Instant.parse("2026-09-01T10:00:00Z");

        @Test
        @DisplayName("1. Exact title match ranks first (above starts-with, contains, and company)")
        void exactTitleMatchRanksFirst() {
            String keyword = "Java Developer";

            Job exactTitle = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Java Developer", "Acme Inc", "Austin, TX", "Write code.", fixedTime);
            Job startsWithTitle = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Java Developer II", "Acme Inc", "Austin, TX", "Write code.", fixedTime);
            Job containsTitle = createJob(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                    "Senior Java Developer", "Acme Inc", "Austin, TX", "Write code.", fixedTime);
            Job exactCompany = createJob(UUID.fromString("00000000-0000-0000-0000-000000000004"),
                    "Software Engineer", "Java Developer", "Austin, TX", "Write code.", fixedTime);

            List<Job> jobs = new ArrayList<>(List.of(startsWithTitle, exactCompany, exactTitle, containsTitle));
            jobs.sort(JobSearchRelevanceOrder.comparator(keyword));

            assertThat(jobs.get(0)).isSameAs(exactTitle);
            assertThat(JobSearchRelevanceOrder.evaluateRank(exactTitle, JobSearchRelevanceOrder.normalizeKeyword(keyword)))
                    .isEqualTo(JobSearchRelevanceOrder.RANK_EXACT_TITLE);
        }

        @Test
        @DisplayName("2. Title starting with keyword ranks above title containing keyword")
        void titleStartsWithRanksAboveTitleContains() {
            String keyword = "Java";

            Job startsWith = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Java Backend Engineer", "Acme Corp", "Austin, TX", "Backend APIs", fixedTime);
            Job contains = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Senior Java Engineer", "Acme Corp", "Austin, TX", "Backend APIs", fixedTime);

            List<Job> jobs = new ArrayList<>(List.of(contains, startsWith));
            jobs.sort(JobSearchRelevanceOrder.comparator(keyword));

            assertThat(jobs.get(0)).isSameAs(startsWith);
            assertThat(jobs.get(1)).isSameAs(contains);
        }

        @Test
        @DisplayName("3. Title containing keyword ranks above company-only matches")
        void titleContainsRanksAboveCompanyMatches() {
            String keyword = "Java";

            Job titleContains = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Cloud Java Specialist", "Alpha Corp", "Chicago, IL", "Microservices", fixedTime);
            Job exactCompany = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Cloud Specialist", "Java", "Chicago, IL", "Microservices", fixedTime);
            Job companyContains = createJob(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                    "Cloud Specialist", "Java Technologies LLC", "Chicago, IL", "Microservices", fixedTime);

            List<Job> jobs = new ArrayList<>(List.of(companyContains, exactCompany, titleContains));
            jobs.sort(JobSearchRelevanceOrder.comparator(keyword));

            assertThat(jobs.get(0)).isSameAs(titleContains);
        }

        @Test
        @DisplayName("4. Exact company match ranks above company partial match")
        void exactCompanyRanksAboveCompanyPartial() {
            String keyword = "Netflix";

            Job exactCompany = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Site Reliability Engineer", "Netflix", "Los Gatos, CA", "Infrastructure", fixedTime);
            Job partialCompany = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Site Reliability Engineer", "Netflix Studio Services", "Los Gatos, CA", "Infrastructure", fixedTime);

            List<Job> jobs = new ArrayList<>(List.of(partialCompany, exactCompany));
            jobs.sort(JobSearchRelevanceOrder.comparator(keyword));

            assertThat(jobs.get(0)).isSameAs(exactCompany);
            assertThat(jobs.get(1)).isSameAs(partialCompany);
        }

        @Test
        @DisplayName("5. Company match ranks above location-only match")
        void companyMatchRanksAboveLocationMatch() {
            String keyword = "Boston";

            Job companyMatch = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Full Stack Developer", "Boston Dynamics", "Waltham, MA", "Robotics software", fixedTime);
            Job locationMatch = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Full Stack Developer", "RoboCorp", "Boston, MA", "Robotics software", fixedTime);

            List<Job> jobs = new ArrayList<>(List.of(locationMatch, companyMatch));
            jobs.sort(JobSearchRelevanceOrder.comparator(keyword));

            assertThat(jobs.get(0)).isSameAs(companyMatch);
            assertThat(jobs.get(1)).isSameAs(locationMatch);
        }

        @Test
        @DisplayName("6. Location-only match ranks above description-only match")
        void locationMatchRanksAboveDescriptionMatch() {
            String keyword = "Austin";

            Job locationMatch = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "DevOps Engineer", "CloudTech", "Austin, TX", "Build CI/CD pipelines.", fixedTime);
            Job descriptionMatch = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "DevOps Engineer", "CloudTech", "Remote", "Our main office is in Austin, Texas.", fixedTime);

            List<Job> jobs = new ArrayList<>(List.of(descriptionMatch, locationMatch));
            jobs.sort(JobSearchRelevanceOrder.comparator(keyword));

            assertThat(jobs.get(0)).isSameAs(locationMatch);
            assertThat(jobs.get(1)).isSameAs(descriptionMatch);
        }

        @Test
        @DisplayName("7. Non-matching jobs rank after matching jobs when sort=RELEVANCE")
        void nonMatchingJobsRankAfterMatchingJobs() {
            String keyword = "Python";

            Job matchDescription = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Software Engineer", "Acme", "New York, NY", "Scripting in Python required.", fixedTime);
            Job nonMatch = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Accountant", "FinanceCorp", "Dallas, TX", "Ledger reconciliation.", fixedTime);

            List<Job> jobs = new ArrayList<>(List.of(nonMatch, matchDescription));
            jobs.sort(JobSearchRelevanceOrder.comparator(keyword));

            assertThat(jobs.get(0)).isSameAs(matchDescription);
            assertThat(jobs.get(1)).isSameAs(nonMatch);
            assertThat(JobSearchRelevanceOrder.evaluateRank(nonMatch, JobSearchRelevanceOrder.normalizeKeyword(keyword)))
                    .isEqualTo(JobSearchRelevanceOrder.RANK_NON_MATCHING);
        }
    }

    @Nested
    @DisplayName("Normalization & Case/Whitespace Rules")
    class NormalizationTests {

        private final Instant fixedTime = Instant.parse("2026-09-01T10:00:00Z");

        @Test
        @DisplayName("8. Case differences do not change relevance ordering")
        void caseDifferencesDoNotChangeOrdering() {
            Job jobA = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Senior Java Developer", "Acme", "Remote", "Core Java.", fixedTime);
            Job jobB = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Lead Java Architect", "Acme", "Remote", "Architecture.", fixedTime);

            List<Job> orderLower = new ArrayList<>(List.of(jobB, jobA));
            orderLower.sort(JobSearchRelevanceOrder.comparator("senior java developer"));

            List<Job> orderUpper = new ArrayList<>(List.of(jobB, jobA));
            orderUpper.sort(JobSearchRelevanceOrder.comparator("SENIOR JAVA DEVELOPER"));

            List<Job> orderMixed = new ArrayList<>(List.of(jobB, jobA));
            orderMixed.sort(JobSearchRelevanceOrder.comparator("SeNiOr JaVa DeVeLoPeR"));

            assertThat(orderLower.get(0)).isSameAs(jobA);
            assertThat(orderUpper.get(0)).isSameAs(jobA);
            assertThat(orderMixed.get(0)).isSameAs(jobA);
        }

        @Test
        @DisplayName("9. Leading/trailing whitespace in keyword does not change relevance ordering")
        void leadingTrailingWhitespaceInKeyword() {
            Job jobA = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Java Engineer", "Acme", "Remote", "Dev", fixedTime);
            Job jobB = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Frontend Dev", "Java Corp", "Remote", "Dev", fixedTime);

            List<Job> list1 = new ArrayList<>(List.of(jobB, jobA));
            list1.sort(JobSearchRelevanceOrder.comparator("   Java Engineer   "));

            List<Job> list2 = new ArrayList<>(List.of(jobB, jobA));
            list2.sort(JobSearchRelevanceOrder.comparator("Java Engineer"));

            assertThat(list1.get(0)).isSameAs(jobA);
            assertThat(list2.get(0)).isSameAs(jobA);
        }

        @Test
        @DisplayName("10. Repeated whitespace is handled consistently with existing normalization behavior")
        void repeatedWhitespaceHandledConsistently() {
            Job exact = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Senior   Java    Developer", "Acme", "Remote", "Dev", fixedTime);
            Job other = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Cloud Architect", "Acme", "Remote", "Senior Java Developer role", fixedTime);

            List<Job> list = new ArrayList<>(List.of(other, exact));
            list.sort(JobSearchRelevanceOrder.comparator("Senior Java Developer"));

            assertThat(list.get(0)).isSameAs(exact);
            assertThat(JobSearchRelevanceOrder.evaluateRank(exact, JobSearchRelevanceOrder.normalizeKeyword("Senior   Java   Developer")))
                    .isEqualTo(JobSearchRelevanceOrder.RANK_EXACT_TITLE);
        }
    }

    @Nested
    @DisplayName("Deterministic Tie-Breaking Rules")
    class TieBreakerTests {

        private final Instant t1 = Instant.parse("2026-09-10T12:00:00Z");
        private final Instant t2 = Instant.parse("2026-09-05T12:00:00Z");

        @Test
        @DisplayName("14. Equal relevance uses postedAt DESC tie-breaker")
        void equalRelevanceUsesPostedAtDesc() {
            String keyword = "Backend";

            Job older = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Senior Backend Engineer", "Acme", "Remote", "Desc", t2);
            Job newer = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Senior Backend Engineer", "Acme", "Remote", "Desc", t1);

            List<Job> jobs = new ArrayList<>(List.of(older, newer));
            jobs.sort(JobSearchRelevanceOrder.comparator(keyword));

            assertThat(jobs.get(0)).isSameAs(newer);
            assertThat(jobs.get(1)).isSameAs(older);
        }

        @Test
        @DisplayName("15. postedAt null handling is deterministic (nulls placed consistently last)")
        void postedAtNullHandlingIsDeterministic() {
            String keyword = "Backend";

            Job withDate = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Backend Engineer", "Acme", "Remote", "Desc", t2);
            Job withNullDate = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Backend Engineer", "Acme", "Remote", "Desc", null);

            List<Job> jobs = new ArrayList<>(List.of(withNullDate, withDate));
            jobs.sort(JobSearchRelevanceOrder.comparator(keyword));

            assertThat(jobs.get(0)).isSameAs(withDate);
            assertThat(jobs.get(1)).isSameAs(withNullDate);
        }

        @Test
        @DisplayName("14b. Equal relevance and equal postedAt breaks ties on companyName ASC")
        void equalRelevanceBreaksTiesOnCompanyNameAsc() {
            String keyword = "Backend";

            Job companyZ = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Backend Engineer", "Zeta Systems", "Remote", "Desc", t1);
            Job companyA = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Backend Engineer", "Alpha Technologies", "Remote", "Desc", t1);

            List<Job> jobs = new ArrayList<>(List.of(companyZ, companyA));
            jobs.sort(JobSearchRelevanceOrder.comparator(keyword));

            assertThat(jobs.get(0)).isSameAs(companyA);
            assertThat(jobs.get(1)).isSameAs(companyZ);
        }

        @Test
        @DisplayName("14c. Equal relevance, postedAt, and companyName breaks ties on title ASC")
        void equalRelevanceBreaksTiesOnTitleAsc() {
            String keyword = "Engineer";

            Job titleZ = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Staff Software Engineer", "Acme", "Remote", "Desc", t1);
            Job titleA = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Lead Software Engineer", "Acme", "Remote", "Desc", t1);

            List<Job> jobs = new ArrayList<>(List.of(titleZ, titleA));
            jobs.sort(JobSearchRelevanceOrder.comparator(keyword));

            assertThat(jobs.get(0)).isSameAs(titleA);
            assertThat(jobs.get(1)).isSameAs(titleZ);
        }

        @Test
        @DisplayName("14d. Equal relevance, postedAt, companyName, and title breaks ties on id ASC")
        void equalRelevanceBreaksTiesOnIdAsc() {
            String keyword = "Engineer";

            UUID id1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
            UUID id2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

            Job job2 = createJob(id2, "Software Engineer", "Acme", "Remote", "Desc", t1);
            Job job1 = createJob(id1, "Software Engineer", "Acme", "Remote", "Desc", t1);

            List<Job> jobs = new ArrayList<>(List.of(job2, job1));
            jobs.sort(JobSearchRelevanceOrder.comparator(keyword));

            assertThat(jobs.get(0)).isSameAs(job1);
            assertThat(jobs.get(1)).isSameAs(job2);
        }
    }

    @Nested
    @DisplayName("JPA Criteria Query Ordering")
    class JpaCriteriaOrderingTests {

        @Test
        @DisplayName("applyRelevanceOrder configures CASE WHEN expression and 6 orders on CriteriaQuery")
        @SuppressWarnings("unchecked")
        void applyRelevanceOrderConfiguresOrders() {
            CriteriaBuilder cb = mock(CriteriaBuilder.class);
            CriteriaQuery<Job> query = mock(CriteriaQuery.class);
            Root<Job> root = mock(Root.class);

            Path<String> titlePath = mock(Path.class);
            Path<String> companyPath = mock(Path.class);
            Path<String> locationPath = mock(Path.class);
            Path<String> descPath = mock(Path.class);
            Path<Instant> postedAtPath = mock(Path.class);
            Path<UUID> idPath = mock(Path.class);

            doReturn(titlePath).when(root).<String>get("title");
            doReturn(companyPath).when(root).<String>get("companyName");
            doReturn(locationPath).when(root).<String>get("location");
            doReturn(descPath).when(root).<String>get("description");
            doReturn(postedAtPath).when(root).<Instant>get("postedAt");
            doReturn(idPath).when(root).<UUID>get("id");

            Predicate dummyPredicate = mock(Predicate.class);
            Expression dummyLower = mock(Expression.class);
            doReturn(dummyLower).when(cb).lower(any());
            doReturn(dummyPredicate).when(cb).equal(any(), anyString());
            doReturn(dummyPredicate).when(cb).like(any(), anyString());
            doReturn(dummyPredicate).when(cb).isNull(any());

            CriteriaBuilder.Case<Integer> caseClause = mock(CriteriaBuilder.Case.class);
            doReturn(caseClause).when(cb).<Integer>selectCase();
            doReturn(caseClause).when(caseClause).when(any(Predicate.class), anyInt());
            Expression<Integer> dummyRank = mock(Expression.class);
            doReturn(dummyRank).when(caseClause).otherwise(anyInt());

            Order dummyOrder = mock(Order.class);
            doReturn(dummyOrder).when(cb).asc(any());
            doReturn(dummyOrder).when(cb).desc(any());

            JobSearchRelevanceOrder.applyRelevanceOrder(cb, query, root, "Java");

            ArgumentCaptor<List<Order>> ordersCaptor = ArgumentCaptor.forClass(List.class);
            verify(query).orderBy(ordersCaptor.capture());

            List<Order> capturedOrders = ordersCaptor.getValue();
            assertThat(capturedOrders).hasSize(6);
        }

        @Test
        @DisplayName("applyRelevanceOrder safely handles null arguments without throwing")
        void applyRelevanceOrderSafeWithNulls() {
            CriteriaBuilder cb = mock(CriteriaBuilder.class);
            CriteriaQuery<Job> query = mock(CriteriaQuery.class);
            Root<Job> root = mock(Root.class);

            // Null cb, query, root, or keyword
            JobSearchRelevanceOrder.applyRelevanceOrder(null, query, root, "keyword");
            JobSearchRelevanceOrder.applyRelevanceOrder(cb, null, root, "keyword");
            JobSearchRelevanceOrder.applyRelevanceOrder(cb, query, null, "keyword");
            JobSearchRelevanceOrder.applyRelevanceOrder(cb, query, root, null);
            JobSearchRelevanceOrder.applyRelevanceOrder(cb, query, root, "   ");
        }
    }

    @Nested
    @DisplayName("Pagination Across Relevance Ranks")
    class PaginationTests {

        private final Instant fixedTime = Instant.parse("2026-09-01T10:00:00Z");

        @Test
        @DisplayName("13. Pagination occurs after full relevance ordering")
        void paginationOccursAfterRelevanceOrdering() {
            String keyword = "Architect";

            Job exact = createJob(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Architect", "Acme", "Remote", "Desc", fixedTime);
            Job startsWith = createJob(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "Architect Specialist", "Acme", "Remote", "Desc", fixedTime);
            Job contains = createJob(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                    "Enterprise Architect", "Acme", "Remote", "Desc", fixedTime);
            Job company = createJob(UUID.fromString("00000000-0000-0000-0000-000000000004"),
                    "Senior Developer", "Architect Solutions", "Remote", "Desc", fixedTime);
            Job desc = createJob(UUID.fromString("00000000-0000-0000-0000-000000000005"),
                    "Senior Developer", "Acme", "Remote", "Reports to chief Architect.", fixedTime);

            List<Job> allJobs = new ArrayList<>(List.of(desc, contains, exact, company, startsWith));
            allJobs.sort(JobSearchRelevanceOrder.comparator(keyword));

            // Page 0 (size 3)
            List<Job> page0 = allJobs.subList(0, 3);
            assertThat(page0).containsExactly(exact, startsWith, contains);

            // Page 1 (size 2)
            List<Job> page1 = allJobs.subList(3, 5);
            assertThat(page1).containsExactly(company, desc);
        }
    }
}
