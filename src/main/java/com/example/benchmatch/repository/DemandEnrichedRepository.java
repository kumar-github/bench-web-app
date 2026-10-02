package com.example.benchmatch.repository;

import com.example.benchmatch.entity.DemandEnriched;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemandEnrichedRepository extends JpaRepository<DemandEnriched, String> {
    List<DemandEnriched> findByIsActiveTrue();

}
