package com.example.benchmatch.export;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Download endpoint for the shortlist workbook (task #19) — the REST-side counterpart to the Vaadin "Generate" button
 * in ShortlistView, for any consumer outside the browser. Both call ShortlistWorkbookService directly; this
 * controller only adds the HTTP download headers.
 */
@RestController
public class ShortlistExportController {

    private final ShortlistWorkbookService workbookService;

    public ShortlistExportController(ShortlistWorkbookService workbookService) {
        this.workbookService = workbookService;
    }

    @GetMapping("/api/shortlist/workbook")
    public ResponseEntity<byte[]> downloadShortlistWorkbook() {
        byte[] bytes = workbookService.generate();
        String fileName = "phase1-matching-shortlist-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
