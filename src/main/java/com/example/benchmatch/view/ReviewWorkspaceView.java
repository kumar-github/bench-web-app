package com.example.benchmatch.view;

import com.example.benchmatch.entity.DemandCandidateDecision;
import com.example.benchmatch.review.DemandReviewService;
import com.example.benchmatch.review.dto.DecisionRequest;
import com.example.benchmatch.review.dto.DemandLifecycleState;
import com.example.benchmatch.review.dto.ReviewCandidateDto;
import com.example.benchmatch.review.dto.ReviewWorkspaceDto;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.*;

import java.util.*;

/**
 * Demand Workspace — the per-demand decision screen opened from {@link ReviewQueueView}. Shows the demand's own ask
 * (skill cluster, location/band, customer/project, due category) plus every Strong/Good/Weak candidate, grouped by
 * tier, each with Propose/Reject.
 * <p>
 * UX decisions locked in 2026-10-05 (all "Recommended" in the AskUserQuestion round):
 * <ul>
 *   <li>Decided candidates stay visible, grayed out — never removed from the list.</li>
 *   <li>Auto-advances to the next demand in the queue once the LAST undecided Strong/Good
 *       candidate on this demand is decided (i.e. the demand leaves OPEN for FILLED/EXHAUSTED).</li>
 *   <li>Mouse-only — no keyboard shortcuts in this first build.</li>
 * </ul>
 * Rebuilds its content wholesale after every decision rather than patching the DOM in place —
 * simplest correct option for a screen that's clicked through at most a few hundred times a day,
 * and keeps this class readable; revisit only if that volume assumption changes.
 */
@Route(value = "review/demand", layout = MainLayout.class)
@PageTitle("Review")
public class ReviewWorkspaceView extends Div implements HasUrlParameter<String> {

    private final DemandReviewService reviewService;
    /**
     * Per-card expand state ("two states, four dimensions" — collapsed by default, expanded on click), independent of
     * {@link #expandAll}. Keyed by employeeId since a workspace only ever shows one demand's candidates at a time.
     */
    private final Set<Long> expandedEmployeeIds = new HashSet<>();
    private String demandId;
    /**
     * The Review queue page (1-indexed, matching the URL the queue itself uses) this workspace was opened from, if any
     * — carried via the "page" query parameter on the RouterLink that opened it (see ReviewQueueView.buildRow()'s
     * comment). Threaded through every navigation back toward the queue below (the plain back link, and both of
     * decide()'s auto-navigate cases) so a reviewer working through page 6 of 13 doesn't get dumped back to page 1
     * after every single decision — the #1 UI/UX gap flagged on 2026-10-06.
     */
    private String fromPage;
    /**
     * The global "expand all" toggle from the requirements doc's "Card expand trigger" decision — "both mechanisms, not
     * one or the other" — for the audit case where a reviewer wants every raw value in front of them at once. ORed with
     * expandedEmployeeIds per-card, so toggling it off doesn't lose individually-expanded cards.
     */
    private boolean expandAll;

    public ReviewWorkspaceView(DemandReviewService reviewService) {
        this.reviewService = reviewService;
        addClassName("bm-dash");
    }

    private static String initialsOf(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && initials.length() < 2) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return initials.isEmpty() ? "?" : initials.toString();
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    @Override
    public void setParameter(BeforeEvent event, String demandId) {
        this.demandId = demandId;
        List<String> pageParam = event.getLocation().getQueryParameters().getParameters().get("page");
        this.fromPage = (pageParam == null || pageParam.isEmpty()) ? null : pageParam.get(0);
        render();
    }

    private QueryParameters backQueryParameters() {
        return fromPage == null ? QueryParameters.empty() : QueryParameters.simple(Map.of("page", fromPage));
    }

    private void render() {
        removeAll();
        ReviewWorkspaceDto workspace = reviewService.workspace(demandId);

        add(buildBackLink());
        add(buildDemandHeader(workspace));
        add(buildExpandAllToggle());
        add(buildCandidateSection("Strong", workspace, "Strong"));
        add(buildCandidateSection("Good", workspace, "Good"));
        add(buildCandidateSection("Weak", workspace, "Weak"));
        add(buildOverrideSection(workspace));
    }

    /**
     * "Both mechanisms, not one or the other" (requirements doc) — this sits alongside, not instead of, each card's own
     * click-to-expand.
     */
    private Div buildExpandAllToggle() {
        Button toggle = new Button(expandAll ? "Collapse all" : "Expand all", e -> {
            expandAll = !expandAll;
            render();
        });
        toggle.addThemeVariants(ButtonVariant.SMALL, ButtonVariant.TERTIARY);
        Div bar = new Div(toggle);
        bar.getStyle().set("display", "flex").set("justify-content", "flex-end");
        return bar;
    }

    private RouterLink buildBackLink() {
        RouterLink back = new RouterLink("← Back to queue", ReviewQueueView.class);
        back.addClassName("bm-queue-open");
        back.setQueryParameters(backQueryParameters());
        return back;
    }

    private Div buildDemandHeader(ReviewWorkspaceDto w) {
        Span title = new Span(w.demandId() + " — "
                + (w.subPersona() != null ? w.persona() + " - " + w.subPersona()
                : (w.persona() == null ? nz(w.clusterNameRaw()) : w.persona())));
        title.addClassName("bm-card-title-lg");

        String lifecycleLabel = switch (w.lifecycleState()) {
            case OPEN -> "Open";
            case FILLED -> "Filled";
            case EXHAUSTED -> "Exhausted — needs hiring";
        };
        Span subtitle = new Span(nz(w.location()) + "  ·  " + nz(w.band())
                + "  ·  Due: " + nz(w.dueCategory())
                + "  ·  Positions open: " + (w.balancePositions() == null ? 0 : w.balancePositions())
                + "  ·  " + nz(w.customer()) + (w.projectName() == null ? "" : " / " + w.projectName())
                + "  ·  Status: " + lifecycleLabel);
        subtitle.addClassName("bm-card-subtitle");

        Div titleBlock = new Div(title, subtitle);
        titleBlock.addClassName("bm-card-title-block");
        Div header = new Div(titleBlock);
        header.addClassName("bm-card");
        return header;
    }

    private Div buildCandidateSection(String heading, ReviewWorkspaceDto w, String tier) {
        List<ReviewCandidateDto> inTier = w.candidates().stream()
                .filter(c -> tier.equals(c.overallTier())).toList();

        Span title = new Span(heading + " (" + inTier.size() + ")");
        title.addClassName("bm-card-title");

        Div rows = new Div();
        rows.addClassName("bm-cand-list");
        for (ReviewCandidateDto c : inTier) {
            rows.add(buildCandidateCard(c, false));
        }
        if (inTier.isEmpty()) {
            Span empty = new Span("No " + heading.toLowerCase(Locale.ROOT) + " candidates.");
            empty.addClassName("bm-card-subtitle");
            rows.add(empty);
        }

        Div card = new Div(title, rows);
        card.addClassName("bm-card");
        return card;
    }

    /**
     * The one-sub-band-below Override-eligible bucket — see ReviewCandidateDto.overrideEligible()'s Javadoc. A separate
     * section, never mixed into Strong/Good/Weak, so Override is never mistaken for an ordinary Propose (same reasoning
     * the requirements doc gives for keeping the action itself distinct). Omitted entirely when there's nothing in it,
     * rather than an always-present "(0)" section — unlike Strong/Good/Weak, which are always one of the demand's three
     * defined tiers, this bucket is the exception case, not a default empty state worth showing.
     */
    private Div buildOverrideSection(ReviewWorkspaceDto w) {
        List<ReviewCandidateDto> eligible = w.candidates().stream()
                .filter(ReviewCandidateDto::overrideEligible).toList();
        if (eligible.isEmpty()) {
            return new Div();
        }

        Span title = new Span("Override-eligible — one band below the ask (" + eligible.size() + ")");
        title.addClassName("bm-card-title");
        Span subtitle = new Span("Excluded by the engine's band rule, but close enough that a reviewer may "
                + "deliberately choose to propose anyway. Use Override, not Propose, so this is never read "
                + "back as an ordinary approval.");
        subtitle.addClassName("bm-card-subtitle");

        Div rows = new Div();
        rows.addClassName("bm-cand-list");
        for (ReviewCandidateDto c : eligible) {
            rows.add(buildCandidateCard(c, true));
        }

        Div card = new Div(title, subtitle, rows);
        card.addClassName("bm-card");
        card.addClassName("bm-override-section");
        return card;
    }

    /**
     * One "two states, four dimensions" card. Collapsed by default: overall tier/one-liner plus four dimension rows
     * showing ONLY the plain-English Collapsed text. Expanded (per-card click, or the global "expand all" toggle): the
     * same four rows also show their raw-value Expanded text. All four dimensions expand together, never independently,
     * per the requirements doc.
     */
    private Div buildCandidateCard(ReviewCandidateDto c, boolean isOverride) {
        boolean expanded = expandAll || expandedEmployeeIds.contains(c.employeeId());

        Div avatar = new Div(new Span(initialsOf(c.employeeName())));
        avatar.addClassName("bm-queue-avatar");

        Span name = new Span(c.employeeName() + "  (#" + c.employeeId() + ")");
        name.addClassName("bm-queue-name");
        Span oneLiner = new Span(nz(c.oneLiner()));
        oneLiner.addClassName("bm-queue-meta");
        Div nameLine = new Div(name, oneLiner);
        nameLine.addClassName("bm-queue-name-line");
        nameLine.getStyle().set("cursor", "pointer");
        nameLine.addClickListener(e -> {
            if (expandedEmployeeIds.contains(c.employeeId())) {
                expandedEmployeeIds.remove(c.employeeId());
            } else {
                expandedEmployeeIds.add(c.employeeId());
            }
            render();
        });

        Span tierBadge = new Span(isOverride ? "ONE BAND BELOW" : c.overallTier().toUpperCase(Locale.ROOT));
        tierBadge.addClassName("bm-cand-tier-badge");
        tierBadge.addClassName(isOverride ? "bm-cand-tier-override" : "bm-cand-tier-" + c.overallTier().toLowerCase(Locale.ROOT));

        Div header = new Div(avatar, nameLine, tierBadge);
        header.addClassName("bm-cand-header");

        Div dims = new Div(
                buildDimensionRow("Skill", c.skillCollapsed(), c.skillExpanded(), expanded),
                buildDimensionRow("Band", c.bandCollapsed(), c.bandExpanded(), expanded),
                buildDimensionRow("Location", c.locationCollapsed(), c.locationExpanded(), expanded),
                buildDimensionRow("Assessment", c.assessmentCollapsed(), c.assessmentExpanded(), expanded)
        );
        dims.addClassName("bm-cand-dims");

        Div card = new Div(header, dims);
        card.addClassName("bm-cand-card");
        if (isOverride) {
            card.addClassName("bm-cand-card-override");
        }

        if (c.isDecided()) {
            card.addClassName("bm-candidate-row-decided");
            Span status = new Span(c.decisionStatus().toUpperCase(Locale.ROOT)
                    + (c.decidedByName() == null ? "" : " · " + c.decidedByName()));
            status.addClassName("bm-badge-danger");
            card.add(status);
        } else if (isOverride) {
            Button override = new Button("Override", e -> decide(c.employeeId(), DemandCandidateDecision.STATUS_OVERRIDDEN));
            override.addThemeVariants(ButtonVariant.SMALL);
            override.addClassName("bm-override-btn");
            Button reject = new Button("Reject", e -> decide(c.employeeId(), DemandCandidateDecision.STATUS_REJECTED));
            reject.addThemeVariants(ButtonVariant.SMALL, ButtonVariant.TERTIARY);
            Div actions = new Div(override, reject);
            actions.getStyle().set("display", "flex").set("gap", "8px").set("flex-shrink", "0");
            card.add(actions);
        } else {
            Button propose = new Button("Propose", e -> decide(c.employeeId(), DemandCandidateDecision.STATUS_APPROVED));
            propose.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.SMALL);
            Button reject = new Button("Reject", e -> decide(c.employeeId(), DemandCandidateDecision.STATUS_REJECTED));
            reject.addThemeVariants(ButtonVariant.SMALL, ButtonVariant.TERTIARY);
            Div actions = new Div(propose, reject);
            actions.getStyle().set("display", "flex").set("gap", "8px").set("flex-shrink", "0");
            card.add(actions);
        }

        return card;
    }

    /**
     * One dimension row. Collapsed text always shows; Expanded text (the raw values) is appended only when
     * {@code expanded} is true — never shown alone, per the card spec.
     */
    private Div buildDimensionRow(String label, String collapsed, String expandedText, boolean expanded) {
        Span labelSpan = new Span(label);
        labelSpan.addClassName("bm-cand-dim-label");
        Span collapsedSpan = new Span(nz(collapsed));
        collapsedSpan.addClassName("bm-cand-dim-collapsed");

        Div row = new Div(labelSpan, collapsedSpan);
        row.addClassName("bm-cand-dim-row");
        if (expanded && expandedText != null && !expandedText.isBlank()) {
            Span expandedSpan = new Span(expandedText);
            expandedSpan.addClassName("bm-cand-dim-expanded");
            row.add(expandedSpan);
        }
        return row;
    }

    private void decide(Long employeeId, String action) {
        ReviewWorkspaceDto updated = reviewService.decide(demandId, employeeId, new DecisionRequest(action));
        if (updated.lifecycleState() != DemandLifecycleState.OPEN) {
            // That was the last undecided Strong/Good candidate — auto-advance per the locked-in
            // UX decision, rather than leaving the reviewer looking at a demand with nothing left
            // to decide.
            String next = updated.nextDemandId();
            if (next != null) {
                // "review/demand/<id>" is this view's own @Route path (value = "review/demand") —
                // the string-plus-QueryParameters overload is used instead of the Class-based one
                // specifically so fromPage survives the auto-advance (see this field's Javadoc);
                // there's no Class-based navigate(...) overload that also takes QueryParameters.
                UI.getCurrent().navigate("review/demand/" + next, backQueryParameters());
                return;
            }
            UI.getCurrent().navigate("review", backQueryParameters());
            return;
        }
        render();
    }
}
