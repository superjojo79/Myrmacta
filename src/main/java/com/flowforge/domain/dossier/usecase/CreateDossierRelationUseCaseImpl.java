package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierRelation;
import com.flowforge.domain.dossier.port.in.CreateDossierRelationUseCase;
import com.flowforge.domain.dossier.port.out.DossierRelationRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CreateDossierRelationUseCaseImpl implements CreateDossierRelationUseCase {

    private final DossierRepository dossierRepository;
    private final DossierRelationRepository relationRepository;
    private final DossierAccessPolicy accessPolicy;

    public CreateDossierRelationUseCaseImpl(
            DossierRepository dossierRepository,
            DossierRelationRepository relationRepository,
            DossierAccessPolicy accessPolicy) {
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.relationRepository = Objects.requireNonNull(relationRepository, "relationRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    @Override
    public DossierRelation create(CreateDossierRelationCommand command, AccessContext ctx) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(ctx, "ctx");
        if (command.sourceDossierId() == null || command.targetDossierId() == null) {
            throw new IllegalArgumentException("sourceDossierId and targetDossierId are required");
        }
        if (command.type() == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (command.sourceDossierId().equals(command.targetDossierId())) {
            throw new IllegalArgumentException("source and target must differ");
        }

        Dossier source = requireTenantDossier(command.sourceDossierId(), command.tenantId());
        accessPolicy.assertCanEdit(source, ctx);
        Dossier target = requireTenantDossier(command.targetDossierId(), command.tenantId());
        accessPolicy.assertCanView(target, ctx);

        DossierRelation relation = new DossierRelation(
                UUID.randomUUID(),
                source.id(),
                target.id(),
                source.tenantId(),
                command.type(),
                command.createdBy(),
                Instant.now(),
                command.idempotencyKey());
        return relationRepository.save(relation);
    }

    private Dossier requireTenantDossier(UUID id, String tenantId) {
        return dossierRepository
                .findById(id)
                .filter(candidate -> tenantId.equals(candidate.tenantId()))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + id));
    }
}
