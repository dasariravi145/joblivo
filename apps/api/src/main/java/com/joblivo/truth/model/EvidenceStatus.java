package com.joblivo.truth.model;

/**
 * Fundamental evidence status of a career fact or claim.
 * Core principle: Joblivo must never allow AI to invent a user's professional facts.
 * Claims must never be silently upgraded to {@link #VERIFIED} without direct factual evidence.
 */
public enum EvidenceStatus {

    /**
     * Directly supported by user-provided career data/evidence in their Master Career Profile.
     */
    VERIFIED,

    /**
     * Logically derived or constructed from supported facts without introducing new factual claims
     * (e.g. valid duration calculations, combining two known verified skills).
     */
    DERIVED,

    /**
     * Potentially valid or partially matched, but insufficiently supported by available evidence.
     * Joblivo cannot safely treat this claim as established fact without explicit user confirmation.
     */
    NEEDS_CONFIRMATION,

    /**
     * Not supported by the user's known career information.
     * No adequate evidence exists in the profile. Must never be used as fact.
     */
    UNSUPPORTED
}
