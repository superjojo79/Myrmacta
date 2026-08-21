package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElementView;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.port.in.ListDossierElementsUseCase;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.Objects;
import java.util.UUID;

public final class ListDossierElementsUseCaseImpl implements ListDossierElementsUseCase {

    private final DossierRepository dossierRepository;
    private final DossierElementRepository elementRepository;
    private final DossierAccessPolicy accessPolicy;

    public ListDossierElementsUseCaseImpl(
            DossierRepository dossierRepository,
            DossierElementRepository elementRepository,
            DossierAccessPolicy accessPolicy) {
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    @Override
    public PageResult<DossierElementView> list(UUID dossierId, AccessContext ctx, PageQuery query) {
        Dossier dossier = dossierRepository
                .findById(Objects.requireNonNull(dossierId, "dossierId"))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        accessPolicy.assertCanView(dossier, ctx);
        return elementRepository
                .pageByDossierId(dossierId, Objects.requireNonNull(query, "query"))
                .map(DossierElementView::from);
    }
}
