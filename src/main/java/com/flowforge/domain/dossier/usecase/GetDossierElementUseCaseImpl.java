package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierElementNotFoundException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.port.in.GetDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import java.util.Objects;
import java.util.UUID;

public final class GetDossierElementUseCaseImpl implements GetDossierElementUseCase {

    private final String tenantId;
    private final DossierElementRepository elementRepository;
    private final DossierRepository dossierRepository;
    private final DossierAccessPolicy accessPolicy;

    public GetDossierElementUseCaseImpl(
            String tenantId,
            DossierElementRepository elementRepository,
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    @Override
    public DossierElement get(UUID id, AccessContext ctx) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ctx, "ctx");
        DossierElement element = elementRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new DossierElementNotFoundException(id));
        Dossier dossier = dossierRepository
                .findById(element.dossierId())
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + element.dossierId()));
        if (!tenantId.equals(dossier.tenantId())) {
            throw new DossierNotFoundException("Dossier not found: " + element.dossierId());
        }
        accessPolicy.assertCanView(dossier, ctx);
        return element;
    }
}
