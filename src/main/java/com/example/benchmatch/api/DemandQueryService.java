package com.example.benchmatch.api;

import com.example.benchmatch.api.dto.DemandDto;
import com.example.benchmatch.entity.DemandEnriched;
import com.example.benchmatch.repository.DemandEnrichedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Shared read/filter logic over demand_enriched — same rationale and same shape as SupplyQueryService.
 */
@Service
public class DemandQueryService {

    private final DemandEnrichedRepository repository;

    public DemandQueryService(DemandEnrichedRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DemandDto> list(String persona, String subPersona, String location, String classificationStatus, boolean activeOnly) {
        List<DemandEnriched> rows = activeOnly ? repository.findByIsActiveTrue() : repository.findAll();
        return rows.stream()
                .filter(e -> persona == null || (e.getPersona() != null && persona.equalsIgnoreCase(e.getPersona().getName())))
                .filter(e -> subPersona == null || (e.getSubPersona() != null && subPersona.equalsIgnoreCase(e.getSubPersona().getName())))
                .filter(e -> location == null || Objects.equals(location, e.getLocation()))
                .filter(e -> classificationStatus == null || classificationStatus.equalsIgnoreCase(e.getClassificationStatus()))
                .map(DemandDto::from)
                .toList();
    }
}
