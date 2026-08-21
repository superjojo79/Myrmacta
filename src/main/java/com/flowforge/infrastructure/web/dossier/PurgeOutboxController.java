package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.PurgeOutboxOrigin;
import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.model.PurgeOutboxStatus;
import com.flowforge.domain.dossier.port.in.EnqueueDossierPurgeUseCase;
import com.flowforge.domain.dossier.port.in.ListPurgeOutboxUseCase;
import com.flowforge.infrastructure.web.ListSorts;
import com.flowforge.infrastructure.web.PageQueryParser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

record PurgeOutboxItemResponse(
        UUID id,
        UUID dossierId,
        PurgeOutboxStatus status,
        PurgeOutboxOrigin origin,
        String eventType,
        Instant createdAt,
        String lastError) {

    static PurgeOutboxItemResponse from(PurgeOutboxRecord record) {
        return new PurgeOutboxItemResponse(
                record.id(),
                record.dossierId(),
                record.status(),
                record.origin(),
                record.eventType(),
                record.createdAt(),
                record.lastError());
    }
}

record PurgeOutboxPageResponse(
        List<PurgeOutboxItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        ListPurgeOutboxUseCase.Counts counts) {

    static PurgeOutboxPageResponse from(ListPurgeOutboxUseCase.Page page) {
        return new PurgeOutboxPageResponse(
                page.result().content().stream().map(PurgeOutboxItemResponse::from).toList(),
                page.result().page(),
                page.result().size(),
                page.result().totalElements(),
                page.result().totalPages(),
                page.counts());
    }
}

@RestController
public class PurgeOutboxController {

    private final ListPurgeOutboxUseCase listPurgeOutboxUseCase;
    private final EnqueueDossierPurgeUseCase enqueueDossierPurgeUseCase;
    private final AccessContextFactory accessContextFactory;

    public PurgeOutboxController(
            ListPurgeOutboxUseCase listPurgeOutboxUseCase,
            EnqueueDossierPurgeUseCase enqueueDossierPurgeUseCase,
            AccessContextFactory accessContextFactory) {
        this.listPurgeOutboxUseCase = listPurgeOutboxUseCase;
        this.enqueueDossierPurgeUseCase = enqueueDossierPurgeUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping("/api/purge-outbox")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public PurgeOutboxPageResponse list(
            @RequestParam(required = false) PurgeOutboxStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        return PurgeOutboxPageResponse.from(listPurgeOutboxUseCase.list(
                actor, status, PageQueryParser.parse(page, size, sort, ListSorts.PURGE_OUTBOX)));
    }

    @PostMapping("/api/dossiers/{id}/purge-requests")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<Void> enqueue(@PathVariable UUID id, Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        EnqueueDossierPurgeUseCase.Result result = enqueueDossierPurgeUseCase.enqueue(id, actor);
        if (result == EnqueueDossierPurgeUseCase.Result.CREATED) {
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }
        return ResponseEntity.ok().build();
    }
}
