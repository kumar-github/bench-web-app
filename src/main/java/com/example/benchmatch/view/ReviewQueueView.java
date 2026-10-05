package com.example.benchmatch.view;

import com.example.benchmatch.review.DemandReviewService;
import com.example.benchmatch.review.dto.DemandLifecycleState;
import com.example.benchmatch.review.dto.ReviewQueueItemDto;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import java.util.List;

/**
 * Demand-side Review landing page — every actionable demand (≥1 Strong/Good candidate) that isn't
 * fully Filled yet, longest-overdue first (DemandReviewService.queue()'s Javadoc). Clicking a row
 * opens {@link ReviewWorkspaceView} for that one demand.
 * <p>
 * A Grid with a single component column — not a plain {@code Div} of rows — rendering each row in
 * the same card style as Dashboard's queue rows ({@code .bm-queue-row} etc.). Found 2026-10-05:
 * with real data this queue runs into the hundreds of demands (458 in the reporter's dataset), and
 * a plain Div list renders every row as real DOM, which (a) is slow to render and scroll, and
 * (b) at that height overflowed {@code .bm-outlet} before that was fixed to scroll internally (see
 * styles.css's {@code .bm-shell}/{@code .bm-outlet} comments) — the sidebar visually "ending"
 * partway down the page was a symptom of that overflow, not of this view specifically. Grid
 * virtualizes rendering (only visible rows exist in the DOM) regardless of whether its items come
 * from an in-memory list or a lazy provider — at this row count an in-memory
 * {@code grid.setItems(list)} is enough; DemandReviewService.queue() stays a plain
 * {@code List<ReviewQueueItemDto>} rather than growing a Pageable-aware counterpart the way
 * SupplyQueryService/DemandQueryService had to (see GridPagingUtil's Javadoc for why THOSE needed
 * one — this list tops out in the hundreds, not the thousands those grids page over).
 */
@Route(value = "review", layout = MainLayout.class)
@PageTitle("Review")
public class ReviewQueueView extends VerticalLayout {

    public ReviewQueueView(DemandReviewService reviewService) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        List<ReviewQueueItemDto> queue = reviewService.queue();

        Span title = new Span("Demand Review Queue");
        title.addClassName("bm-card-title-lg");
        Span subtitle = new Span(queue.size() + " demand(s) with at least one Strong/Good candidate — most overdue first");
        subtitle.addClassName("bm-card-subtitle");
        Div header = new Div(title, subtitle);
        header.addClassName("bm-card-title-block");
        add(header);

        Grid<ReviewQueueItemDto> grid = buildGrid();
        grid.setItems(queue);
        add(grid);
        setFlexGrow(1, grid);

        if (queue.isEmpty()) {
            Span empty = new Span("Nothing to review right now.");
            empty.addClassName("bm-card-subtitle");
            add(empty);
        }
    }

    private Grid<ReviewQueueItemDto> buildGrid() {
        Grid<ReviewQueueItemDto> grid = new Grid<>();
        grid.addClassName("bm-review-grid");
        grid.addComponentColumn(this::buildRow).setAutoWidth(false).setFlexGrow(1);
        grid.setSizeFull();
        grid.setAllRowsVisible(false);
        return grid;
    }

    private Div buildRow(ReviewQueueItemDto item) {
        Div avatar = new Div(new Span(String.valueOf(item.balancePositions() == null ? 0 : item.balancePositions())));
        avatar.addClassName("bm-queue-avatar");

        Span name = new Span(item.demandId() + " — " + label(item));
        name.addClassName("bm-queue-name");
        Span meta = new Span(metaLine(item));
        meta.addClassName("bm-queue-meta");
        Div nameLine = new Div(name, meta);
        nameLine.addClassName("bm-queue-name-line");

        Div row = new Div(avatar, nameLine);
        row.addClassName("bm-queue-row");

        if (item.lifecycleState() == DemandLifecycleState.EXHAUSTED) {
            Span badge = new Span("EXHAUSTED — NEEDS HIRING");
            badge.addClassName("bm-badge-danger");
            row.add(badge);
        } else if (item.undecidedStrongGoodCount() == 0) {
            Span badge = new Span("ALL CANDIDATES DECIDED");
            badge.addClassName("bm-badge-danger");
            row.add(badge);
        } else {
            // Neutral, not alarming — most rows land here, and every row being red noise defeats
            // the point of a danger badge (it should mean "look here first", not "every row").
            Span badge = new Span(item.undecidedStrongGoodCount() + " undecided");
            badge.addClassName("bm-badge-neutral");
            row.add(badge);
        }

        RouterLink open = new RouterLink("Open →", ReviewWorkspaceView.class, item.demandId());
        open.addClassName("bm-queue-open");
        row.add(open);

        return row;
    }

    private String label(ReviewQueueItemDto item) {
        if (item.subPersona() != null) {
            return item.persona() + " - " + item.subPersona();
        }
        return item.persona() == null ? (item.clusterNameRaw() == null ? "—" : item.clusterNameRaw()) : item.persona();
    }

    private String metaLine(ReviewQueueItemDto item) {
        return (item.location() == null ? "—" : item.location())
                + "  ·  " + (item.band() == null ? "—" : item.band())
                + "  ·  Due: " + (item.dueCategory() == null ? "—" : item.dueCategory())
                + "  ·  " + item.strongCount() + " Strong / " + item.goodCount() + " Good / " + item.weakCount() + " Weak"
                + "  ·  Positions open: " + (item.balancePositions() == null ? 0 : item.balancePositions());
    }
}
