package com.dockmind.dock.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRunRepository extends JpaRepository<ScheduleRun, UUID> {

    Optional<ScheduleRun> findFirstByTenantIdAndScheduleDateOrderByComputedAtDesc(String tenantId, LocalDate date);

    long countByTenantIdAndScheduleDate(String tenantId, LocalDate date);
}
