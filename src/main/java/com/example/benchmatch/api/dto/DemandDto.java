package com.example.benchmatch.api.dto;

import com.example.benchmatch.entity.DemandEnriched;
import com.example.benchmatch.refresh.RefreshLogic;

import java.time.OffsetDateTime;
import java.util.List;

public record DemandDto(
        String demandId,
        String clusterNameRaw,
        String finalSkillClusterRaw,
        String masMappingRaw,
        String additionalRequestRaw,
        String location,
        String band,
        String persona,
        String subPersona,
        List<String> namedAccessories,
        Boolean frameworkConfirmed,
        String classificationStatus,
        boolean masMappingMismatchFlag,
        String masMappingNote,
        boolean isActive,
        OffsetDateTime lastRefreshedAt
) {
    public static DemandDto from(DemandEnriched e) {
        String personaName = e.getPersona() == null ? null : e.getPersona().getName();
        return new DemandDto(
                e.getDemandId(),
                e.getClusterNameRaw(),
                e.getFinalSkillClusterRaw(),
                e.getMasMappingRaw(),
                e.getAdditionalRequestRaw(),
                e.getLocation(),
                e.getBand(),
                personaName,
                e.getSubPersona() == null ? null : e.getSubPersona().getName(),
                e.getNamedAccessories(),
                e.getFrameworkConfirmed(),
                e.getClassificationStatus(),
                e.isMasMappingMismatchFlag(),
                RefreshLogic.masMappingNote(e.getMasMappingRaw(), personaName),
                e.isActive(),
                e.getLastRefreshedAt()
        );
    }
}
