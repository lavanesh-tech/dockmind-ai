package com.dockmind.dock.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DockRepository extends JpaRepository<Dock, UUID> {

    List<Dock> findByTenantIdOrderByCode(String tenantId);

    boolean existsByTenantIdAndCode(String tenantId, String code);
}
