package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.workflow.model.ConfigPublication;
import com.flowforge.domain.workflow.model.DraftArtifactType;
import com.flowforge.domain.workflow.model.PendingDraft;
import com.flowforge.domain.workflow.model.PublicationMode;
import com.flowforge.domain.workflow.port.in.ManageDraftsUseCase;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §82 (D6) — écran « Brouillons à publier » : liste (provenance, auteur, écart avec la version en ligne), publication par
 * un humain (mode {@code MANUAL}), abandon, journal des publications. Les mêmes cas d'usage que l'outil MCP.
 */
@RestController
@RequestMapping("/api/admin/drafts")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
class DraftsController {

    private final ManageDraftsUseCase manageDrafts;
    private final AccessContextFactory accessContextFactory;

    DraftsController(ManageDraftsUseCase manageDrafts, AccessContextFactory accessContextFactory) {
        this.manageDrafts = manageDrafts;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping
    List<PendingDraft> list(Authentication authentication) {
        return manageDrafts.listPending(accessContextFactory.user(authentication));
    }

    @GetMapping("/publications")
    List<ConfigPublication> publications(
            @RequestParam(required = false, defaultValue = "50") int limit, Authentication authentication) {
        return manageDrafts.recentPublications(accessContextFactory.user(authentication), limit);
    }

    @PostMapping("/{type}/{key}/publish")
    ConfigPublication publish(
            @PathVariable DraftArtifactType type,
            @PathVariable String key,
            @RequestBody PublishDraftRequest request,
            Authentication authentication) {
        return manageDrafts.publish(
                accessContextFactory.user(authentication), type, key, request.expectedUpdatedAt(), PublicationMode.MANUAL);
    }

    @DeleteMapping("/{type}/{key}")
    ResponseEntity<Void> discard(
            @PathVariable DraftArtifactType type,
            @PathVariable String key,
            @RequestParam(required = false) Instant expectedUpdatedAt,
            Authentication authentication) {
        manageDrafts.discard(accessContextFactory.user(authentication), type, key, expectedUpdatedAt);
        return ResponseEntity.noContent().build();
    }
}

record PublishDraftRequest(Instant expectedUpdatedAt) {}
