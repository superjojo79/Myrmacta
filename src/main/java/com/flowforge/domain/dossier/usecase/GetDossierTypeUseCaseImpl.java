package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.in.GetDossierTypeUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.util.Objects;

public final class GetDossierTypeUseCaseImpl implements GetDossierTypeUseCase {

    private final DossierTypeRepository repository;
    private final DossierAccessPolicy accessPolicy;

    public GetDossierTypeUseCaseImpl(DossierTypeRepository repository, DossierAccessPolicy accessPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    @Override
    public DossierType getByCode(String tenantId, String code, AccessContext ctx) {
        DossierType type = repository.findByCode(tenantId, code)
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier type not found: " + tenantId + "/" + code));
        accessPolicy.assertCanViewType(type.tenantId(), type.code(), ctx);
        return type;
    }
}
