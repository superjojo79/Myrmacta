package be.myrmacta.infrastructure.dossier;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierRelation;
import be.myrmacta.domain.dossier.model.RelationType;
import be.myrmacta.domain.dossier.port.in.CreateDossierRelationUseCase;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChildDossierRelationConfirmer {

    private final DossierRelationRepository relationRepository;
    private final CreateDossierUseCase createDossierUseCase;
    private final CreateDossierRelationUseCase createDossierRelationUseCase;

    public ChildDossierRelationConfirmer(
            DossierRelationRepository relationRepository,
            CreateDossierUseCase createDossierUseCase,
            CreateDossierRelationUseCase createDossierRelationUseCase) {
        this.relationRepository = Objects.requireNonNull(relationRepository, "relationRepository");
        this.createDossierUseCase = Objects.requireNonNull(createDossierUseCase, "createDossierUseCase");
        this.createDossierRelationUseCase =
                Objects.requireNonNull(createDossierRelationUseCase, "createDossierRelationUseCase");
    }

    @Transactional
    public UUID confirm(
            String tenantId,
            UUID sourceDossierId,
            String childDossierTypeCode,
            String childDossierLabel,
            Map<String, Object> childDossierFields,
            RelationType relationType,
            String idempotencyKey) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sourceDossierId, "sourceDossierId");
        Objects.requireNonNull(childDossierTypeCode, "childDossierTypeCode");
        Objects.requireNonNull(childDossierLabel, "childDossierLabel");
        Objects.requireNonNull(relationType, "relationType");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");

        return relationRepository
                .findByIdempotencyKey(tenantId, sourceDossierId, idempotencyKey)
                .map(DossierRelation::targetDossierId)
                .orElseGet(() -> createChild(
                        tenantId,
                        sourceDossierId,
                        childDossierTypeCode,
                        childDossierLabel,
                        childDossierFields == null ? Map.of() : childDossierFields,
                        relationType,
                        idempotencyKey));
    }

    // No catch-and-recover here on purpose: once DossierRelationJpaAdapter#save's own
    // @Transactional method lets DuplicateDossierRelationException propagate, Spring has
    // already marked this participating transaction rollback-only (global-rollback-on-
    // participation-failure is Spring's default). Any further read attempted in *this*
    // transaction — including a "helpful" recovery lookup — runs against an already-doomed
    // Hibernate session and fails too, surfacing a raw DataIntegrityViolationException instead
    // of a clean signal. Let the exception propagate untouched; the caller retries with a
    // brand new confirm() call (new transaction, new session), whose own idempotency lookup
    // then finds the winner's already-committed row. Verified against a real concurrent race
    // (two threads, same idempotency key) — see CreateChildDossierDelegateTest / IT.
    private UUID createChild(
            String tenantId,
            UUID sourceDossierId,
            String childDossierTypeCode,
            String childDossierLabel,
            Map<String, Object> childDossierFields,
            RelationType relationType,
            String idempotencyKey) {
        AccessContext system = AccessContext.system("flowable-delegate");
        Dossier child = createDossierUseCase.create(
                new CreateDossierUseCase.CreateDossierCommand(
                        tenantId, childDossierTypeCode, childDossierLabel, childDossierFields, "workflow"),
                system);
        createDossierRelationUseCase.create(
                new CreateDossierRelationUseCase.CreateDossierRelationCommand(
                        tenantId,
                        sourceDossierId,
                        child.id(),
                        relationType,
                        "workflow",
                        idempotencyKey),
                system);
        return child.id();
    }
}
