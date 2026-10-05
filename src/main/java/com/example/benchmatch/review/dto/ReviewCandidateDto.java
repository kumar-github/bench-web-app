package com.example.benchmatch.review.dto;

import java.time.OffsetDateTime;

public record ReviewCandidateDto(
        Long employeeId,
        String employeeName,
        String band,
        String subBand,
        String location,
        Integer benchAgeingDays,
        String rating,
        String overallTier,     // Strong / Good / Weak
        String skillSignal,
        String bandSignal,
        String locationSignal,
        String oneLiner,
        String decisionStatus,  // pending / approved / rejected / staffed / null (never decided)
        OffsetDateTime decidedAt,
        String decidedByName
) {
    public boolean isDecided() {
        return decisionStatus != null
                && !decisionStatus.equals("pending");
    }
}
