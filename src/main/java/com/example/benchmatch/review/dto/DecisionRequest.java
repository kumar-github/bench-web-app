package com.example.benchmatch.review.dto;

/**
 * POST body for recording a Propose/Reject decision. {@code action} is "approved" (Propose) or
 * "rejected" (Reject) — matching demand_candidate_decisions.status's existing values, rather than
 * inventing separate UI-only verbs.
 */
public record DecisionRequest(String action) {
}
