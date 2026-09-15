package com.joblivo.job.service;

import com.joblivo.job.Job;
import com.joblivo.job.config.JobFreshnessProperties;
import com.joblivo.job.model.JobFreshnessStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Encapsulates deterministic freshness ordering rules for Job Discovery search results.
 * <p>
 * Evaluates canonical freshness lifecycle precedence:
 * <ol>
 *   <li>{@link JobFreshnessStatus#ACTIVE} (rank 1)</li>
 *   <li>{@link JobFreshnessStatus#UNKNOWN} (rank 2)</li>
 *   <li>{@link JobFreshnessStatus#STALE} (rank 3)</li>
 *   <li>{@link JobFreshnessStatus#EXPIRED} (rank 4)</li>
 * </ol>
 * Followed by deterministic tie-breakers:
 * <ul>
 *   <li>For ACTIVE, UNKNOWN, STALE: {@code postedAt} DESC, placing null timestamps consistently last</li>
 *   <li>For EXPIRED: {@code expiresAt} DESC, placing null timestamps consistently last</li>
 *   <li>{@code companyName} ASC</li>
 *   <li>{@code title} ASC</li>
 *   <li>{@code id} ASC</li>
 * </ul>
 */
public final class JobSearchFreshnessOrder {

    public static final int RANK_ACTIVE = 1;
    public static final int RANK_UNKNOWN = 2;
    public static final int RANK_STALE = 3;
    public static final int RANK_EXPIRED = 4;

    private JobSearchFreshnessOrder() {
    }

    /**
     * Maps a {@link JobFreshnessStatus} to its integer sort rank precedence (1 to 4).
     *
     * @param status job freshness status
     * @return integer rank where lower values represent higher priority
     */
    public static int rank(JobFreshnessStatus status) {
        if (status == null) {
            return RANK_UNKNOWN;
        }
        return switch (status) {
            case ACTIVE -> RANK_ACTIVE;
            case UNKNOWN -> RANK_UNKNOWN;
            case STALE -> RANK_STALE;
            case EXPIRED -> RANK_EXPIRED;
        };
    }

    /**
     * Returns a deterministic in-memory {@link Comparator} for {@link Job} entities using the canonical
     * freshness status precedence (ACTIVE -&gt; UNKNOWN -&gt; STALE -&gt; EXPIRED) followed by canonical tie-breakers.
     *
     * @param evaluator canonical freshness evaluator
     * @return deterministic comparator
     */
    public static Comparator<Job> comparator(JobFreshnessEvaluator evaluator) {
        JobFreshnessEvaluator effectiveEvaluator = evaluator != null ? evaluator : new JobFreshnessEvaluator();
        return (job1, job2) -> {
            JobFreshnessStatus status1 = effectiveEvaluator.evaluate(job1);
            JobFreshnessStatus status2 = effectiveEvaluator.evaluate(job2);

            // 1. Freshness rank ASC (1 before 2, etc.)
            int rank1 = rank(status1);
            int rank2 = rank(status2);
            int rankDiff = Integer.compare(rank1, rank2);
            if (rankDiff != 0) {
                return rankDiff;
            }

            // 2. Primary timestamp tie-breaker:
            // For EXPIRED: expiresAt DESC, nulls last
            // For ACTIVE, UNKNOWN, STALE: postedAt DESC, nulls last
            Instant ts1 = (status1 == JobFreshnessStatus.EXPIRED)
                    ? (job1 != null ? job1.getExpiresAt() : null)
                    : (job1 != null ? job1.getPostedAt() : null);
            Instant ts2 = (status2 == JobFreshnessStatus.EXPIRED)
                    ? (job2 != null ? job2.getExpiresAt() : null)
                    : (job2 != null ? job2.getPostedAt() : null);

            if (ts1 == null && ts2 != null) {
                return 1;
            }
            if (ts1 != null && ts2 == null) {
                return -1;
            }
            if (ts1 != null && ts2 != null) {
                int tsDiff = ts2.compareTo(ts1); // DESC
                if (tsDiff != 0) {
                    return tsDiff;
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
     * Convenience comparator using production default evaluator.
     *
     * @return deterministic comparator
     */
    public static Comparator<Job> comparator() {
        return comparator(new JobFreshnessEvaluator());
    }

    /**
     * Applies the deterministic freshness ordering and canonical tie-breakers directly to a JPA {@link CriteriaQuery}.
     * This guarantees that freshness ordering occurs before pagination at the database level.
     *
     * @param cb        CriteriaBuilder
     * @param query     CriteriaQuery for Job entities
     * @param root      Root of Job
     * @param evaluator canonical freshness evaluator
     */
    public static void applyFreshnessOrder(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Job> root, JobFreshnessEvaluator evaluator) {
        if (cb == null || query == null || root == null) {
            return;
        }

        Clock clock = (evaluator != null && evaluator.getClock() != null) ? evaluator.getClock() : Clock.systemUTC();
        Instant now = clock.instant();

        Duration staleDuration = (evaluator != null && evaluator.getProperties() != null && evaluator.getProperties().getStaleAfter() != null)
                ? evaluator.getProperties().getStaleAfter()
                : JobFreshnessProperties.DEFAULT_STALE_AFTER;
        Instant staleCutoff = now.minus(staleDuration);

        // 1. Contradictory or completely empty timestamps -> UNKNOWN (rank 2)
        Predicate allTimestampsNull = cb.and(
                cb.isNull(root.get("lastSeenAt")),
                cb.isNull(root.get("postedAt")),
                cb.isNull(root.get("expiresAt")),
                cb.isNull(root.get("discoveredAt"))
        );

        Predicate expiresBeforePosted = cb.and(
                cb.isNotNull(root.get("postedAt")),
                cb.isNotNull(root.get("expiresAt")),
                cb.lessThan(root.get("expiresAt"), root.get("postedAt"))
        );

        Predicate lastSeenBeforeDiscovered = cb.and(
                cb.isNotNull(root.get("discoveredAt")),
                cb.isNotNull(root.get("lastSeenAt")),
                cb.lessThan(root.get("lastSeenAt"), root.get("discoveredAt"))
        );

        Predicate postedInFuture = cb.and(
                cb.isNotNull(root.get("postedAt")),
                cb.greaterThan(root.get("postedAt"), now)
        );

        Predicate lastSeenInFuture = cb.and(
                cb.isNotNull(root.get("lastSeenAt")),
                cb.greaterThan(root.get("lastSeenAt"), now)
        );

        Predicate discoveredInFuture = cb.and(
                cb.isNotNull(root.get("discoveredAt")),
                cb.greaterThan(root.get("discoveredAt"), now)
        );

        Predicate isContradictoryOrEmpty = cb.or(
                allTimestampsNull,
                expiresBeforePosted,
                lastSeenBeforeDiscovered,
                postedInFuture,
                lastSeenInFuture,
                discoveredInFuture
        );

        // 2. EXPIRED: expiresAt exists and expiresAt <= now (now >= expiresAt)
        Predicate isExpired = cb.and(
                cb.isNotNull(root.get("expiresAt")),
                cb.lessThanOrEqualTo(root.get("expiresAt"), now)
        );

        // 3. STALE: not expired, lastSeenAt exists, and lastSeenAt < staleCutoff
        Predicate isStale = cb.and(
                cb.isNotNull(root.get("lastSeenAt")),
                cb.lessThan(root.get("lastSeenAt"), staleCutoff)
        );

        // 4. ACTIVE: not expired, not stale, and data is fresh (lastSeenAt != null || postedAt != null)
        Predicate isActive = cb.or(
                cb.isNotNull(root.get("lastSeenAt")),
                cb.isNotNull(root.get("postedAt"))
        );

        // Derived freshness rank (1..4)
        Expression<Integer> freshnessRank = cb.<Integer>selectCase()
                .when(isContradictoryOrEmpty, RANK_UNKNOWN)
                .when(isExpired, RANK_EXPIRED)
                .when(isStale, RANK_STALE)
                .when(isActive, RANK_ACTIVE)
                .otherwise(RANK_UNKNOWN);

        // Effective sort timestamp:
        // For EXPIRED: expiresAt
        // For ACTIVE, UNKNOWN, STALE: postedAt
        Expression<Instant> effectiveTimestamp = cb.<Instant>selectCase()
                .when(cb.equal(freshnessRank, RANK_EXPIRED), root.get("expiresAt"))
                .otherwise(root.get("postedAt"));

        // Null timestamps placed consistently last (0 if present, 1 if null)
        Predicate isExpiredState = cb.equal(freshnessRank, RANK_EXPIRED);
        Predicate expiredTimestampNull = cb.and(isExpiredState, cb.isNull(root.get("expiresAt")));
        Predicate nonExpiredTimestampNull = cb.and(cb.not(isExpiredState), cb.isNull(root.get("postedAt")));

        Expression<Integer> timestampNullsLast = cb.<Integer>selectCase()
                .when(cb.or(expiredTimestampNull, nonExpiredTimestampNull), 1)
                .otherwise(0);

        List<Order> orders = List.of(
                cb.asc(freshnessRank),
                cb.asc(timestampNullsLast),
                cb.desc(effectiveTimestamp),
                cb.asc(root.get("companyName")),
                cb.asc(root.get("title")),
                cb.asc(root.get("id"))
        );

        query.orderBy(orders);
    }
}
