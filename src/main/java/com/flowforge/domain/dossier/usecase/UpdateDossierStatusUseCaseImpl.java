package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierConflictException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierStatusTransitions;
import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import java.time.Instant;
import java.util.Objects;

public final class UpdateDossierStatusUseCaseImpl implements UpdateDossierStatusUseCase {

    private final DossierRepository repository;
    private final DossierAccessPolicy accessPolicy;

    public UpdateDossierStatusUseCaseImpl(DossierRepository repository, DossierAccessPolicy accessPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    @Override
    public Dossier update(UpdateDossierStatusCommand command, AccessContext ctx) {
        Objects.requireNonNull(command, "command");
        Dossier dossier = repository.findById(command.dossierId())
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + command.dossierId()));

        accessPolicy.assertCanEdit(dossier, ctx);
        if (command.expectedVersion() != null && command.expectedVersion() != dossier.version()) {
            throw new DossierConflictException(
                    "Expected version " + command.expectedVersion() + " but found " + dossier.version());
        }

        DossierStatusTransitions.assertCanTransition(dossier.status(), command.status());
        Dossier updated = dossier.withStatus(command.status(), command.updatedBy(), Instant.now());
        return repository.save(updated);
    }
}
