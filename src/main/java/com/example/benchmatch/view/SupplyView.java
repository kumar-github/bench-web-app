package com.example.benchmatch.view;

import com.example.benchmatch.api.SupplyQueryService;
import com.example.benchmatch.api.dto.SupplyDto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.dataview.GridLazyDataView;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Sort;

/**
 * Grid over supply_enriched (bench employees), reading through SupplyQueryService — the same filter logic the REST API
 * (SupplyController) uses. Filters are plain text fields matched the same way the API matches them (case-insensitive
 * for persona/subPersona/classificationStatus, exact for location), applied on demand via a "Search" button rather than
 * on every keystroke, since a full re-filter re-runs the query.
 * <p>
 * No longer the landing page as of the Dashboard view (2026-10-02) — {@code ""} now routes to {@link DashboardView}.
 * <p>
 * Lazy/paged as of 2026-10-03 (reported the same day as "the demand and supply grid showing all the records seems to be
 * very slow"): {@code grid.setItems(fetchCallback, countCallback)} asks SupplyQueryService for one page of rows at a
 * time (default page size 50 — see {@code grid.setPageSize} below) instead of the old
 * {@code grid.setItems(queryService.list(...))}, which pulled every row in supply_enriched into memory and serialized
 * all of it to the browser on every load and every Search/Clear click, regardless of how many rows were actually
 * visible. The filter fields and Search/Clear buttons work the same as before from the user's side; only the
 * data-loading strategy changed. See SupplyQueryService.page()'s Javadoc for the query-level half of this fix.
 */
@Route(value = "supply", layout = MainLayout.class)
@PageTitle("Supply")
public class SupplyView extends VerticalLayout {

    // Falls back to this order whenever no grid column is actively sorted, so paging stays over a stable row
    // order — see GridPagingUtil.toPageable's Javadoc for why an unsorted page sequence would otherwise be unsafe.
    private static final Sort DEFAULT_SORT = Sort.by("employeeId");

    private final SupplyQueryService queryService;

    private final TextField personaFilter = new TextField();
    private final TextField subPersonaFilter = new TextField();
    private final TextField locationFilter = new TextField();
    private final TextField classificationStatusFilter = new TextField();
    private final Checkbox activeOnly = new Checkbox("Active only", true);

    private final Grid<SupplyDto> grid = new Grid<>(SupplyDto.class, false);
    private GridLazyDataView<SupplyDto> dataView;

    public SupplyView(SupplyQueryService queryService) {
        this.queryService = queryService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(buildFilterBar());
        add(buildGrid());
        setFlexGrow(1, grid);

        setupDataProvider();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private HorizontalLayout buildFilterBar() {
        personaFilter.setPlaceholder("Persona");
        subPersonaFilter.setPlaceholder("Sub-persona");
        locationFilter.setPlaceholder("Location");
        classificationStatusFilter.setPlaceholder("Classification status");

        var search = new com.vaadin.flow.component.button.Button("Search", e -> refresh());
        search.addThemeVariants(ButtonVariant.PRIMARY);
        var clear = new Button("Clear", e -> {
            personaFilter.clear();
            subPersonaFilter.clear();
            locationFilter.clear();
            classificationStatusFilter.clear();
            activeOnly.setValue(true);
            refresh();
        });

        HorizontalLayout bar = new HorizontalLayout(
                personaFilter, subPersonaFilter, locationFilter, classificationStatusFilter,
                activeOnly, search, clear
        );
        bar.setWidthFull();
        bar.setAlignItems(HorizontalLayout.Alignment.BASELINE);
        return bar;
    }

    private Grid<SupplyDto> buildGrid() {
        // flexGrow(0) (the default alongside setAutoWidth(true)) packs every column to its exact content width with
        // no slack, which is what made this grid feel "too narrow/thin" — the text-heavy columns below now take a
        // share of whatever width is left over instead of hugging their minimum content width; the short/code
        // columns (ID, Band, Sub Band, Flag, Bench Ageing, Status) stay at flexGrow(0) since giving them extra room
        // would just add empty padding around a couple of characters. See styles.css's `.bm-grid` rule for the
        // accompanying row-height/cell-padding bump (2026-10-07) — this class name is what scopes that rule to just
        // this grid (and DemandView's), not Shortlist's.
        grid.addClassName("bm-grid");
        grid.addColumn(SupplyDto::employeeId).setHeader("Employee ID").setSortable(true).setSortProperty("employeeId").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(SupplyDto::employeeName).setHeader("Name").setSortable(true).setSortProperty("employeeName").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(SupplyDto::band).setHeader("Band").setSortable(true).setSortProperty("band").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(SupplyDto::subBand).setHeader("Sub Band").setSortable(true).setSortProperty("subBand").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(SupplyDto::location).setHeader("Location").setSortable(true).setSortProperty("location").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(SupplyDto::persona).setHeader("Persona").setSortable(true).setSortProperty("persona.name").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(SupplyDto::subPersona).setHeader("Sub-persona").setSortable(true).setSortProperty("subPersona.name").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(SupplyDto::classificationStatus).setHeader("Classification").setSortable(true).setSortProperty("classificationStatus").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(dto -> dto.subCapabilityMismatchFlag() ? "⚠ Sub-capability mismatch" : "")
                .setHeader("Flag").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(SupplyDto::benchAgeingDays).setHeader("Bench Ageing (days)").setSortable(true).setSortProperty("benchAgeingDays").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(dto -> dto.isActive() ? "Active" : "Inactive").setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        grid.addThemeVariants(GridVariant.ROW_STRIPES);
        grid.setSizeFull();
        grid.setPageSize(50);
        return grid;
    }

    /**
     * Wires the grid to a lazy data view, backed by {@code SupplyQueryService.page()}/{@code count()}, rather than
     * loading a {@code List} once. Both callbacks read the filter fields' CURRENT values at call time (same as the old
     * {@code refresh()} did), so {@link #refresh()} only needs to tell the data view to re-fetch — it doesn't rebuild
     * the data provider itself.
     */
    private void setupDataProvider() {
        dataView = grid.setItems(
                query -> queryService.page(
                        blankToNull(personaFilter.getValue()),
                        blankToNull(subPersonaFilter.getValue()),
                        blankToNull(locationFilter.getValue()),
                        blankToNull(classificationStatusFilter.getValue()),
                        Boolean.TRUE.equals(activeOnly.getValue()),
                        GridPagingUtil.toPageable(query, DEFAULT_SORT)
                ).getContent().stream(),
                query -> (int) queryService.count(
                        blankToNull(personaFilter.getValue()),
                        blankToNull(subPersonaFilter.getValue()),
                        blankToNull(locationFilter.getValue()),
                        blankToNull(classificationStatusFilter.getValue()),
                        Boolean.TRUE.equals(activeOnly.getValue())
                )
        );
    }

    private void refresh() {
        dataView.refreshAll();
    }
}
