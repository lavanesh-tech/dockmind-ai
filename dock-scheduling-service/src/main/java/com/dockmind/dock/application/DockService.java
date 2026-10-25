package com.dockmind.dock.application;

import com.dockmind.common.web.ApiException;
import com.dockmind.dock.domain.Dock;
import com.dockmind.dock.domain.DockRepository;
import com.dockmind.scheduling.model.Capability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class DockService {

    private final DockRepository docks;

    public DockService(DockRepository docks) {
        this.docks = docks;
    }

    public Dock register(String tenantId, String code, Set<Capability> capabilities, LocalTime opensAt, LocalTime closesAt) {
        if (docks.existsByTenantIdAndCode(tenantId, code)) {
            throw ApiException.conflict("A dock with code " + code + " already exists.");
        }
        try {
            return docks.saveAndFlush(new Dock(tenantId, code, capabilities, opensAt, closesAt));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Dock> list(String tenantId) {
        return docks.findByTenantIdOrderByCode(tenantId);
    }
}
