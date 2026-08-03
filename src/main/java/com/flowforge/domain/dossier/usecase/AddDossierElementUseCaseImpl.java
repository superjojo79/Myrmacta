package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class AddDossierElementUseCaseImpl implements AddDossierElementUseCase {

    private final String tenantId;
    private final DossierRepository dossierRepository;
    private final DossierElementRepository elementRepository;

    public AddDossierElementUseCaseImpl(
            String tenantId,
            DossierRepository dossierRepository,
            DossierElementRepository elementRepository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
    }

    @Override
    public DossierElement add(AddCommand command, AccessContext ctx) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(ctx, "ctx");
        if (command.type() != ElementType.PRODUCED_DOC) {
            throw new InvalidDossierElementException("Only PRODUCED_DOC is supported in V1");
        }
        if (command.storageRef() == null || command.storageRef().isBlank()) {
            throw new InvalidDossierElementException("storageRef is required for PRODUCED_DOC");
        }
        if (command.label() == null || command.label().isBlank()) {
            throw new InvalidDossierElementException("label is required");
        }
        Dossier dossier = dossierRepository
                .findById(Objects.requireNonNull(command.dossierId(), "dossierId"))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + command.dossierId()));
        if (!tenantId.equals(dossier.tenantId())) {
            throw new DossierNotFoundException("Dossier not found: " + command.dossierId());
        }
        Map<String, Object> metadata = command.metadata() == null ? Map.of() : command.metadata();
        DossierElement element = new DossierElement(
                UUID.randomUUID(),
                dossier.id(),
                tenantId,
                ElementType.PRODUCED_DOC,
                command.label().trim(),
                command.storageRef().trim(),
                metadata,
                command.addedBy() == null || command.addedBy().isBlank() ? "system" : command.addedBy(),
                Instant.now(),
                command.workflowStepId());
        return elementRepository.save(element);
    }
}
