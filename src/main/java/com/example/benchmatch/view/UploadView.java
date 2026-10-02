package com.example.benchmatch.view;

import com.example.benchmatch.entity.RefreshRun;
import com.example.benchmatch.refresh.RefreshService;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Runs a supply or demand refresh from an uploaded Excel file. Calls {@link RefreshService} directly, in-process —
 * NOT the REST multipart endpoints in RefreshController (see that class's Javadoc for why: this is a Vaadin view,
 * so per this app's own architecture it goes straight to the service layer rather than round-tripping through
 * HTTP to itself).
 * <p>
 * Each Upload widget accepts one .xlsx file, buffers it in memory (small files — the real AFD-Supply/Demand
 * exports are a few hundred rows), stages it to a temp file (RefreshService/ExcelSheetReader need a real Path, not
 * a stream), runs the matching refresh, shows the resulting row counts, and cleans the temp file up.
 */
@Route(value = "upload", layout = MainLayout.class)
@PageTitle("Upload")
public class UploadView extends VerticalLayout {

    private final RefreshService refreshService;

    public UploadView(RefreshService refreshService) {
        this.refreshService = refreshService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(buildUploadSection("Supply", "Upload the AFD-Supply.xlsx export to refresh supply_enriched.", true));
        add(buildUploadSection("Demand", "Upload the Demand.xlsx export to refresh demand_enriched.", false));
    }

    private VerticalLayout buildUploadSection(String title, String helpText, boolean isSupply) {
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".xlsx");
        upload.setMaxFiles(1);
        upload.setDropAllowed(true);

        Div result = new Div();

        upload.addSucceededListener(event -> {
            String fileName = event.getFileName();
            Path staged = null;
            try {
                staged = stage(buffer.getInputStream(), fileName);
                RefreshRun run = isSupply ? refreshService.refreshSupply(staged) : refreshService.refreshDemand(staged);
                showResult(result, run);
                Notification success = Notification.show((isSupply ? "Supply" : "Demand") + " refresh finished: " + run.getStatus());
                success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception e) {
                result.removeAll();
                result.add(new Text("Refresh failed: " + e.getMessage()));
                Notification failure = Notification.show("Refresh failed — see details below.");
                failure.addThemeVariants(NotificationVariant.LUMO_ERROR);
            } finally {
                deleteQuietly(staged);
            }
        });

        upload.addFileRejectedListener(event -> {
            Notification n = Notification.show(event.getErrorMessage());
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        VerticalLayout section = new VerticalLayout(new H3(title), new Div(new Text(helpText)), upload, result);
        section.setPadding(false);
        section.setSpacing(true);
        return section;
    }

    private void showResult(Div result, RefreshRun run) {
        result.removeAll();
        HorizontalLayout counts = new HorizontalLayout(
                new Text("Status: " + run.getStatus()),
                new Text("Rows in: " + run.getRowsIn()),
                new Text("New: " + run.getRowsNew()),
                new Text("Changed: " + run.getRowsChanged()),
                new Text("Flagged: " + run.getRowsFlagged())
        );
        counts.setSpacing(true);
        result.add(counts);
        if (run.getErrorMessage() != null) {
            result.add(new Div(new Text("Error: " + run.getErrorMessage())));
        }
    }

    private Path stage(InputStream in, String originalFileName) throws IOException {
        String suffix = (originalFileName != null && originalFileName.contains("."))
                ? originalFileName.substring(originalFileName.lastIndexOf('.'))
                : ".xlsx";
        Path staged = Files.createTempFile("benchmatch-upload-", suffix);
        try (in) {
            Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
        }
        return staged;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }
}
