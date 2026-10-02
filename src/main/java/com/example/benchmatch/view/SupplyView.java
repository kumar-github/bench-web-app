package com.example.benchmatch.view;

import com.example.benchmatch.api.SupplyQueryService;
import com.example.benchmatch.api.dto.SupplyDto;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Grid over supply_enriched (bench employees), reading through SupplyQueryService — the same filter logic the REST
 * API (SupplyController) uses. Filters are plain text fields matched the same way the API matches them
 * (case-insensitive for persona/subPersona/classificationStatus, exact for location), applied on demand via a
 * "Search" button rather than on every keystroke, since a full re-filter re-runs the in-memory scan over every
 * active row.
 * <p>
 * No longer the landing page as of the Dashboard view (2026-10-02) — {@code ""} now routes to
 * {@link DashboardView}.
 */
@Route(value = "supply", layout = MainLayout.class)
@PageTitle("Supply")
public class SupplyView extends VerticalLayout {

    private final SupplyQueryService queryService;

    private final TextField personaFilter = new TextField();
    private final TextField subPersonaFilter = new TextField();
    private final TextField locationFilter = new TextField();
    private final TextField classificationStatusFilter = new TextField();
    private final Checkbox activeOnly = new Checkbox("Active only", true);

    private final Grid<SupplyDto> grid = new Grid<>(SupplyDto.class, false);

    public SupplyView(SupplyQueryService queryService) {
        this.queryService = queryService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(buildFilterBar());
        add(buildGrid());
        setFlexGrow(1, grid);

        refresh();
    }

    private HorizontalLayout buildFilterBar() {
        personaFilter.setPlaceholder("Persona");
        subPersonaFilter.setPlaceholder("Sub-persona");
        locationFilter.setPlaceholder("Location");
        classificationStatusFilter.setPlaceholder("Classification status");

        var search = new com.vaadin.flow.component.button.Button("Search", e -> refresh());
        search.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);
        var clear = new com.vaadin.flow.component.button.Button("Clear", e -> {
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
        grid.addColumn(SupplyDto::employeeId).setHeader("Employee ID").setSortable(true).setAutoWidth(true);
        grid.addColumn(SupplyDto::employeeName).setHeader("Name").setSortable(true).setAutoWidth(true);
        grid.addColumn(SupplyDto::band).setHeader("Band").setSortable(true).setAutoWidth(true);
        grid.addColumn(SupplyDto::subBand).setHeader("Sub Band").setSortable(true).setAutoWidth(true);
        grid.addColumn(SupplyDto::location).setHeader("Location").setSortable(true).setAutoWidth(true);
        grid.addColumn(SupplyDto::persona).setHeader("Persona").setSortable(true).setAutoWidth(true);
        grid.addColumn(SupplyDto::subPersona).setHeader("Sub-persona").setSortable(true).setAutoWidth(true);
        grid.addColumn(SupplyDto::classificationStatus).setHeader("Classification").setSortable(true).setAutoWidth(true);
        grid.addColumn(dto -> dto.subCapabilityMismatchFlag() ? "⚠ Sub-capability mismatch" : "")
                .setHeader("Flag").setAutoWidth(true);
        grid.addColumn(SupplyDto::benchAgeingDays).setHeader("Bench Ageing (days)").setSortable(true).setAutoWidth(true);
        grid.addColumn(dto -> dto.isActive() ? "Active" : "Inactive").setHeader("Status").setAutoWidth(true);
        grid.setSizeFull();
        return grid;
    }

    private void refresh() {
        grid.setItems(queryService.list(
                blankToNull(personaFilter.getValue()),
                blankToNull(subPersonaFilter.getValue()),
                blankToNull(locationFilter.getValue()),
                blankToNull(classificationStatusFilter.getValue()),
                Boolean.TRUE.equals(activeOnly.getValue())
        ));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
