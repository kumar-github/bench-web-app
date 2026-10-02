package com.example.benchmatch.view;

import com.example.benchmatch.entity.RefreshRun;
import com.example.benchmatch.repository.RefreshRunRepository;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.checkbox.Switch;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/**
 * Shared shell for every view — rebuilt pixel-for-pixel against the finalized design
 * ({@code Main.dc.html} in the approved design canvas): a fixed 232px dark sidebar, a 72px
 * white header, and a light content column. This intentionally does NOT use
 * {@code AppLayout}/{@code SideNav} — their shadow-DOM internals don't expose enough styling
 * hooks to hit this mock's exact colors/spacing, so the shell here is plain {@code Div}s/
 * {@code RouterLink}s styled from {@code styles.css} (".bm-*" classes), the same technique the
 * mock itself uses (plain elements, inline styles).
 * <p>
 * Nav items only link to routes that actually exist in this codebase today: Dashboard, Supply,
 * Demand, Upload, Shortlist. Review, Coverage Log and Admin have no view yet (no
 * demand_review_state/coverage/admin backend), so they render as the mock's disabled
 * "navitem-static" treatment with a SOON badge. "Shortlist" has no equivalent in the mock (it
 * predates this view) — it reuses a plain custom list icon, not one from the approved design.
 * <p>
 * The mock itself is a fixed light palette with a permanently-dark sidebar and no dark-mode
 * variant of its own. As of 2026-10-02 the dark/light Switch flips {@code Page.setColorScheme}
 * AND the shell chrome (sidebar/header/content background, see styles.css's
 * ":root[theme~='dark']" block) now has a real dark variant, on top of Aura's stock components
 * (Grid, Button, etc. on Supply/Demand/Upload/Shortlist) which already respond on their own.
 * The Dashboard route remains the one deliberate exception — its ".bm-dash" container pins
 * itself to the mock's literal light colors regardless of theme, since nobody has designed a
 * dark version of its KPI tiles/cards yet.
 */
public class MainLayout extends FlexLayout implements RouterLayout {

    private static final String COLOR_SCHEME_ATTRIBUTE = "benchmatch.colorScheme";
    private static final DateTimeFormatter LAST_REFRESHED_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US);

    // Icons copied verbatim from the approved Main.dc.html mock (stroke-width 1.8, 24x24
    // viewBox), with stroke="currentColor" so one markup works for both the active (white) and
    // inactive (#C7CCDA) states via CSS `color` on the parent — the mock hardcoded two separate
    // stroke colors per state instead, since it's static HTML.
    private static final String ICON_DASHBOARD =
            "<svg width='18' height='18' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8'>"
                    + "<rect x='3' y='3' width='8' height='8' rx='1.5'/><rect x='13' y='3' width='8' height='8' rx='1.5'/>"
                    + "<rect x='3' y='13' width='8' height='8' rx='1.5'/><rect x='13' y='13' width='8' height='8' rx='1.5'/></svg>";
    private static final String ICON_SUPPLY =
            "<svg width='18' height='18' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8'>"
                    + "<circle cx='9' cy='8' r='3.2'/><path d='M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6'/>"
                    + "<circle cx='17.5' cy='8.5' r='2.4'/><path d='M15.2 13.1c2.6.2 4.8 2.4 4.8 5.2'/></svg>";
    private static final String ICON_DEMAND =
            "<svg width='18' height='18' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8'>"
                    + "<rect x='3' y='7' width='18' height='12' rx='1.6'/><path d='M8 7V5.5A1.5 1.5 0 0 1 9.5 4h5A1.5 1.5 0 0 1 16 5.5V7'/></svg>";
    private static final String ICON_REVIEW =
            "<svg width='18' height='18' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8'>"
                    + "<rect x='3.5' y='3.5' width='17' height='17' rx='2.2'/><path d='M8 12.5l2.6 2.6L16.5 9'/></svg>";
    private static final String ICON_COVERAGE =
            "<svg width='18' height='18' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8'>"
                    + "<rect x='6' y='3.5' width='12' height='17' rx='1.6'/><path d='M9 3.5h6v3H9z'/><path d='M9 10.5h6M9 14h6'/></svg>";
    private static final String ICON_UPLOAD =
            "<svg width='18' height='18' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8'>"
                    + "<path d='M12 3v12M7 10l5 5 5-5'/><path d='M4 19h16'/></svg>";
    private static final String ICON_ADMIN =
            "<svg width='18' height='18' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8'>"
                    + "<circle cx='12' cy='12' r='3'/><path d='M19.4 13.5a7.6 7.6 0 0 0 0-3l1.8-1.3-1.5-2.6-2.1.6a7.5 7.5 0 0 0-2.6-1.5L14.6 3h-3L11 5.7a7.5 7.5 0 0 0-2.6 1.5l-2.1-.6-1.5 2.6 1.8 1.3a7.6 7.6 0 0 0 0 3l-1.8 1.3 1.5 2.6 2.1-.6c.75.68 1.63 1.19 2.6 1.5L11 21h3l.4-2.7c.97-.31 1.85-.82 2.6-1.5l2.1.6 1.5-2.6z'/></svg>";
    // Not in the mock — Shortlist has no design yet, so this is a plain placeholder icon.
    private static final String ICON_SHORTLIST =
            "<svg width='18' height='18' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8'>"
                    + "<path d='M7 6h12M7 12h12M7 18h12'/><circle cx='3.6' cy='6' r='0.9' fill='currentColor' stroke='none'/>"
                    + "<circle cx='3.6' cy='12' r='0.9' fill='currentColor' stroke='none'/><circle cx='3.6' cy='18' r='0.9' fill='currentColor' stroke='none'/></svg>";

    private final RefreshRunRepository refreshRunRepository;
    private final Div outlet = new Div();
    private final Span headerTitle = new Span();

    public MainLayout(RefreshRunRepository refreshRunRepository) {
        this.refreshRunRepository = refreshRunRepository;

        addClassName("bm-shell");
        setSizeFull();
        getStyle().set("min-height", "100vh");

        add(buildSidebar());
        add(buildContentColumn());

        addAttachListener(event -> {
            if (isDarkModeActive()) {
                event.getUI().getPage().setColorScheme(ColorScheme.Value.DARK);
            }
        });
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        outlet.getElement().removeAllChildren();
        if (content != null) {
            outlet.getElement().appendChild(content.getElement());
            headerTitle.setText(pageTitleOf(content));
            // Marks the shell itself with which route is showing, so styles.css's dark-mode
            // block can skip entirely for Dashboard — ".bm-header" is shared by every route
            // (it lives here, not in DashboardView), so without this marker it would flip dark
            // while ".bm-dash" below it stays pinned light, splitting the page in two. With the
            // marker, Dashboard stays fully light (header included) and every other route flips
            // fully dark (header included) — see ":not(.bm-dashboard-route)" in styles.css.
            setClassName("bm-dashboard-route", content instanceof DashboardView);
        }
    }

    private String pageTitleOf(HasElement content) {
        if (content instanceof Component component) {
            PageTitle annotation = component.getClass().getAnnotation(PageTitle.class);
            if (annotation != null) {
                return annotation.value();
            }
        }
        return "Bench Match";
    }

    private Div buildSidebar() {
        Div sidebar = new Div();
        sidebar.addClassName("bm-sidebar");

        Div logoBadge = new Div(new Span("B"));
        logoBadge.addClassName("bm-logo-badge");
        Span brand = new Span("Bench Match");
        brand.addClassName("bm-brand");
        Div logoRow = new Div(logoBadge, brand);
        logoRow.addClassName("bm-logo-row");
        sidebar.add(logoRow);

        sidebar.add(navLink("Dashboard", ICON_DASHBOARD, DashboardView.class));
        sidebar.add(navLink("Supply", ICON_SUPPLY, SupplyView.class));
        sidebar.add(navLink("Demand", ICON_DEMAND, DemandView.class));
        sidebar.add(soonItem("Review", ICON_REVIEW));
        sidebar.add(soonItem("Coverage Log", ICON_COVERAGE));
        sidebar.add(navLink("Upload", ICON_UPLOAD, UploadView.class));
        sidebar.add(navLink("Shortlist", ICON_SHORTLIST, ShortlistView.class));

        Div spacer = new Div();
        spacer.addClassName("bm-sidebar-spacer");
        sidebar.add(spacer);

        sidebar.add(soonItem("Admin", ICON_ADMIN));

        Span version = new Span("v1.0 · Phase 1");
        version.addClassName("bm-version");
        sidebar.add(version);

        return sidebar;
    }

    private RouterLink navLink(String label, String iconSvg, Class<? extends Component> target) {
        RouterLink link = new RouterLink();
        link.addClassName("bm-navitem");
        link.setRoute(target);
        link.add(new Html("<span class='bm-navicon'>" + iconSvg + "</span>"));
        Span text = new Span(label);
        text.addClassName("bm-navlabel");
        link.add(text);
        return link;
    }

    private Div soonItem(String label, String iconSvg) {
        Div item = new Div();
        item.addClassNames("bm-navitem", "bm-navitem-static");
        item.add(new Html("<span class='bm-navicon'>" + iconSvg + "</span>"));
        Span text = new Span(label);
        text.addClassNames("bm-navlabel", "bm-navlabel-flex");
        Span soon = new Span("SOON");
        soon.addClassName("soon-badge");
        item.add(text, soon);
        return item;
    }

    private Div buildContentColumn() {
        Div column = new Div();
        column.addClassName("bm-content-column");
        column.add(buildHeader());
        outlet.addClassName("bm-outlet");
        column.add(outlet);
        return column;
    }

    private Div buildHeader() {
        headerTitle.addClassName("bm-header-title");

        Div lastRefreshed = new Div();
        lastRefreshed.addClassName("bm-last-refreshed");
        lastRefreshed.add(new Span("Last refreshed "));
        Span refreshedValue = new Span(latestRefreshLabel());
        refreshedValue.addClassName("bm-last-refreshed-value");
        lastRefreshed.add(refreshedValue);

        // The mock's search bar has no backing search feature yet, so it's shown disabled rather
        // than a live-looking control that silently does nothing when used.
        TextField search = new TextField();
        search.setPlaceholder("Search employees, demands… (coming soon)");
        search.setEnabled(false);
        search.setWidth("260px");
        search.addClassName("bm-search");

        Switch darkModeSwitch = new Switch();
        darkModeSwitch.getElement().setProperty("title", "Dark mode (affects everything except Dashboard)");
        darkModeSwitch.setValue(isDarkModeActive());
        darkModeSwitch.addValueChangeListener(event -> applyColorScheme(event.getValue()));

        Div avatar = new Div(new Span("RK"));
        avatar.addClassName("bm-avatar");

        Div right = new Div(lastRefreshed, search, darkModeSwitch, avatar);
        right.addClassName("bm-header-right");

        Div header = new Div(headerTitle, right);
        header.addClassName("bm-header");
        return header;
    }

    private String latestRefreshLabel() {
        OffsetDateTime latest = refreshRunRepository.findAll().stream()
                .map(RefreshRun::getFinishedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return latest == null ? "—" : latest.format(LAST_REFRESHED_FORMAT);
    }

    private void applyColorScheme(boolean dark) {
        // ColorScheme itself is an annotation (@ColorScheme, for a fixed app-wide scheme on
        // AppShellConfigurator) — the actual LIGHT/DARK enum constants live one level deeper, on
        // its nested ColorScheme.Value, which is also what Page#setColorScheme(...) takes.
        // Confirmed against Vaadin's own source (ColorScheme.java), not just summarized docs,
        // after this exact mistake (ColorScheme.DARK, which doesn't exist) shipped uncompiled.
        ColorScheme.Value scheme = dark ? ColorScheme.Value.DARK : ColorScheme.Value.LIGHT;
        getUI().ifPresent(ui -> ui.getPage().setColorScheme(scheme));
        VaadinSession.getCurrent().setAttribute(COLOR_SCHEME_ATTRIBUTE, dark);
    }

    private boolean isDarkModeActive() {
        Object stored = VaadinSession.getCurrent().getAttribute(COLOR_SCHEME_ATTRIBUTE);
        return Boolean.TRUE.equals(stored);
    }
}
