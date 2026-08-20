package com.flowforge.infrastructure.web.observability;

import com.flowforge.infrastructure.observability.ObservabilitySnapshotFacade;
import com.flowforge.infrastructure.observability.ObservabilitySnapshotResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ObservabilityController {

    private final ObservabilitySnapshotFacade facade;

    public ObservabilityController(ObservabilitySnapshotFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/api/observability")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ObservabilitySnapshotResponse get() {
        return facade.snapshot();
    }
}
