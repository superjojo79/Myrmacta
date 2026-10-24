package com.flowforge.infrastructure.web.portal;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatus;
import com.flowforge.domain.workflow.portal.port.in.ManagePortalLinkUseCase;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §68 US-5 — liens portail d'un dossier, vus par l'instructeur (chaîne BFF authentifiée,
 * CSRF). Lecture avec VIEW, envoi/renvoi/révocation avec EDIT sur le type (H6, tranché le
 * 2026-09-24). Un envoi dont l'adresse reste invalide répond {@code 409} <em>avec</em> le lien
 * {@code NOT_SENT} créé (état persisté, raison lisible par l'UI).
 */
@RestController
@RequestMapping("/api/dossiers/{dossierId}/portal-links")
class PortalLinkAdminController {

    private final ManagePortalLinkUseCase useCase;
    private final AccessContextFactory accessContextFactory;

    PortalLinkAdminController(ManagePortalLinkUseCase useCase, AccessContextFactory accessContextFactory) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @GetMapping
    PortalLinkResponse.ListResponse list(@PathVariable UUID dossierId, Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        ManagePortalLinkUseCase.PortalLinks links = useCase.list(dossierId, actor);
        return new PortalLinkResponse.ListResponse(
                links.canManage(),
                links.links().stream()
                        .map(l -> PortalLinkResponse.from(l, links.canManage(), links.identities().get(l.id())))
                        .toList());
    }

    @PostMapping("/tasks/{taskId}/send")
    @Transactional
    ResponseEntity<PortalLinkResponse> send(
            @PathVariable UUID dossierId, @PathVariable String taskId, Authentication authentication) {
        PortalLink link = useCase.send(dossierId, taskId, accessContextFactory.user(authentication));
        HttpStatus status = link.status() == PortalLinkStatus.NOT_SENT ? HttpStatus.CONFLICT : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(PortalLinkResponse.from(link, true));
    }

    @PostMapping("/{linkId}/revoke")
    @Transactional
    PortalLinkResponse revoke(@PathVariable UUID dossierId, @PathVariable UUID linkId, Authentication authentication) {
        return PortalLinkResponse.from(useCase.revoke(dossierId, linkId, accessContextFactory.user(authentication)), true);
    }
}
