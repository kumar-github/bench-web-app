package com.example.benchmatch.repository;

import com.example.benchmatch.entity.DemandReviewState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DemandReviewStateRepository extends JpaRepository<DemandReviewState, String> {
}
