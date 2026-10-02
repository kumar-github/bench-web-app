package com.example.benchmatch.view;

import com.example.benchmatch.export.ShortlistWorkbookService;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

/**
 * "Generate" button for the shortlist workbook (task #19) — the Vaadin-side counterpart to
 * ShortlistExportController's REST download. Calls ShortlistWorkbookService directly, in-process, same as
 * UploadView calls RefreshService — see that class's Javadoc for the architecture rationale.
 * <p>
 * The download itself is an Anchor with a StreamResource rather than a plain Button, because that's what makes the
 * browser actually save a file: the resource's content supplier (ShortlistWorkbookService.generate()) only runs when
 * the link is clicked, so nothing is computed just by opening this page.
 */
@Route(value = "shortlist", layout = MainLayout.class)
@PageTitle("Shortlist")
public class ShortlistView extends VerticalLayout {

    public ShortlistView(ShortlistWorkbookService workbookService) {
        setPadding(true);
        setSpacing(true);

        H3 title = new H3("Matching Shortlist Workbook");
        Div helpText = new Div(new Text(
                "Runs a fresh matching pass over active, classified supply and demand and builds the Phase 1 "
                        + "Shortlist workbook — one employee-grouped sheet plus a Read Me / methodology sheet. "
                        + "Always reflects current data, not a previous matching run."));

        StreamResource resource = new StreamResource(
                "phase1-matching-shortlist-" + LocalDate.now() + ".xlsx",
                () -> new ByteArrayInputStream(workbookService.generate())
        );
        resource.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        Anchor download = new Anchor(resource, "");
        download.getElement().setAttribute("download", true);
        Button generateButton = new Button("Generate & Download");
        generateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        download.add(generateButton);

        add(title, helpText, download);
    }
}
