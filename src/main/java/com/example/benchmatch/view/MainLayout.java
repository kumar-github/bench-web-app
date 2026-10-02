package com.example.benchmatch.view;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.router.RouterLayout;

/**
 * Shared shell for every view: a top bar with the app name and a Tabs strip of RouterLinks to the three routes
 * that exist so far (Supply, Demand, Upload). Deliberately minimal — no drawer content, no per-role navigation —
 * since there's no auth/roles yet (see README's "What is NOT done yet").
 */
public class MainLayout extends AppLayout implements RouterLayout {

    public MainLayout() {
        H1 title = new H1("Demand & Supply Mapping");
        title.getStyle().set("font-size", "1.25rem").set("margin", "0");

        Tabs tabs = new Tabs(
                new Tab(new RouterLink("Supply", SupplyView.class)),
                new Tab(new RouterLink("Demand", DemandView.class)),
                new Tab(new RouterLink("Upload", UploadView.class)),
                new Tab(new RouterLink("Shortlist", ShortlistView.class))
        );
        tabs.setOrientation(Tabs.Orientation.HORIZONTAL);

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), title);
        header.setWidthFull();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);

        addToNavbar(header);
        addToNavbar(true, tabs);
    }
}
