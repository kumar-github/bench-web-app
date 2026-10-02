package com.example.benchmatch.repository;

import com.example.benchmatch.entity.RefreshRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshRunRepository extends JpaRepository<RefreshRun, Long> {
}
