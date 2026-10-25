package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierElementNotFoundException;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.port.in.GetDossierElementUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
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
