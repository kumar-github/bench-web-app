package com.example.benchmatch.repository;

import com.example.benchmatch.entity.DecisionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DecisionHistoryRepository extends JpaRepository<DecisionHistory, Integer> {
    List<DecisionHistory> findByDecisionIdOrderByChangedAtDesc(Integer decisionId);
}
