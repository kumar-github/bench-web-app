package com.example.benchmatch.view;

import com.example.benchmatch.api.DemandQueryService;
import com.example.benchmatch.api.dto.DemandDto;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Grid over demand_enriched (client requirements), same shape and same filter-through-a-shared-service pattern as
 * SupplyView — see that class's Javadoc.
 */
@Route(value = "demand", layout = MainLayout.class)
@PageTitle("Demand")
public class DemandView extends VerticalLayout {

    private final DemandQueryService queryService;

    private final TextField personaFilter = new TextField();
    private final TextField subPersonaFilter = new TextField();
    private final TextField locationFilter = new TextField();
    private final TextField classificationStatusFilter = new TextField();
    private final Checkbox activeOnly = new Checkbox("Active only", true);

    private final Grid<DemandDto> grid = new Grid<>(DemandDto.class, false);

    public DemandView(DemandQueryService queryService) {
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

        Button search = new Button("Search", e -> refresh());
        search.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button clear = new Button("Clear", e -> {
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

    private Grid<DemandDto> buildGrid() {
        grid.addColumn(DemandDto::demandId).setHeader("Demand ID").setSortable(true).setAutoWidth(true);
        grid.addColumn(DemandDto::clusterNameRaw).setHeader("Cluster").setSortable(true).setAutoWidth(true);
        grid.addColumn(DemandDto::location).setHeader("Location").setSortable(true).setAutoWidth(true);
        grid.addColumn(DemandDto::band).setHeader("Band").setSortable(true).setAutoWidth(true);
        grid.addColumn(DemandDto::persona).setHeader("Persona").setSortable(true).setAutoWidth(true);
        grid.addColumn(DemandDto::subPersona).setHeader("Sub-persona").setSortable(true).setAutoWidth(true);
        grid.addColumn(DemandDto::classificationStatus).setHeader("Classification").setSortable(true).setAutoWidth(true);
        grid.addColumn(dto -> dto.masMappingMismatchFlag() ? "⚠ MAS mapping mismatch" : "")
                .setHeader("Flag").setAutoWidth(true);
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
