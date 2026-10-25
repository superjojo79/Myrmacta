package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierListItem;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierRelation;
import be.myrmacta.domain.dossier.model.DossierRelationView;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.RelationDirection;
import be.myrmacta.domain.dossier.port.in.ListDossierRelationsUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListDossierRelationsUseCaseImpl implements ListDossierRelationsUseCase {

    private final DossierRepository dossierRepository;
    private final DossierRelationRepository relationRepository;
    private final DossierTypeRepository typeRepository;
    private final DossierAccessPolicy accessPolicy;

    public ListDossierRelationsUseCaseImpl(
            DossierRepository dossierRepository,
            DossierRelationRepository relationRepository,
            DossierTypeRepository typeRepository,
            DossierAccessPolicy accessPolicy) {
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.relationRepository = Objects.requireNonNull(relationRepository, "relationRepository");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    @Override
    public List<DossierRelationView> list(UUID dossierId, AccessContext ctx) {
        Dossier dossier = dossierRepository
                .findById(Objects.requireNonNull(dossierId, "dossierId"))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        accessPolicy.assertCanView(dossier, ctx);

        List<DossierRelationView> views = new ArrayList<>();
        for (DossierRelation relation : relationRepository.findByDossier(dossier.tenantId(), dossierId)) {
            boolean outgoing = dossierId.equals(relation.sourceDossierId());
            UUID relatedId = outgoing ? relation.targetDossierId() : relation.sourceDossierId();
            Dossier related = dossierRepository.findById(relatedId).orElse(null);
            if (related == null) {
                continue;
            }
            try {
                accessPolicy.assertCanView(related, ctx);
            } catch (AccessDeniedException ignored) {
                continue;
            }
            String typeLabel = typeRepository
                    .findByCode(related.tenantId(), related.dossierTypeCode())
                    .map(DossierType::label)
                    .orElse("");
            views.add(new DossierRelationView(
                    relation.id(),
                    relation.type(),
                    outgoing ? RelationDirection.OUTGOING : RelationDirection.INCOMING,
                    DossierListItem.from(related, typeLabel),
                    relation.createdBy(),
                    relation.createdAt()));
        }
        return List.copyOf(views);
    }
}
