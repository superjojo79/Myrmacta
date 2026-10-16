package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import com.flowforge.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Component;

/**
 * TODO §68 — lit l'adresse du destinataire <strong>par nom littéral</strong> : un champ du dossier
 * ({@code Dossier.fields()}, lecture autorisée par {@code ArchitectureRulesTest} — une seule clé,
 * jamais renvoyée à un navigateur) ou une variable de process ({@code RuntimeService.getVariable},
 * sans évaluation d'expression — TODO §69). Sert aussi au destinataire de la brique « Notifier »
 * ({@code dossierField:}, TODO §93).
 */
@Component
class PortalRecipientResolverAdapter implements PortalRecipientResolverPort {

    private final DossierRepository dossierRepository;
    private final RuntimeService runtimeService;

    PortalRecipientResolverAdapter(DossierRepository dossierRepository, RuntimeService runtimeService) {
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
    }

    @Override
    public Optional<Object> resolve(PortalRecipientSource source, UUID dossierId, String processInstanceId) {
        return switch (source.kind()) {
            case DOSSIER_FIELD -> dossierRepository.findById(dossierId)
                    .map(dossier -> dossier.fields().get(source.name()));
            case PROCESS_VARIABLE -> Optional.ofNullable(runtimeService.getVariable(processInstanceId, source.name()));
        };
    }
}
