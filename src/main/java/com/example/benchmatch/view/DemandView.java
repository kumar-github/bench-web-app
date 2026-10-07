package com.example.benchmatch.view;

import com.example.benchmatch.api.DemandQueryService;
import com.example.benchmatch.api.dto.DemandDto;
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
 * Grid over demand_enriched (client requirements), same shape and same filter-through-a-shared-service pattern as
 * SupplyView — see that class's Javadoc, including the 2026-10-03 move to a lazy/paged data view
 * ({@code DemandQueryService.page()}/{@code count()}) for the same "very slow with every record on screen" reason.
 */
@Route(value = "demand", layout = MainLayout.class)
@PageTitle("Demand")
public class DemandView extends VerticalLayout {

    // See SupplyView.DEFAULT_SORT's Javadoc — same reason, applies here too.
    private static final Sort DEFAULT_SORT = Sort.by("demandId");

    private final DemandQueryService queryService;

    private final TextField personaFilter = new TextField();
    private final TextField subPersonaFilter = new TextField();
    private final TextField locationFilter = new TextField();
    private final TextField classificationStatusFilter = new TextField();
    private final Checkbox activeOnly = new Checkbox("Active only", true);

    private final Grid<DemandDto> grid = new Grid<>(DemandDto.class, false);
    private GridLazyDataView<DemandDto> dataView;

    public DemandView(DemandQueryService queryService) {
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

        Button search = new Button("Search", e -> refresh());
        search.addThemeVariants(ButtonVariant.PRIMARY);
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
        // See SupplyView.buildGrid's Javadoc — same fix, same reasoning: text-heavy columns get a share of
        // leftover width instead of hugging their minimum content width; short/code columns stay at flexGrow(0).
        grid.addClassName("bm-grid");
        grid.addColumn(DemandDto::demandId).setHeader("Demand ID").setSortable(true).setSortProperty("demandId").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(DemandDto::clusterNameRaw).setHeader("Cluster").setSortable(true).setSortProperty("clusterNameRaw").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(DemandDto::location).setHeader("Location").setSortable(true).setSortProperty("location").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(DemandDto::band).setHeader("Band").setSortable(true).setSortProperty("band").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(DemandDto::persona).setHeader("Persona").setSortable(true).setSortProperty("persona.name").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(DemandDto::subPersona).setHeader("Sub-persona").setSortable(true).setSortProperty("subPersona.name").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(DemandDto::classificationStatus).setHeader("Classification").setSortable(true).setSortProperty("classificationStatus").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(dto -> dto.masMappingMismatchFlag() ? "⚠ MAS mapping mismatch" : "")
                .setHeader("Flag").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(dto -> dto.isActive() ? "Active" : "Inactive").setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        grid.addThemeVariants(GridVariant.ROW_STRIPES);
        grid.setSizeFull();
        grid.setPageSize(50);
        return grid;
    }

    /**
     * See SupplyView.setupDataProvider's Javadoc — same pattern.
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
