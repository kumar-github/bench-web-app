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
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import java.util.Locale;

/**
 * Demand Workspace — the per-demand decision screen opened from {@link ReviewQueueView}. Shows
 * the demand's own ask (skill cluster, location/band, customer/project, due category) plus every
 * Strong/Good/Weak candidate, grouped by tier, each with Propose/Reject.
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
    private String demandId;

    public ReviewWorkspaceView(DemandReviewService reviewService) {
        this.reviewService = reviewService;
        addClassName("bm-dash");
    }

    @Override
    public void setParameter(BeforeEvent event, String demandId) {
        this.demandId = demandId;
        render();
    }

    private void render() {
        removeAll();
        ReviewWorkspaceDto workspace = reviewService.workspace(demandId);

        add(buildBackLink());
        add(buildDemandHeader(workspace));
        add(buildCandidateSection("Strong", workspace, "Strong"));
        add(buildCandidateSection("Good", workspace, "Good"));
        add(buildCandidateSection("Weak", workspace, "Weak"));
    }

    private RouterLink buildBackLink() {
        RouterLink back = new RouterLink("← Back to queue", ReviewQueueView.class);
        back.addClassName("bm-queue-open");
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
        java.util.List<ReviewCandidateDto> inTier = w.candidates().stream()
                .filter(c -> tier.equals(c.overallTier())).toList();

        Span title = new Span(heading + " (" + inTier.size() + ")");
        title.addClassName("bm-card-title");

        Div rows = new Div();
        rows.addClassName("bm-queue-rows");
        for (ReviewCandidateDto c : inTier) {
            rows.add(buildCandidateRow(c));
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

    private Div buildCandidateRow(ReviewCandidateDto c) {
        Div avatar = new Div(new Span(initialsOf(c.employeeName())));
        avatar.addClassName("bm-queue-avatar");

        Span name = new Span(c.employeeName() + "  (#" + c.employeeId() + ")");
        name.addClassName("bm-queue-name");
        Span meta = new Span(nz(c.band()) + " / " + nz(c.subBand()) + "  ·  " + nz(c.location())
                + "  ·  Bench " + (c.benchAgeingDays() == null ? "—" : c.benchAgeingDays() + "d")
                + "  ·  " + nz(c.oneLiner()));
        meta.addClassName("bm-queue-meta");
        Div nameLine = new Div(name, meta);
        nameLine.addClassName("bm-queue-name-line");

        Div row = new Div(avatar, nameLine);
        row.addClassName("bm-queue-row");

        if (c.isDecided()) {
            row.addClassName("bm-candidate-row-decided");
            Span status = new Span(c.decisionStatus().toUpperCase(Locale.ROOT)
                    + (c.decidedByName() == null ? "" : " · " + c.decidedByName()));
            status.addClassName("bm-badge-danger");
            row.add(status);
        } else {
            Button propose = new Button("Propose", e -> decide(c.employeeId(), DemandCandidateDecision.STATUS_APPROVED));
            propose.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            Button reject = new Button("Reject", e -> decide(c.employeeId(), DemandCandidateDecision.STATUS_REJECTED));
            reject.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            Div actions = new Div(propose, reject);
            actions.getStyle().set("display", "flex").set("gap", "8px").set("flex-shrink", "0");
            row.add(actions);
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
                UI.getCurrent().navigate(ReviewWorkspaceView.class, next);
                return;
            }
            UI.getCurrent().navigate(ReviewQueueView.class);
            return;
        }
        render();
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
}
