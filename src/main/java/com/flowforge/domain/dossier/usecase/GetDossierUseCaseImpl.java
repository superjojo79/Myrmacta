package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import java.util.Objects;
import java.util.UUID;

public final class GetDossierUseCaseImpl implements GetDossierUseCase {

    private final DossierRepository repository;
    private final DossierAccessPolicy accessPolicy;

    public GetDossierUseCaseImpl(DossierRepository repository, DossierAccessPolicy accessPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    @Override
    public Dossier get(UUID id, AccessContext ctx) {
        Dossier dossier = repository.findById(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + id));
        accessPolicy.assertCanView(dossier, ctx);
        return dossier;
    }

    @Override
    public Dossier getByReference(String tenantId, String reference, AccessContext ctx) {
        Dossier dossier = repository.findByReference(
                        Objects.requireNonNull(tenantId, "tenantId"),
                        Objects.requireNonNull(reference, "reference"))
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier not found: " + tenantId + "/" + reference));
        accessPolicy.assertCanView(dossier, ctx);
        return dossier;
    }
}
