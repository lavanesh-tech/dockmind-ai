package com.dockmind.recovery.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoredPlanRepository extends JpaRepository<StoredPlan, UUID> {

    List<StoredPlan> findByDisruptionIdOrderByRank(UUID disruptionId);
}
