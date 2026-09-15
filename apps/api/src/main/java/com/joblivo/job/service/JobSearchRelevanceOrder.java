package com.joblivo.job.service;

import com.joblivo.job.Job;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Encapsulates transparent, deterministic relevance ordering rules for Job Discovery search results.
 * <p>
 * Evaluates 7 fixed precedence signals against a normalized keyword query:
 * <ol>
 *   <li>Exact normalized title match (rank 1)</li>
 *   <li>Title starts with normalized keyword (rank 2)</li>
 *   <li>Title contains normalized keyword (rank 3)</li>
 *   <li>Exact normalized company-name match (rank 4)</li>
 *   <li>Company name contains normalized keyword (rank 5)</li>
 *   <li>Location contains normalized keyword (rank 6)</li>
 *   <li>Description contains normalized keyword (rank 7)</li>
 *   <li>Non-matching fallback (rank 8)</li>
 * </ol>
 * Followed by canonical, deterministic tie-breakers:
 * <ol>
 *   <li>Relevance rank ASC (1 before 2, etc.)</li>
 *   <li>{@code postedAt} DESC, placing null timestamps consistently last</li>
 *   <li>{@code companyName} ASC</li>
 *   <li>{@code title} ASC</li>
 *   <li>{@code id} ASC</li>
 * </ol>
 */
public final class JobSearchRelevanceOrder {

    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    public static final int RANK_EXACT_TITLE = 1;
    public static final int RANK_TITLE_STARTS_WITH = 2;
    public static final int RANK_TITLE_CONTAINS = 3;
    public static final int RANK_EXACT_COMPANY = 4;
    public static final int RANK_COMPANY_CONTAINS = 5;
    public static final int RANK_LOCATION_CONTAINS = 6;
    public static final int RANK_DESCRIPTION_CONTAINS = 7;
    public static final int RANK_NON_MATCHING = 8;

    private JobSearchRelevanceOrder() {
    }

    /**
     * Normalizes a keyword query by trimming whitespace, collapsing consecutive whitespace,
     * and converting to lower case under {@link Locale#ROOT}.
     *
     * @param keyword raw input keyword
     * @return canonical normalized keyword, or null if input is null or blank
     */
    public static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String stripped = keyword.strip();
        return MULTI_SPACE_PATTERN.matcher(stripped).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    /**
     * Normalizes an arbitrary text string for relevance comparison.
     *
     * @param text input string
     * @return normalized text or empty string if null or blank
     */
    public static String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String stripped = text.strip();
        return MULTI_SPACE_PATTERN.matcher(stripped).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    /**
     * Evaluates the relevance precedence tier for a given job against a normalized keyword.
     *
     * @param job              the candidate job record
     * @param normalizedKeyword the canonical normalized keyword (must already be normalized)
     * @return integer rank from 1 (highest priority) to 8 (non-matching)
     */
    public static int evaluateRank(Job job, String normalizedKeyword) {
        if (job == null || normalizedKeyword == null || normalizedKeyword.isBlank()) {
            return RANK_NON_MATCHING;
        }

        String title = normalizeText(job.getTitle());
        if (!title.isEmpty()) {
            if (title.equals(normalizedKeyword)) {
                return RANK_EXACT_TITLE;
            }
            if (title.startsWith(normalizedKeyword)) {
                return RANK_TITLE_STARTS_WITH;
            }
            if (title.contains(normalizedKeyword)) {
                return RANK_TITLE_CONTAINS;
            }
        }

        String company = normalizeText(job.getCompanyName());
        if (!company.isEmpty()) {
            if (company.equals(normalizedKeyword)) {
                return RANK_EXACT_COMPANY;
            }
            if (company.contains(normalizedKeyword)) {
                return RANK_COMPANY_CONTAINS;
            }
        }

        String location = normalizeText(job.getLocation());
        if (!location.isEmpty() && location.contains(normalizedKeyword)) {
            return RANK_LOCATION_CONTAINS;
        }

        String description = normalizeText(job.getDescription());
        if (!description.isEmpty() && description.contains(normalizedKeyword)) {
            return RANK_DESCRIPTION_CONTAINS;
        }

        return RANK_NON_MATCHING;
    }

    /**
     * Returns a deterministic in-memory {@link Comparator} for {@link Job} entities using the fixed 7-signal
     * relevance precedence followed by canonical tie-breakers.
     *
     * @param keyword search keyword
     * @return deterministic comparator
     */
    public static Comparator<Job> comparator(String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        return (job1, job2) -> {
            // 1. Relevance precedence ASC (rank 1 before rank 2)
            int rank1 = evaluateRank(job1, normalizedKeyword);
            int rank2 = evaluateRank(job2, normalizedKeyword);
            int rankDiff = Integer.compare(rank1, rank2);
            if (rankDiff != 0) {
                return rankDiff;
            }

            // 2. postedAt DESC, nulls last
            Instant postedAt1 = job1 != null ? job1.getPostedAt() : null;
            Instant postedAt2 = job2 != null ? job2.getPostedAt() : null;
            if (postedAt1 == null && postedAt2 != null) {
                return 1;
            }
            if (postedAt1 != null && postedAt2 == null) {
                return -1;
            }
            if (postedAt1 != null && postedAt2 != null) {
                int postedDiff = postedAt2.compareTo(postedAt1);
                if (postedDiff != 0) {
                    return postedDiff;
                }
            }

            // 3. companyName ASC
            String comp1 = (job1 != null && job1.getCompanyName() != null) ? job1.getCompanyName() : "";
            String comp2 = (job2 != null && job2.getCompanyName() != null) ? job2.getCompanyName() : "";
            int compDiff = comp1.compareTo(comp2);
            if (compDiff != 0) {
                return compDiff;
            }

            // 4. title ASC
            String title1 = (job1 != null && job1.getTitle() != null) ? job1.getTitle() : "";
            String title2 = (job2 != null && job2.getTitle() != null) ? job2.getTitle() : "";
            int titleDiff = title1.compareTo(title2);
            if (titleDiff != 0) {
                return titleDiff;
            }

            // 5. id ASC
            UUID id1 = job1 != null ? job1.getId() : null;
            UUID id2 = job2 != null ? job2.getId() : null;
            if (id1 != null && id2 != null) {
                return id1.compareTo(id2);
            }
            if (id1 == null && id2 != null) {
                return 1;
            }
            if (id1 != null && id2 == null) {
                return -1;
            }
            return 0;
        };
    }

    /**
     * Applies the deterministic relevance ordering and tie-breakers directly to a JPA {@link CriteriaQuery}.
     * This guarantees that relevance ordering occurs before pagination at the database level.
     *
     * @param cb      CriteriaBuilder
     * @param query   CriteriaQuery for Job entities
     * @param root    Root of Job
     * @param keyword search keyword
     */
    public static void applyRelevanceOrder(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Job> root, String keyword) {
        if (cb == null || query == null || root == null) {
            return;
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword == null) {
            return;
        }

        // Relevance signals 1..7
        Predicate exactTitle = cb.equal(cb.lower(root.get("title")), normalizedKeyword);
        Predicate titleStartsWith = cb.like(cb.lower(root.get("title")), normalizedKeyword + "%");
        Predicate titleContains = cb.like(cb.lower(root.get("title")), "%" + normalizedKeyword + "%");
        Predicate exactCompany = cb.equal(cb.lower(root.get("companyName")), normalizedKeyword);
        Predicate companyContains = cb.like(cb.lower(root.get("companyName")), "%" + normalizedKeyword + "%");
        Predicate locationContains = cb.like(cb.lower(root.get("location")), "%" + normalizedKeyword + "%");
        Predicate descriptionContains = cb.like(cb.lower(root.get("description")), "%" + normalizedKeyword + "%");

        Expression<Integer> relevanceRank = cb.<Integer>selectCase()
                .when(exactTitle, RANK_EXACT_TITLE)
                .when(titleStartsWith, RANK_TITLE_STARTS_WITH)
                .when(titleContains, RANK_TITLE_CONTAINS)
                .when(exactCompany, RANK_EXACT_COMPANY)
                .when(companyContains, RANK_COMPANY_CONTAINS)
                .when(locationContains, RANK_LOCATION_CONTAINS)
                .when(descriptionContains, RANK_DESCRIPTION_CONTAINS)
                .otherwise(RANK_NON_MATCHING);

        // postedAt nulls last: 0 if not null, 1 if null (ascending order places 1s last)
        Expression<Integer> postedAtNullsLast = cb.<Integer>selectCase()
                .when(cb.isNull(root.get("postedAt")), 1)
                .otherwise(0);

        List<Order> orders = List.of(
                cb.asc(relevanceRank),
                cb.asc(postedAtNullsLast),
                cb.desc(root.get("postedAt")),
                cb.asc(root.get("companyName")),
                cb.asc(root.get("title")),
                cb.asc(root.get("id"))
        );

        query.orderBy(orders);
    }
}
