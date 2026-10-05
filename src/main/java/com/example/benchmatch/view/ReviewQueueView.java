package com.example.benchmatch.view;

import com.example.benchmatch.review.DemandReviewService;
import com.example.benchmatch.review.dto.DemandLifecycleState;
import com.example.benchmatch.review.dto.ReviewQueueItemDto;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import java.util.List;

/**
 * Demand-side Review landing page — every actionable demand (≥1 Strong/Good candidate) that isn't
 * fully Filled yet, longest-overdue first (DemandReviewService.queue()'s Javadoc). Clicking a row
 * opens {@link ReviewWorkspaceView} for that one demand.
 * <p>
 * Rebuilt 2026-10-05 to match the approved wireframe (Main.dc.html, "Demand Queue (landing)"
 * artboard) structurally, not just a Dashboard-style card list: urgency-colored left-border rows
 * with a dot+label badge, header stat line, legend, a real search box, and classic page-based
 * pagination (20 rows/page, Prev/1/2/3/Next) — not infinite scroll or Grid virtualization. A
 * same-day earlier attempt used a Vaadin {@code Grid} for virtualized rendering once the row count
 * turned out to be in the hundreds (458 in the reporter's dataset); that solved the "very long
 * list to scroll" complaint mechanically but didn't match the mock's actual design, which never
 * intended infinite scroll at all — paging 20 at a time is both the correct UX per the mock AND
 * still only renders ~20 rows of real DOM at a time, so there's no virtualization need left once
 * paging is done properly. {@code DemandReviewService.queue()} stays a plain in-memory
 * {@code List<ReviewQueueItemDto>}; paging/search/urgency grouping all happen here, client-side,
 * over that list — fine at this row count (hundreds, not thousands), same reasoning that list's own
 * Javadoc already gives for not needing a Pageable-aware counterpart.
 */
@Route(value = "review", layout = MainLayout.class)
@PageTitle("Review")
public class ReviewQueueView extends VerticalLayout {

    private static final int PAGE_SIZE = 20;

    private final DemandReviewService reviewService;
    private final List<ReviewQueueItemDto> queue;

    private final TextField search = new TextField();
    private final Div rowsContainer = new Div();
    private final Div paginationContainer = new Div();

    private List<ReviewQueueItemDto> filtered;
    private int page = 0;

    public ReviewQueueView(DemandReviewService reviewService) {
        this.reviewService = reviewService;
        this.queue = reviewService.queue();
        this.filtered = queue;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(buildHeader());
        add(buildToolbar());
        add(buildLegend());

        rowsContainer.addClassName("bm-rev-rows");
        add(rowsContainer);
        setFlexGrow(1, rowsContainer);

        add(buildNote());

        paginationContainer.setWidthFull();
        add(paginationContainer);

        render();
    }

    private Div buildHeader() {
        Span title = new Span("Demand Review");
        title.addClassName("bm-card-title-lg");

        int overdue = (int) queue.stream().filter(i -> i.ageingRank() == 0).count();
        int dueSoon = (int) queue.stream().filter(i -> i.ageingRank() == 1).count();
        int noCoverage = reviewService.noCoverageCount();

        Span totalCount = new Span(String.valueOf(queue.size()));
        totalCount.getStyle().set("font-weight", "700").set("color", "var(--bm-ink)").set("font-size", "15px");
        Span total = new Span(totalCount, new Span(" demand(s) need review"));
        Span overdueSpan = new Span(overdue + " overdue");
        overdueSpan.addClassName("bm-rev-stat-overdue");
        Span dueSoonSpan = new Span(dueSoon + " due this week");
        dueSoonSpan.addClassName("bm-rev-stat-duesoon");
        Span noCoverageSpan = new Span(noCoverage + " have zero candidate");
        noCoverageSpan.addClassName("bm-rev-stat-nocoverage");

        Div stats = new Div(total, overdueSpan, dueSoonSpan, noCoverageSpan);
        stats.addClassName("bm-rev-stats");

        Div header = new Div(title, stats);
        header.addClassName("bm-rev-header");
        return header;
    }

    private Div buildToolbar() {
        search.setPlaceholder("Search demand ID, customer, project…");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        search.addClassName("bm-rev-search");
        search.addValueChangeListener(e -> {
            applyFilter(e.getValue());
            page = 0;
            render();
        });

        Div toolbar = new Div(search);
        toolbar.addClassName("bm-rev-toolbar");
        return toolbar;
    }

    private Div buildLegend() {
        Div legend = new Div(
                legendItem("var(--bm-rev-overdue)", "Overdue"),
                legendItem("var(--bm-rev-duesoon)", "Due soon"),
                legendItem("var(--bm-rev-ontrack)", "On track")
        );
        legend.addClassName("bm-rev-legend");
        return legend;
    }

    private Div legendItem(String color, String label) {
        Span dot = new Span();
        dot.addClassName("bm-rev-legend-dot");
        dot.getStyle().set("background", color);
        Div item = new Div(dot, new Span(label));
        item.addClassName("bm-rev-legend-item");
        return item;
    }

    private Div buildNote() {
        Span dot = new Span();
        dot.addClassName("bm-rev-note-dot");
        Span text = new Span("Exhausted = every Strong/Good candidate already Proposed or Rejected, "
                + "but positions remain open — this is the live signal for proactive hiring, not a stuck queue item.");
        Div note = new Div(dot, text);
        note.addClassName("bm-rev-note");
        return note;
    }

    private void applyFilter(String term) {
        String t = term == null ? "" : term.trim().toLowerCase();
        if (t.isEmpty()) {
            filtered = queue;
            return;
        }
        filtered = queue.stream()
                .filter(i -> containsIgnoreCase(i.demandId(), t)
                        || containsIgnoreCase(i.customer(), t)
                        || containsIgnoreCase(i.projectName(), t))
                .toList();
    }

    private boolean containsIgnoreCase(String value, String term) {
        return value != null && value.toLowerCase().contains(term);
    }

    private void render() {
        rowsContainer.removeAll();

        if (filtered.isEmpty()) {
            Span empty = new Span(queue.isEmpty() ? "Nothing to review right now." : "No demands match your search.");
            empty.addClassName("bm-card-subtitle");
            rowsContainer.add(empty);
            paginationContainer.removeAll();
            return;
        }

        int totalPages = (int) Math.ceil(filtered.size() / (double) PAGE_SIZE);
        if (page >= totalPages) {
            page = totalPages - 1;
        }
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, filtered.size());

        for (ReviewQueueItemDto item : filtered.subList(from, to)) {
            rowsContainer.add(buildRow(item));
        }

        renderPagination(from, to, totalPages);
    }

    private void renderPagination(int from, int to, int totalPages) {
        paginationContainer.removeAll();

        Span caption = new Span("Showing " + (from + 1) + "–" + to + " of " + filtered.size()
                + ", sorted most urgent first");
        caption.addClassName("bm-rev-pagination-caption");

        Div pages = new Div();
        pages.addClassName("bm-rev-pagination-pages");

        pages.add(pageButton("Prev", page - 1, page > 0));
        // Compact page-number window around the current page, capped at 5 buttons, same idea as
        // GridPagingUtil's own pager — this list only needs the same treatment client-side here.
        int windowStart = Math.max(0, Math.min(page - 2, totalPages - 5));
        int windowEnd = Math.min(totalPages, windowStart + 5);
        for (int p = windowStart; p < windowEnd; p++) {
            Button btn = pageButton(String.valueOf(p + 1), p, true);
            if (p == page) {
                btn.addClassName("bm-rev-page-btn--active");
            }
            pages.add(btn);
        }
        pages.add(pageButton("Next", page + 1, page < totalPages - 1));

        Div pagination = new Div(caption, pages);
        pagination.addClassName("bm-rev-pagination");
        paginationContainer.removeAll();
        paginationContainer.add(pagination);
    }

    private Button pageButton(String label, int targetPage, boolean enabled) {
        Button btn = new Button(label);
        btn.addClassName("bm-rev-page-btn");
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        btn.setEnabled(enabled);
        btn.addClickListener(e -> {
            page = targetPage;
            render();
        });
        return btn;
    }

    private Component buildRow(ReviewQueueItemDto item) {
        boolean exhausted = item.lifecycleState() == DemandLifecycleState.EXHAUSTED;

        Div urgency = new Div();
        urgency.addClassName("bm-rev-urgency");
        Span urgencyLabel = new Span(urgencyLabelText(item, exhausted));
        urgencyLabel.addClassName("bm-rev-urgency-label");
        if (exhausted) {
            urgencyLabel.addClassName("bm-rev-urgency-label--exhausted");
        } else {
            String modifier = urgencyClass(item.ageingRank());
            if (!modifier.isEmpty()) {
                urgencyLabel.addClassName(modifier);
            }
        }
        Span demandId = new Span(item.demandId());
        demandId.addClassName("bm-rev-id");
        urgency.add(urgencyLabel, demandId);

        Div info = new Div();
        info.addClassName("bm-rev-info");
        Span infoTitle = new Span(classificationLine(item));
        infoTitle.addClassName("bm-rev-title");
        Span infoSub = new Span(customerLine(item));
        infoSub.addClassName("bm-rev-sub");
        info.add(infoTitle, infoSub);

        int positions = item.balancePositions() == null ? 0 : item.balancePositions();
        Div positionsBlock = new Div();
        positionsBlock.addClassName("bm-rev-positions");
        Span positionsMain = new Span(positions + (positions == 1 ? " position" : " positions"));
        positionsMain.addClassName("bm-rev-positions-main");
        Span positionsSub = new Span(item.approvedCount() + " of " + positions + " filled"
                + (exhausted ? " — no one left to decide on" : ""));
        positionsSub.addClassName(exhausted ? "bm-rev-positions-sub bm-rev-positions-sub--warn" : "bm-rev-positions-sub");
        positionsBlock.add(positionsMain, positionsSub);

        Div pills = new Div();
        pills.addClassName("bm-rev-pills");
        if (exhausted) {
            Span decided = new Span("All candidates decided");
            decided.addClassName("bm-rev-pill");
            decided.addClassName("bm-rev-pill-zero");
            pills.add(decided);
        } else {
            Span strong = new Span(item.strongCount() + " Strong");
            strong.addClassName("bm-rev-pill");
            strong.addClassName(item.strongCount() > 0 ? "bm-rev-pill-strong" : "bm-rev-pill-zero");
            Span good = new Span(item.goodCount() + " Good");
            good.addClassName("bm-rev-pill");
            good.addClassName(item.goodCount() > 0 ? "bm-rev-pill-good" : "bm-rev-pill-zero");
            pills.add(strong, good);
        }

        Span ctaText = new Span(exhausted ? "Flag for hiring" : "Review");
        Span arrow = new Span("→");
        Div cta = new Div(ctaText, arrow);
        cta.addClassName("bm-rev-cta");
        if (exhausted) {
            cta.addClassName("bm-rev-cta--muted");
        }

        // Text-plus-components constructor, same overload ReviewQueueView already relied on before
        // this rewrite ("Open →") — a RouterLink that also carries a navigation parameter has no
        // no-text overload, so an empty-string text node is unavoidable; it renders as nothing.
        RouterLink row = new RouterLink("", ReviewWorkspaceView.class, item.demandId());
        row.addClassName("bm-rev-row");
        row.addClassName(rowUrgencyClass(item, exhausted));
        row.add(urgency, info, positionsBlock, pills, cta);
        return row;
    }

    private String rowUrgencyClass(ReviewQueueItemDto item, boolean exhausted) {
        if (exhausted) {
            return "bm-rev-row--exhausted";
        }
        return switch (item.ageingRank()) {
            case 0 -> "bm-rev-row--overdue";
            case 1 -> "bm-rev-row--duesoon";
            default -> "bm-rev-row--ontrack";
        };
    }

    private String urgencyLabelText(ReviewQueueItemDto item, boolean exhausted) {
        if (exhausted) {
            return "● EXHAUSTED";
        }
        String raw = item.newAgeing() != null && !item.newAgeing().isBlank()
                ? item.newAgeing()
                : item.dueCategory();
        if (raw == null || raw.isBlank()) {
            raw = item.ageingRank() == 0 ? "Overdue" : item.ageingRank() == 1 ? "Due soon" : "On track";
        }
        return "● " + raw.toUpperCase();
    }

    private String urgencyClass(int rank) {
        return rank == 0 ? "bm-rev-urgency-label--overdue" : rank == 1 ? "bm-rev-urgency-label--duesoon" : "";
    }

    private String classificationLine(ReviewQueueItemDto item) {
        StringBuilder sb = new StringBuilder();
        sb.append(label(item));
        if (item.band() != null && !item.band().isBlank()) {
            sb.append(" · ").append(item.band());
        }
        if (item.location() != null && !item.location().isBlank()) {
            sb.append(" · ").append(item.location());
        }
        return sb.toString();
    }

    private String customerLine(ReviewQueueItemDto item) {
        String customer = item.customer() == null || item.customer().isBlank() ? "—" : item.customer();
        String project = item.projectName() == null || item.projectName().isBlank() ? null : item.projectName();
        return project == null ? customer : (customer + " — " + project);
    }

    private String label(ReviewQueueItemDto item) {
        if (item.subPersona() != null) {
            return item.persona() + " - " + item.subPersona();
        }
        return item.persona() == null ? (item.clusterNameRaw() == null ? "—" : item.clusterNameRaw()) : item.persona();
    }
}
