package com.example.benchmatch.review;

import com.example.benchmatch.entity.DemandCandidateDecision;
import com.example.benchmatch.entity.DemandEnriched;
import com.example.benchmatch.entity.DecisionHistory;
import com.example.benchmatch.entity.MatchCandidate;
import com.example.benchmatch.entity.SupplyEnriched;
import com.example.benchmatch.entity.User;
import com.example.benchmatch.matching.MatchingService;
import com.example.benchmatch.repository.DecisionHistoryRepository;
import com.example.benchmatch.repository.DemandCandidateDecisionRepository;
import com.example.benchmatch.repository.DemandEnrichedRepository;
import com.example.benchmatch.repository.MatchCandidateRepository;
import com.example.benchmatch.repository.SupplyEnrichedRepository;
import com.example.benchmatch.repository.UserRepository;
import com.example.benchmatch.review.dto.DecisionRequest;
import com.example.benchmatch.review.dto.DemandLifecycleState;
import com.example.benchmatch.review.dto.ReviewCandidateDto;
import com.example.benchmatch.review.dto.ReviewQueueItemDto;
import com.example.benchmatch.review.dto.ReviewWorkspaceDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Demand-side Review queue + workspace: the first entry point built against the
 * demand_candidate_decisions/decision_history PERSISTENT tables (full_db_design.sql section 3,
 * created by V1__init_schema.sql but unused by any Java code until now). Supply-side review is a
 * separate, not-yet-built entry point into these SAME rows (pair-level decisions, keyed by
 * (demand_id, employee_id) — see {@link DemandCandidateDecision}'s Javadoc) — nothing here is
 * demand-specific at the data-model level, only at the UI level.
 * <p>
 * Deliberately a straight in-memory pass over {@code findAll()}/{@code findByIsActiveTrue()}
 * results rather than per-demand queries — the same approach {@code ShortlistWorkbookService}
 * already uses at this data volume (hundreds of demands, hundreds of supply rows, a few thousand
 * match_candidates), so this isn't a new performance tradeoff for the codebase.
 * <p>
 * "Propose"/"Reject" record ONLY an in-app status for now (demand_candidate_decisions.status =
 * 'approved'/'rejected') — no external system is called, per the explicit 2026-10-05 scope
 * decision ("remaining all takes place outside this app"). No collision handling between
 * concurrent reviewers either, per that same decision — last write wins, same as every other
 * table in this app.
 */
@Service
public class DemandReviewService {

    /**
     * Placeholder single reviewer identity until real auth exists — see
     * V10__reviewer_default_user.sql's comment for the plan to replace this.
     */
    private static final String CURRENT_USER_EMAIL = "tag-reviewer@bench-match.local";

    private static final Set<String> STRONG_GOOD = Set.of("Strong", "Good");
    private static final Set<String> SHOWN_TIERS = Set.of("Strong", "Good", "Weak");
    private static final Map<String, Integer> TIER_RANK = Map.of("Strong", 0, "Good", 1, "Weak", 2);

    private final DemandEnrichedRepository demandRepository;
    private final SupplyEnrichedRepository supplyRepository;
    private final MatchCandidateRepository matchCandidateRepository;
    private final DemandCandidateDecisionRepository decisionRepository;
    private final DecisionHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final MatchingService matchingService;

    public DemandReviewService(DemandEnrichedRepository demandRepository, SupplyEnrichedRepository supplyRepository,
                                MatchCandidateRepository matchCandidateRepository,
                                DemandCandidateDecisionRepository decisionRepository,
                                DecisionHistoryRepository historyRepository, UserRepository userRepository,
                                MatchingService matchingService) {
        this.demandRepository = demandRepository;
        this.supplyRepository = supplyRepository;
        this.matchCandidateRepository = matchCandidateRepository;
        this.decisionRepository = decisionRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.matchingService = matchingService;
    }

    /**
     * Actionable (≥1 Strong/Good candidate), not-yet-Filled demands, sorted longest-overdue-first —
     * same ageing/urgency comparator ShortlistWorkbookService.generateByDemand() already uses, now
     * applied as a grid's default sort instead of only an export's.
     */
    @Transactional(readOnly = true)
    public List<ReviewQueueItemDto> queue() {
        List<DemandEnriched> demands = demandRepository.findByIsActiveTrue().stream()
                .filter(d -> d.getPersona() != null)
                .toList();

        Map<String, List<MatchCandidate>> candidatesByDemand = matchCandidateRepository.findAll().stream()
                .collect(Collectors.groupingBy(MatchCandidate::getDemandId));
        Map<String, List<DemandCandidateDecision>> decisionsByDemand = decisionRepository.findAll().stream()
                .collect(Collectors.groupingBy(DemandCandidateDecision::getDemandId));

        return demands.stream()
                .map(d -> toQueueItem(d, candidatesByDemand.getOrDefault(d.getDemandId(), List.of()),
                        decisionsByDemand.getOrDefault(d.getDemandId(), List.of())))
                .filter(item -> item != null && item.lifecycleState() != DemandLifecycleState.FILLED)
                .sorted(Comparator.<ReviewQueueItemDto>comparingInt(ReviewQueueItemDto::ageingRank)
                        .thenComparingInt(item -> -(item.balancePositions() == null ? 0 : item.balancePositions())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewWorkspaceDto workspace(String demandId) {
        DemandEnriched demand = demandRepository.findById(demandId)
                .orElseThrow(() -> new NoSuchElementException("No demand " + demandId));

        List<MatchCandidate> candidates = matchCandidateRepository.findByDemandId(demandId).stream()
                .filter(c -> SHOWN_TIERS.contains(c.getOverallTier()))
                .sorted(Comparator.comparingInt(c -> TIER_RANK.getOrDefault(c.getOverallTier(), 9)))
                .toList();

        Map<Long, DemandCandidateDecision> decisionByEmployee = decisionRepository.findByDemandId(demandId).stream()
                .collect(Collectors.toMap(DemandCandidateDecision::getEmployeeId, d -> d));

        Map<Long, SupplyEnriched> supplyById = supplyRepository.findAllById(
                candidates.stream().map(MatchCandidate::getEmployeeId).toList()
        ).stream().collect(Collectors.toMap(SupplyEnriched::getEmployeeId, s -> s));

        Map<Integer, String> userNames = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getUserId, User::getDisplayName));

        List<ReviewCandidateDto> candidateDtos = candidates.stream()
                .map(c -> toCandidateDto(c, supplyById.get(c.getEmployeeId()), decisionByEmployee.get(c.getEmployeeId()), userNames))
                .toList();

        DemandLifecycleState lifecycle = lifecycleOf(demand, candidates, decisionByEmployee.values());

        return new ReviewWorkspaceDto(
                demand.getDemandId(), demand.getClusterNameRaw(), demand.getFinalSkillClusterRaw(),
                demand.getAdditionalRequestRaw(),
                demand.getPersona() == null ? null : demand.getPersona().getName(),
                demand.getSubPersona() == null ? null : demand.getSubPersona().getName(),
                demand.getLocation(), demand.getBand(), demand.getCustomer(), demand.getProjectName(),
                demand.getBalancePositions(), demand.getDueCategory(), demand.getNewAgeing(),
                lifecycle, candidateDtos, nextDemandIdAfter(demandId)
        );
    }

    /**
     * Records a Propose ("approved") or Reject ("rejected") decision on one (demand, employee)
     * pair — an upsert against demand_candidate_decisions' own UNIQUE (demand_id, employee_id), a
     * full status-change row in decision_history, then the refreshed workspace so the view can
     * auto-advance if that was the last undecided candidate.
     */
    @Transactional
    public ReviewWorkspaceDto decide(String demandId, Long employeeId, DecisionRequest request) {
        String action = request.action();
        if (!DemandCandidateDecision.STATUS_APPROVED.equals(action) && !DemandCandidateDecision.STATUS_REJECTED.equals(action)) {
            throw new IllegalArgumentException("Unsupported decision action: " + action);
        }

        String engineTier = matchCandidateRepository.findByDemandId(demandId).stream()
                .filter(c -> c.getEmployeeId().equals(employeeId))
                .map(MatchCandidate::getOverallTier)
                .findFirst().orElse(null);

        DemandCandidateDecision decision = decisionRepository.findByDemandIdAndEmployeeId(demandId, employeeId)
                .orElseGet(() -> new DemandCandidateDecision(demandId, employeeId));
        String oldStatus = decision.getDecisionId() == null ? null : decision.getStatus();

        Integer currentUserId = currentUser().getUserId();
        decision.setEngineTierAtDecision(engineTier);
        decision.setStatus(action);
        decision.setDecidedBy(currentUserId);
        decision.setDecidedAt(OffsetDateTime.now());
        decision = decisionRepository.save(decision);

        if (!action.equals(oldStatus)) {
            historyRepository.save(new DecisionHistory(decision.getDecisionId(), oldStatus, action, currentUserId));
        }

        return workspace(demandId);
    }

    private User currentUser() {
        return userRepository.findByEmail(CURRENT_USER_EMAIL)
                .orElseThrow(() -> new IllegalStateException(
                        "No reviewer user seeded — expected V10__reviewer_default_user.sql to have run"));
    }

    private String nextDemandIdAfter(String demandId) {
        List<ReviewQueueItemDto> q = queue();
        int idx = -1;
        for (int i = 0; i < q.size(); i++) {
            if (q.get(i).demandId().equals(demandId)) {
                idx = i;
                break;
            }
        }
        if (idx >= 0 && idx + 1 < q.size()) {
            return q.get(idx + 1).demandId();
        }
        // Current demand no longer in the queue (just became Filled/Exhausted via this decision,
        // or was never actionable) — fall back to the front of the queue so auto-advance still
        // lands somewhere useful, unless that's the demand the reviewer is already leaving.
        return q.stream().map(ReviewQueueItemDto::demandId).filter(id -> !id.equals(demandId)).findFirst().orElse(null);
    }

    private ReviewQueueItemDto toQueueItem(DemandEnriched d, List<MatchCandidate> candidates,
                                            List<DemandCandidateDecision> decisions) {
        List<MatchCandidate> strongGood = candidates.stream().filter(c -> STRONG_GOOD.contains(c.getOverallTier())).toList();
        if (strongGood.isEmpty()) {
            return null; // not actionable — belongs on a future "No Coverage"/Coverage Log view, not here
        }
        int strongCount = (int) candidates.stream().filter(c -> "Strong".equals(c.getOverallTier())).count();
        int goodCount = (int) candidates.stream().filter(c -> "Good".equals(c.getOverallTier())).count();
        int weakCount = (int) candidates.stream().filter(c -> "Weak".equals(c.getOverallTier())).count();

        DemandLifecycleState lifecycle = lifecycleOf(d, candidates, decisions);
        int approvedCount = (int) decisions.stream().filter(dec -> DemandCandidateDecision.STATUS_APPROVED.equals(dec.getStatus())).count();
        int decidedCount = (int) decisions.stream()
                .filter(dec -> DemandCandidateDecision.STATUS_APPROVED.equals(dec.getStatus())
                        || DemandCandidateDecision.STATUS_REJECTED.equals(dec.getStatus()))
                .count();
        Set<Long> decidedEmployeeIds = decisions.stream()
                .filter(dec -> DemandCandidateDecision.STATUS_APPROVED.equals(dec.getStatus())
                        || DemandCandidateDecision.STATUS_REJECTED.equals(dec.getStatus()))
                .map(DemandCandidateDecision::getEmployeeId)
                .collect(Collectors.toSet());
        int undecidedStrongGood = (int) strongGood.stream().filter(c -> !decidedEmployeeIds.contains(c.getEmployeeId())).count();

        return new ReviewQueueItemDto(
                d.getDemandId(), d.getClusterNameRaw(),
                d.getPersona() == null ? null : d.getPersona().getName(),
                d.getSubPersona() == null ? null : d.getSubPersona().getName(),
                d.getLocation(), d.getBand(), d.getCustomer(), d.getProjectName(),
                d.getBalancePositions(), d.getDueCategory(), d.getNewAgeing(),
                matchingService.ageingRank(d.getDueCategory()),
                lifecycle, strongCount, goodCount, weakCount, decidedCount, undecidedStrongGood, approvedCount
        );
    }

    private DemandLifecycleState lifecycleOf(DemandEnriched d, List<MatchCandidate> candidates,
                                              java.util.Collection<DemandCandidateDecision> decisions) {
        List<MatchCandidate> strongGood = candidates.stream().filter(c -> STRONG_GOOD.contains(c.getOverallTier())).toList();
        int totalPositions = d.getBalancePositions() == null ? 0 : d.getBalancePositions();
        int approvedCount = (int) decisions.stream().filter(dec -> DemandCandidateDecision.STATUS_APPROVED.equals(dec.getStatus())).count();

        if (totalPositions > 0 && approvedCount >= totalPositions) {
            return DemandLifecycleState.FILLED;
        }
        Set<Long> decidedEmployeeIds = decisions.stream()
                .filter(dec -> DemandCandidateDecision.STATUS_APPROVED.equals(dec.getStatus())
                        || DemandCandidateDecision.STATUS_REJECTED.equals(dec.getStatus()))
                .map(DemandCandidateDecision::getEmployeeId)
                .collect(Collectors.toSet());
        boolean allStrongGoodDecided = !strongGood.isEmpty()
                && strongGood.stream().allMatch(c -> decidedEmployeeIds.contains(c.getEmployeeId()));
        if (allStrongGoodDecided) {
            return DemandLifecycleState.EXHAUSTED;
        }
        return DemandLifecycleState.OPEN;
    }

    private ReviewCandidateDto toCandidateDto(MatchCandidate c, SupplyEnriched supply,
                                               DemandCandidateDecision decision, Map<Integer, String> userNames) {
        return new ReviewCandidateDto(
                c.getEmployeeId(),
                supply == null ? "Employee #" + c.getEmployeeId() : supply.getEmployeeName(),
                supply == null ? null : supply.getBand(),
                supply == null ? null : supply.getSubBand(),
                supply == null ? null : supply.getLocation(),
                supply == null ? null : supply.getBenchAgeingDays(),
                supply == null ? null : supply.getRating(),
                c.getOverallTier(), c.getSkillSignal(), c.getBandSignal(), c.getLocationSignal(), c.getOneLiner(),
                decision == null ? null : decision.getStatus(),
                decision == null ? null : decision.getDecidedAt(),
                decision == null || decision.getDecidedBy() == null ? null : userNames.get(decision.getDecidedBy())
        );
    }
}
