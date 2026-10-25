package com.dockmind.recovery.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisruptionRepository extends JpaRepository<Disruption, UUID> {

    Optional<Disruption> findByIdAndTenantId(UUID id, String tenantId);

    List<Disruption> findTop50ByTenantIdOrderByReportedAtDesc(String tenantId);
}
