package com.example.benchmatch.api;

import com.example.benchmatch.api.dto.SupplyDto;
import com.example.benchmatch.entity.SupplyEnriched;
import com.example.benchmatch.repository.SupplyEnrichedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Shared read/filter logic over supply_enriched, extracted out of SupplyController (2026-09-30, task #18) so the new
 * Vaadin SupplyView can call the exact same filtering the REST API uses, in-process, rather than duplicating it or
 * routing through HTTP to itself — per this app's architecture (Vaadin views and REST controllers both call the
 * service layer; see BenchMatchApplication's Javadoc).
 * <p>
 * {@code @Transactional(readOnly = true)} because SupplyDto.from() touches the lazy persona/subPersona associations
 * — without a transaction open here, that throws LazyInitializationException in the caller.
 */
@Service
public class SupplyQueryService {

    private final SupplyEnrichedRepository repository;

    public SupplyQueryService(SupplyEnrichedRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SupplyDto> list(String persona, String subPersona, String location, String classificationStatus, boolean activeOnly) {
        List<SupplyEnriched> rows = activeOnly ? repository.findByIsActiveTrue() : repository.findAll();
        return rows.stream()
                .filter(e -> persona == null || (e.getPersona() != null && persona.equalsIgnoreCase(e.getPersona().getName())))
                .filter(e -> subPersona == null || (e.getSubPersona() != null && subPersona.equalsIgnoreCase(e.getSubPersona().getName())))
                .filter(e -> location == null || Objects.equals(location, e.getLocation()))
                .filter(e -> classificationStatus == null || classificationStatus.equalsIgnoreCase(e.getClassificationStatus()))
                .map(SupplyDto::from)
                .toList();
    }
}
