package com.example.benchmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * PERSISTENT — per-demand reviewer state, created by V1__init_schema.sql but unused by any Java
 * code until now (same situation {@link DemandCandidateDecision} was in before the Review feature
 * was built — see full_db_design.sql section 3). A refresh/matching run must never write to this
 * table; {@code demand_id} is a plain {@code TEXT PRIMARY KEY REFERENCES demand_enriched}, not a
 * generated id, so the app assigns it explicitly rather than via {@code @GeneratedValue}.
 * <p>
 * First real use (2026-10-06): the Review queue's "Flag for hiring" action on EXHAUSTED demands
 * (every Strong/Good candidate already Proposed/Rejected, but positions remain open) repurposes
 * {@code needs_reattention}/{@code reattention_reason} — originally scoped in the schema comment
 * for "source text changed since last review" — as a generic "this demand needs a human to look
 * again, and here's why" flag. That's exactly the EXHAUSTED signal too, so a second boolean column
 * isn't needed: one flag, with the reason text saying which case it is.
 */
@Entity
@Table(name = "demand_review_state")
public class DemandReviewState {

    @Id
    @Column(name = "demand_id")
    private String demandId;

    @Column(name = "status", nullable = false)
    private String status = "open";

    @Column(name = "assigned_reviewer")
    private Integer assignedReviewer;

    @Column(name = "notes")
    private String notes;

    @Column(name = "needs_reattention", nullable = false)
    private boolean needsReattention;

    @Column(name = "reattention_reason")
    private String reattentionReason;

    @Column(name = "closed_by")
    private Integer closedBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    protected DemandReviewState() {
    }

    public DemandReviewState(String demandId) {
        this.demandId = demandId;
    }

    public String getDemandId() {
        return demandId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAssignedReviewer() {
        return assignedReviewer;
    }

    public void setAssignedReviewer(Integer assignedReviewer) {
        this.assignedReviewer = assignedReviewer;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isNeedsReattention() {
        return needsReattention;
    }

    public void setNeedsReattention(boolean needsReattention) {
        this.needsReattention = needsReattention;
    }

    public String getReattentionReason() {
        return reattentionReason;
    }

    public void setReattentionReason(String reattentionReason) {
        this.reattentionReason = reattentionReason;
    }

    public Integer getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(Integer closedBy) {
        this.closedBy = closedBy;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }
}
