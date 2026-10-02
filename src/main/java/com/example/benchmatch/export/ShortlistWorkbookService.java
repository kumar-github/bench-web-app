package com.example.benchmatch.export;

import com.example.benchmatch.entity.DemandEnriched;
import com.example.benchmatch.entity.SupplyEnriched;
import com.example.benchmatch.matching.AssessmentResult;
import com.example.benchmatch.matching.ClassificationResult;
import com.example.benchmatch.matching.MatchRow;
import com.example.benchmatch.matching.MatchingService;
import com.example.benchmatch.matching.SignalResult;
import com.example.benchmatch.refresh.RefreshLogic;
import com.example.benchmatch.repository.DemandEnrichedRepository;
import com.example.benchmatch.repository.SupplyEnrichedRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Java port of write_shortlist_workbook.py + build_matches.py's main() — builds the "Phase 1 Shortlist" workbook
 * (task #19). Runs a fresh matching pass at export time (same candidate scope and per-employee cap as
 * MatchingRunService.runMatching()) rather than reading match_candidates, because match_candidates only persists
 * each signal's quality code and the one-liner (see MatchingRunService/MatchCandidate) — not the full
 * collapsed/expanded text this workbook shows in the "— summary"/"— raw detail" column pairs. Recomputing here keeps
 * this export self-contained and always current, at the cost of duplicating MatchingRunService's small
 * candidate-scope filter (candidateDemandsFor below) — see that method's Javadoc.
 * <p>
 * Formatting (colors, fonts, column widths, freeze panes, employee/DATA-ISSUE block layout) mirrors the Python
 * source as closely as Apache POI allows. The "Read Me" sheet keeps the Python version's structure (scope, tier
 * definitions, capping rule, classification approach, known limitations) but recomputes every number live from the
 * current database rather than hard-coding the original Phase-1 run's snapshot figures (e.g. "16 employees have
 * zero Strong/Good match") — those were true of one specific historical run, not a general fact worth freezing into
 * every future export.
 */
@Service
public class ShortlistWorkbookService {

    private static final String FONT_NAME = "Arial";
    private static final Map<String, Integer> TIER_ORDER = Map.of("Strong", 0, "Good", 1, "Weak", 2, "Excluded", 3);

    private static final String[][] COLUMNS = {
            {"Overall Signal", "14"}, {"Employee Band", "12"}, {"Employee Location", "14"},
            {"Job Req ID", "11"}, {"Customer", "16"}, {"Project", "20"},
            {"Demand Persona / Sub-persona", "24"}, {"Demand Band", "10"}, {"Demand Location", "14"},
            {"Balance Positions", "10"}, {"Due Category", "16"}, {"Ageing Bucket", "12"},
            {"Skill — summary", "46"}, {"Band — summary", "20"}, {"Location — summary", "16"},
            {"Assessment — summary", "24"}, {"Skill — raw detail", "46"}, {"Band — raw detail", "30"},
            {"Location — raw detail", "40"}, {"Assessment — raw detail", "28"}, {"Reasoning (one-line)", "60"},
            {"MAS Mapping check", "30"}, {"Frontend Anchor check", "40"},
            // Added 2026-10-01 — mirrors write_shortlist_workbook.py's "MAS Mapping cross-check"
            // column exactly, same position (right after Frontend Anchor check, before Additional
            // Request). See ShortlistRow.masMappingCrossCheckNote's javadoc for what it checks.
            {"MAS Mapping cross-check", "40"},
            {"Additional Request (manual review only)", "50"}
    };

    // 0-indexed column positions of the two review-flag columns that only get a value (and bold amber styling) when
    // actually flagged — mirrors write_shortlist_workbook.py's `elif c in (22, 23) and v` (its 1-indexed columns).
    private static final int COL_MAS_MAPPING_CHECK = 21;
    private static final int COL_FRONTEND_ANCHOR_CHECK = 22;
    private static final int COL_MAS_MAPPING_CROSS_CHECK = 23; // added 2026-10-01

    private final SupplyEnrichedRepository supplyRepo;
    private final DemandEnrichedRepository demandRepo;
    private final MatchingService matchingService;

    public ShortlistWorkbookService(SupplyEnrichedRepository supplyRepo, DemandEnrichedRepository demandRepo,
                                     MatchingService matchingService) {
        this.supplyRepo = supplyRepo;
        this.demandRepo = demandRepo;
        this.matchingService = matchingService;
    }

    @Transactional(readOnly = true)
    public byte[] generate() {
        List<SupplyEnriched> allSupply = supplyRepo.findByIsActiveTrue();
        List<SupplyEnriched> classifiedSupply = allSupply.stream().filter(s -> s.getPersona() != null).toList();
        List<SupplyEnriched> unclassifiedSupply = allSupply.stream()
                .filter(s -> s.getPersona() == null)
                .sorted(Comparator.comparing(SupplyEnriched::getEmployeeName, Comparator.nullsLast(String::compareTo)))
                .toList();

        List<DemandEnriched> classifiedDemand = demandRepo.findByIsActiveTrue().stream()
                .filter(d -> d.getPersona() != null)
                .toList();
        Map<String, List<DemandEnriched>> demandByPersona = classifiedDemand.stream()
                .collect(Collectors.groupingBy(d -> d.getPersona().getName()));

        // employeeId -> capped, tier/ageing/demandId-sorted rows for that employee
        Map<Long, List<ShortlistRow>> byEmployee = new java.util.LinkedHashMap<>();
        for (SupplyEnriched emp : classifiedSupply) {
            List<DemandEnriched> candidates = candidateDemandsFor(emp, demandByPersona);
            if (candidates.isEmpty()) {
                continue;
            }
            List<ShortlistRow> rows = buildRows(emp, candidates);
            if (rows.isEmpty()) {
                continue;
            }
            List<ShortlistRow> kept = capAndSort(rows);
            if (!kept.isEmpty()) {
                byEmployee.put(emp.getEmployeeId(), kept);
            }
        }

        // Employees ordered by name, same as ordered_emp_codes in the Python source.
        List<SupplyEnriched> orderedEmployees = classifiedSupply.stream()
                .filter(e -> byEmployee.containsKey(e.getEmployeeId()))
                .sorted(Comparator.comparing(SupplyEnriched::getEmployeeName, Comparator.nullsLast(String::compareTo)))
                .toList();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles styles = new Styles(wb);
            writeShortlistSheet(wb, styles, orderedEmployees, byEmployee, unclassifiedSupply);
            writeReadMeSheet(wb, styles, orderedEmployees, byEmployee, classifiedSupply.size() + unclassifiedSupply.size(),
                    unclassifiedSupply.size(), classifiedDemand);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build shortlist workbook", e);
        }
    }

    /**
     * Same broad-persona-family candidate scope as MatchingRunService.candidateDemandsFor() — duplicated rather than
     * shared to keep this export independently readable and to avoid widening MatchingRunService's method
     * visibility for a single caller; see this class's own Javadoc for why a fresh pass is computed here at all.
     */
    private List<DemandEnriched> candidateDemandsFor(SupplyEnriched emp, Map<String, List<DemandEnriched>> demandByPersona) {
        String persona = emp.getPersona() == null ? null : emp.getPersona().getName();
        if (persona == null) {
            return List.of();
        }
        if ("Fullstack Java".equals(persona) || "Fullstack .NET".equals(persona)) {
            return demandByPersona.getOrDefault(persona, List.of());
        }
        if ("Frontend".equals(persona)) {
            return demandByPersona.getOrDefault("Frontend", List.of());
        }
        return List.of();
    }

    private List<ShortlistRow> buildRows(SupplyEnriched emp, List<DemandEnriched> candidates) {
        ClassificationResult empClassification = toClassificationResult(emp);
        List<ShortlistRow> rows = new ArrayList<>();
        for (DemandEnriched dem : candidates) {
            ClassificationResult demClassification = toClassificationResult(dem);
            SignalResult skill = matchingService.skillSignal(empClassification, demClassification);
            if ("no_relation".equals(skill.quality())) {
                continue;
            }
            SignalResult band = matchingService.bandSignal(emp.getSubBand(), dem.getBand());
            SignalResult location = matchingService.locationSignal(emp.getLocation(), dem.getLocation(), null);
            AssessmentResult assessment = matchingService.assessmentSignal(emp.getRating(), emp.getScore());
            String tier = matchingService.overallTier(skill, band, location);
            int ageingRank = matchingService.ageingRank(dem.getDueCategory());

            List<String> bits = new ArrayList<>();
            bits.add("core".equals(skill.quality()) ? leadIn(skill.collapsed()) : skill.collapsed());
            bits.add(band.collapsed());
            bits.add(location.collapsed());
            if (!"Not yet assessed".equals(assessment.collapsed())) {
                bits.add(assessment.collapsed());
            }
            String oneLiner = String.join("; ", bits);

            // Reviewer-only flags — checked against the engine-derived persona, never used to filter/score.
            // Mirrors build_matches.py's mas_mapping_note/frontend_anchor_note/additional_request passthrough.
            String masMappingNote = RefreshLogic.masMappingNote(dem.getMasMappingRaw(), demClassification.persona());
            String frontendAnchorNote = RefreshLogic.frontendAnchorNote(
                    dem.getFinalSkillClusterRaw(), demClassification.persona(), demClassification.frameworkConfirmed());
            String additionalRequest = dem.getAdditionalRequestRaw();
            // Pairing-level cross-check (2026-10-01) — distinct from masMappingNote above, which
            // only ever looks at the demand's own raw value vs. its engine-derived persona. This
            // compares the demand's raw MAS Mapping against the MATCHED employee's own raw MAS
            // Mapping. See MatchingService.masMappingCrossCheck()'s javadoc for the full reasoning.
            String masMappingCrossCheckNote = matchingService.masMappingCrossCheckNote(
                    emp.getMasMappingRaw(), dem.getMasMappingRaw());

            rows.add(new ShortlistRow(
                    emp.getEmployeeId(), emp.getEmployeeName(),
                    empClassification.persona(), empClassification.subPersona(), emp.getCompleteness(),
                    emp.getSubBand(), emp.getLocation(), emp.getPrimeNv(), emp.getAfdStatus(), emp.getBenchAgeingDays(),
                    dem.getDemandId(), dem.getCustomer(), dem.getProjectName(),
                    demClassification.persona(), demClassification.subPersona(), dem.getBand(), dem.getLocation(),
                    dem.getBalancePositions(), dem.getDueCategory(), dem.getNewAgeing(),
                    tier, ageingRank, skill.quality(),
                    skill.collapsed(), skill.expanded(), band.collapsed(), band.expanded(),
                    location.collapsed(), location.expanded(), assessment.collapsed(), assessment.expanded(),
                    oneLiner,
                    masMappingNote, frontendAnchorNote, masMappingCrossCheckNote, additionalRequest
            ));
        }
        return rows;
    }

    private String leadIn(String collapsed) {
        int idx = collapsed.indexOf(" — ");
        return idx < 0 ? collapsed : collapsed.substring(0, idx);
    }

    private ClassificationResult toClassificationResult(SupplyEnriched emp) {
        return new ClassificationResult(
                emp.getPersona() == null ? null : emp.getPersona().getName(),
                emp.getSubPersona() == null ? null : emp.getSubPersona().getName(),
                emp.getNamedAccessories(), emp.getCompleteness(), emp.getFrameworkConfirmed(), null
        );
    }

    private ClassificationResult toClassificationResult(DemandEnriched dem) {
        return new ClassificationResult(
                dem.getPersona() == null ? null : dem.getPersona().getName(),
                dem.getSubPersona() == null ? null : dem.getSubPersona().getName(),
                dem.getNamedAccessories(), null, dem.getFrameworkConfirmed(), dem.getClassificationNote()
        );
    }

    /**
     * capEmployeeRows() operates on MatchRow (matching.MatchRow — the minimal shape it needs to sort/cap); this maps
     * ShortlistRow -> MatchRow, caps, then re-joins back to the richer ShortlistRow by demandId (safe: employeeId is
     * fixed for the whole call, so demandId alone is a unique key within one employee's rows) — same approach
     * MatchingRunService uses to go the other way (MatchRow -> MatchCandidate). Final sort matches
     * write_shortlist_workbook.py's build(): tier order, then ageing_rank, then job_req_id (demandId) — capEmployeeRows
     * groups by tier but does not itself sort each tier by ageing_rank/demandId the way the workbook wants.
     */
    private List<ShortlistRow> capAndSort(List<ShortlistRow> rows) {
        List<MatchRow> matchRows = rows.stream()
                .map(r -> new MatchRow(r.employeeId(), r.demandId(), r.overallTier(), r.skillQuality(),
                        r.ageingRank(), r.balancePositions(), r.oneLiner(), null, null, null))
                .toList();
        List<MatchRow> capped = matchingService.capEmployeeRows(matchRows);
        Set<String> keptDemandIds = capped.stream().map(MatchRow::demandId).collect(Collectors.toCollection(HashSet::new));

        Comparator<ShortlistRow> byTierThenUrgencyThenDemandId = Comparator
                .<ShortlistRow>comparingInt(r -> TIER_ORDER.getOrDefault(r.overallTier(), 9))
                .thenComparingInt(ShortlistRow::ageingRank)
                .thenComparing(ShortlistRow::demandId, Comparator.nullsLast(String::compareTo));

        return rows.stream()
                .filter(r -> keptDemandIds.contains(r.demandId()))
                .sorted(byTierThenUrgencyThenDemandId)
                .toList();
    }

    private String combinePersona(String persona, String sub) {
        return (sub == null || sub.isBlank()) ? persona : persona + " / " + sub;
    }

    // ------------------------------------------------------------------
    // Sheet 1: Phase 1 Shortlist
    // ------------------------------------------------------------------

    private void writeShortlistSheet(XSSFWorkbook wb, Styles s, List<SupplyEnriched> orderedEmployees,
                                      Map<Long, List<ShortlistRow>> byEmployee, List<SupplyEnriched> unclassifiedSupply) {
        Sheet ws = wb.createSheet("Phase 1 Shortlist");
        ws.setDisplayGridlines(false);

        Row r1 = ws.createRow(0);
        Cell title = r1.createCell(0);
        title.setCellValue("Demand & Supply Mapping — Matching Engine Output");
        title.setCellStyle(s.title);
        ws.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        Row r2 = ws.createRow(1);
        Cell subtitle = r2.createCell(0);
        subtitle.setCellValue("Grouped by employee; within each employee, rows are sorted Strong > Good > Weak > Excluded "
                + "(see 'Read Me' tab for methodology, the row-cap applied per employee, and open/unconfirmed items).");
        subtitle.setCellStyle(s.subtitle);
        ws.addMergedRegion(new CellRangeAddress(1, 1, 0, COLUMNS.length - 1));

        int headerRowIdx = 3;
        Row headerRow = ws.createRow(headerRowIdx);
        headerRow.setHeightInPoints(30f);
        for (int c = 0; c < COLUMNS.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(COLUMNS[c][0]);
            cell.setCellStyle(s.header);
            ws.setColumnWidth(c, Integer.parseInt(COLUMNS[c][1]) * 256);
        }
        ws.createFreezePane(0, headerRowIdx + 1);

        int row = headerRowIdx + 1;
        for (SupplyEnriched emp : orderedEmployees) {
            List<ShortlistRow> empRows = byEmployee.get(emp.getEmployeeId());
            row = writeEmployeeBlock(ws, s, row, emp, empRows);
        }
        for (SupplyEnriched emp : unclassifiedSupply) {
            row = writeDataIssueBlock(ws, s, row, emp);
        }
    }

    private int writeEmployeeBlock(Sheet ws, Styles s, int row, SupplyEnriched emp, List<ShortlistRow> empRows) {
        boolean hasStrongOrGood = empRows.stream().anyMatch(r -> "Strong".equals(r.overallTier()) || "Good".equals(r.overallTier()));
        String flag = hasStrongOrGood ? "" : "  ⚠ NO STRONG/GOOD MATCH IN SCOPE — ";

        Row headerRow = ws.createRow(row);
        headerRow.setHeightInPoints(20f);
        Cell cell = headerRow.createCell(0);
        ShortlistRow first = empRows.get(0);
        String subCapabilityNote = RefreshLogic.subCapabilityNote(emp.getSubCapabilityRaw(), first.employeePersona());
        String scSuffix = (subCapabilityNote == null || subCapabilityNote.isBlank())
                ? "" : "  |  ⚠ Sub-Capability check: " + subCapabilityNote;
        cell.setCellValue(flag + emp.getEmployeeName() + "  (Emp Code " + emp.getEmployeeId() + ")  —  "
                + combinePersona(first.employeePersona(), first.employeeSubPersona())
                + "  |  Completeness: " + nz(emp.getCompleteness())
                + "  |  Band: " + nz(emp.getSubBand())
                + "  |  Location: " + nz(emp.getLocation()) + " (" + nz(emp.getPrimeNv()) + ")"
                + "  |  Status: " + nz(emp.getAfdStatus())
                + "  |  Bench ageing: " + fmtNum(emp.getBenchAgeingDays()) + " days"
                + scSuffix);
        cell.setCellStyle(hasStrongOrGood ? s.empHeader : s.empHeaderNoMatch);
        ws.addMergedRegion(new CellRangeAddress(row, row, 0, COLUMNS.length - 1));
        row++;

        for (ShortlistRow r : empRows) {
            Row dataRow = ws.createRow(row);
            String tier = r.overallTier();
            Object[] values = {
                    tier, r.employeeBand(), r.employeeLocation(), r.demandId(), r.customer(), r.project(),
                    combinePersona(r.demandPersona(), r.demandSubPersona()), r.demandBand(), r.demandLocation(),
                    fmtNum(r.balancePositions()), r.dueCategory(), r.ageingBucket(),
                    r.skillCollapsed(), r.bandCollapsed(), r.locationCollapsed(), r.assessmentCollapsed(),
                    r.skillExpanded(), r.bandExpanded(), r.locationExpanded(), r.assessmentExpanded(), r.oneLiner(),
                    nz(r.masMappingNote()), nz(r.frontendAnchorNote()), nz(r.masMappingCrossCheckNote()),
                    nz(r.additionalRequest())
            };
            for (int c = 0; c < values.length; c++) {
                Cell dc = dataRow.createCell(c);
                setValue(dc, values[c]);
                if (c == 0) {
                    dc.setCellStyle(s.tierCell(tier));
                } else if ((c == COL_MAS_MAPPING_CHECK || c == COL_FRONTEND_ANCHOR_CHECK || c == COL_MAS_MAPPING_CROSS_CHECK)
                        && values[c] != null && !values[c].toString().isEmpty()) {
                    // Only has a value when actually flagged — bold amber, same treatment as the tier-discount text.
                    dc.setCellStyle(s.reviewFlagCell());
                } else {
                    // 0-indexed wrap columns: Skill/Location/Band/Location raw detail, Reasoning, Additional Request
                    // — mirrors Python's 1-indexed `c in (13, 17, 18, 19, 21, 25)`.
                    boolean wrap = (c == 12 || c == 16 || c == 17 || c == 18 || c == 20 || c == 24);
                    dc.setCellStyle(s.bodyCell("Excluded".equals(tier), wrap));
                }
            }
            row++;
        }
        return row;
    }

    private int writeDataIssueBlock(Sheet ws, Styles s, int row, SupplyEnriched emp) {
        Row headerRow = ws.createRow(row);
        Cell cell = headerRow.createCell(0);
        cell.setCellValue(emp.getEmployeeName() + "  (Emp Code " + emp.getEmployeeId() + ")  —  "
                + "Band: " + nz(emp.getSubBand()) + "  |  Location: " + nz(emp.getLocation())
                + "  |  Status: " + nz(emp.getAfdStatus())
                + "  |  Bench ageing: " + fmtNum(emp.getBenchAgeingDays()) + " days");
        cell.setCellStyle(s.empHeader);
        ws.addMergedRegion(new CellRangeAddress(row, row, 0, COLUMNS.length - 1));
        row++;

        Row dataRow = ws.createRow(row);
        Cell tierCell = dataRow.createCell(0);
        tierCell.setCellValue("DATA ISSUE");
        tierCell.setCellStyle(s.tierCell("DATA ISSUE"));
        for (int c = 1; c < COLUMNS.length - 1; c++) {
            Cell blank = dataRow.createCell(c);
            blank.setCellStyle(s.bodyCell(false, false));
        }
        Cell reasonCell = dataRow.createCell(COLUMNS.length - 1);
        reasonCell.setCellValue(nz(emp.getClassificationNote()));
        reasonCell.setCellStyle(s.bodyCell(false, true));
        row++;
        return row;
    }

    // ------------------------------------------------------------------
    // Sheet 2: Read Me
    // ------------------------------------------------------------------

    private void writeReadMeSheet(XSSFWorkbook wb, Styles s, List<SupplyEnriched> orderedEmployees,
                                   Map<Long, List<ShortlistRow>> byEmployee, int totalPhase1Supply,
                                   int unclassifiedCount, List<DemandEnriched> classifiedDemand) {
        Sheet rm = wb.createSheet("Read Me");
        rm.setColumnWidth(0, 100 * 256);
        int[] r = {0};

        addLine(rm, s.title, r, "Demand & Supply Mapping — Matching Engine: Methodology & Coverage Notes");
        r[0]++;
        addLine(rm, s.bold, r, "Scope (this export)");
        long employeesWithRows = byEmployee.size();
        addLine(rm, s.body, r, totalPhase1Supply + " Phase 1 bench employees on file; " + employeesWithRows
                + " classified into a Phase 1 persona with at least one candidate shortlist row, "
                + unclassifiedCount + " flagged as DATA ISSUE rows at the bottom of the Phase 1 Shortlist tab "
                + "(unclassified or outside the supported band ladder — see each row's Reasoning column for why).");
        r[0]++;

        addLine(rm, s.bold, r, "Overall Signal tiers");
        addLine(rm, s.body, r, "Strong — same persona (and sub-persona, for Frontend), core skills present, band exact, same city.");
        addLine(rm, s.body, r, "Good — persona match but exactly one thing discounted: one band above the ask, a different city, "
                + "or a named-accessory gap (demand asks for a named extra the employee's profile doesn't show).");
        addLine(rm, s.body, r, "Weak — a cross-persona discount, the framework-unconfirmed bucket, a weak Node.js-only MERN "
                + "signal, OR a persona match with two or more things discounted at once.");
        addLine(rm, s.body, r, "Excluded — band two-or-more levels off (either direction) or wrong sub-persona. Shown greyed "
                + "out, capped to the 3 closest near-misses per employee, for transparency — never blended into the ranked list.");
        r[0]++;

        addLine(rm, s.bold, r, "Per-employee row cap");
        addLine(rm, s.body, r, "All Strong rows kept; top 3 Good rows kept (by demand urgency, i.e. ageing rank then open "
                + "positions); Weak rows shown ONLY for an employee with zero Strong and zero Good matches (capped at 10 in "
                + "that case); the 3 closest near-miss Excluded rows kept per employee regardless. Strong and Good are both "
                + "\"propose freely\" tiers — Good is never hidden just because Strong exists.");
        r[0]++;

        long noStrongOrGood = orderedEmployees.stream()
                .filter(e -> byEmployee.get(e.getEmployeeId()).stream()
                        .noneMatch(row -> "Strong".equals(row.overallTier()) || "Good".equals(row.overallTier())))
                .count();
        addLine(rm, s.bold, r, "Employees with zero Strong and zero Good match, this export");
        addLine(rm, s.body, r, noStrongOrGood + " of " + employeesWithRows + " employees with at least one candidate row have "
                + "no Strong/Good match right now — flagged with a ⚠ NO STRONG/GOOD MATCH IN SCOPE banner in the main sheet.");
        r[0]++;

        addLine(rm, s.bold, r, "Classification approach");
        addLine(rm, s.body, r, "Supply's Skill Cluster is a closed, curated Phase 1 value set, classified via an exact-match "
                + "lookup table. Demand's Final Skill Cluster is open free text, classified via a token/rule parser "
                + "implementing the anchor/accessory/persona rules (framework implies language, named-vs-unnamed accessory "
                + "tiers, anchor precedence). Both sides resolve to the same shared persona label before matching — never "
                + "compared as raw text. No fuzzy matching: an unrecognized value is excluded and logged, never guessed.");
        r[0]++;

        addLine(rm, s.bold, r, "Reviewer flags (check-only — never filter or score)");
        addLine(rm, s.body, r, "MAS Mapping check: a demand row's own raw MAS Mapping vs. the persona the engine derived "
                + "for that same row. Frontend Anchor check: a frontend framework named alongside a bare Java/.NET anchor "
                + "with no backend-framework confirmation. MAS Mapping cross-check (added 2026-10-01): the demand's raw "
                + "MAS Mapping vs. the MATCHED employee's own raw MAS Mapping — two independently filled-in source labels "
                + "that can disagree with each other even when each individually agrees with its own side's derived "
                + "persona, a case the first two checks can't see since neither looks across the pairing. All three are "
                + "reviewer-facing only; none of them route or exclude a candidate.");
        r[0]++;

        long totalPos = classifiedDemand.stream().mapToLong(d -> d.getBalancePositions() == null ? 0 : d.getBalancePositions()).sum();
        addLine(rm, s.bold, r, "Demand classification coverage");
        addLine(rm, s.body, r, totalPos + " open positions across " + classifiedDemand.size()
                + " classified, active demand rows feed this export's matching pass. Demand rows with no recognized "
                + "persona generate no shortlist row for any employee — they are not force-fit.");
        r[0]++;

        addLine(rm, s.bold, r, "Known limitations / not yet resolved");
        for (String item : List.of(
                "Pan India location handling — not modeled; location is always scored same-city/different-city.",
                "Band-vs-location discount sizing (is one band above worse than a different city?) — both currently "
                        + "collapse to one 'discount' each in the Good tier; the relative weighting is unverified.",
                "Sub-bands outside the defined E1.1-E3.2 ladder show a 'Band ladder position unclear' note rather than "
                        + "being silently scored.",
                "Additional Request free text and Must-have/Nice-to-have tagging are not yet factored into matching."
        )) {
            addLine(rm, s.body, r, "  •  " + item);
        }
    }

    private void addLine(Sheet rm, CellStyle style, int[] r, String text) {
        Row row = rm.createRow(r[0]);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(style);
        r[0]++;
    }

    private static void setValue(Cell cell, Object v) {
        if (v == null) {
            cell.setBlank();
        } else if (v instanceof Integer i) {
            cell.setCellValue(i);
        } else if (v instanceof Double d) {
            cell.setCellValue(d);
        } else {
            cell.setCellValue(v.toString());
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String fmtNum(Integer v) {
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * Cell styles, built once per workbook and reused (POI penalizes creating many distinct XSSFCellStyle instances).
     * Colors/fonts mirror write_shortlist_workbook.py's module-level constants.
     */
    private static final class Styles {
        final CellStyle title;
        final CellStyle subtitle;
        final CellStyle header;
        final CellStyle empHeader;
        final CellStyle empHeaderNoMatch;
        final CellStyle body;
        final CellStyle bold;
        private final Map<String, CellStyle> tierStyles = new java.util.HashMap<>();
        private final CellStyle bodyPlain;
        private final CellStyle bodyPlainWrap;
        private final CellStyle bodyExcluded;
        private final CellStyle bodyExcludedWrap;
        private final CellStyle reviewFlag;

        Styles(XSSFWorkbook wb) {
            Font titleFont = font(wb, 14, true, false, "1F4E78");
            title = base(wb);
            title.setFont(titleFont);

            Font subtitleFont = font(wb, 10, false, true, "595959");
            subtitle = base(wb);
            subtitle.setFont(subtitleFont);
            subtitle.setWrapText(true);

            Font headerFont = font(wb, 10, true, false, "FFFFFF");
            header = base(wb);
            header.setFont(headerFont);
            fill(header, "1F4E78");
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);

            Font empHeaderFont = font(wb, 11, true, false, "1F4E78");
            empHeader = base(wb);
            empHeader.setFont(empHeaderFont);
            fill(empHeader, "D9E1F2");
            empHeader.setVerticalAlignment(VerticalAlignment.CENTER);
            empHeader.setIndention((short) 1);

            empHeaderNoMatch = base(wb);
            empHeaderNoMatch.setFont(empHeaderFont);
            fill(empHeaderNoMatch, "FFD966");
            empHeaderNoMatch.setVerticalAlignment(VerticalAlignment.CENTER);
            empHeaderNoMatch.setIndention((short) 1);

            body = base(wb);
            body.setFont(font(wb, 10, false, false, "000000"));
            body.setWrapText(true);

            bold = base(wb);
            bold.setFont(font(wb, 10, true, false, "000000"));

            Font bodyFont = font(wb, 10, false, false, "000000");
            Font excludedFont = font(wb, 9, false, true, "808080");

            bodyPlain = bodyStyle(wb, bodyFont, false);
            bodyPlainWrap = bodyStyle(wb, bodyFont, true);
            bodyExcluded = bodyStyle(wb, excludedFont, false);
            bodyExcludedWrap = bodyStyle(wb, excludedFont, true);
            // MAS Mapping check / Frontend Anchor check — bold amber when actually flagged, mirrors Python's
            // `Font(name=FONT_NAME, size=10, bold=True, color='9C6500')` (same amber as the "Good" tier).
            reviewFlag = bodyStyle(wb, font(wb, 10, true, false, "9C6500"), true);

            String[][] tiers = {
                    {"Strong", "C6EFCE", "006100", "false"}, {"Good", "FFEB9C", "9C6500", "false"},
                    {"Weak", "FCE4D6", "974706", "false"}, {"Excluded", "D9D9D9", "7F7F7F", "true"},
                    {"DATA ISSUE", "FF7C80", "FFFFFF", "false"}
            };
            for (String[] t : tiers) {
                CellStyle style = base(wb);
                fill(style, t[1]);
                Font f = font(wb, 10, true, Boolean.parseBoolean(t[3]), t[2]);
                style.setFont(f);
                style.setAlignment(HorizontalAlignment.CENTER);
                style.setVerticalAlignment(VerticalAlignment.CENTER);
                tierStyles.put(t[0], style);
            }
        }

        CellStyle tierCell(String tier) {
            return tierStyles.getOrDefault(tier, bodyPlain);
        }

        CellStyle bodyCell(boolean excludedRow, boolean wrap) {
            if (excludedRow) {
                return wrap ? bodyExcludedWrap : bodyExcluded;
            }
            return wrap ? bodyPlainWrap : bodyPlain;
        }

        CellStyle reviewFlagCell() {
            return reviewFlag;
        }

        private CellStyle bodyStyle(XSSFWorkbook wb, Font f, boolean wrap) {
            CellStyle style = base(wb);
            style.setFont(f);
            style.setVerticalAlignment(VerticalAlignment.TOP);
            style.setWrapText(wrap);
            return style;
        }

        private CellStyle base(XSSFWorkbook wb) {
            org.apache.poi.xssf.usermodel.XSSFCellStyle style = wb.createCellStyle();
            BorderStyle thin = BorderStyle.THIN;
            style.setBorderTop(thin);
            style.setBorderBottom(thin);
            style.setBorderLeft(thin);
            style.setBorderRight(thin);
            XSSFColor borderColor = new XSSFColor(Color.decode("#BFBFBF"), null);
            style.setTopBorderColor(borderColor);
            style.setBottomBorderColor(borderColor);
            style.setLeftBorderColor(borderColor);
            style.setRightBorderColor(borderColor);
            return style;
        }

        private void fill(CellStyle style, String hex) {
            if (style instanceof org.apache.poi.xssf.usermodel.XSSFCellStyle xssfStyle) {
                xssfStyle.setFillForegroundColor(new XSSFColor(Color.decode("#" + hex), null));
                xssfStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
        }

        private Font font(XSSFWorkbook wb, int size, boolean bold, boolean italic, String hex) {
            Font f = wb.createFont();
            f.setFontName(FONT_NAME);
            f.setFontHeightInPoints((short) size);
            f.setBold(bold);
            f.setItalic(italic);
            if (f instanceof org.apache.poi.xssf.usermodel.XSSFFont xssfFont) {
                xssfFont.setColor(new XSSFColor(Color.decode("#" + hex), null));
            }
            return f;
        }
    }
}
