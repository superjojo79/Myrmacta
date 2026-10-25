package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.AttachmentPieceTypes;
import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierConflictException;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierElementNotFoundException;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.model.InvalidDossierElementException;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.dossier.port.in.ClassifyDossierAttachmentUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §105 (D-PIECE-CLASSIFY) — le classement est tracé sur l'élément lui-même ({@code pieceClassifiedBy},
 * {@code pieceClassifiedAt}) et dans le journal de sécurité par l'adaptateur web ; le contenu de la pièce n'est jamais lu.
 */
public final class ClassifyDossierAttachmentUseCaseImpl implements ClassifyDossierAttachmentUseCase {

    static final String CLASSIFIED_BY = "pieceClassifiedBy";
    static final String CLASSIFIED_AT = "pieceClassifiedAt";

    private final String tenantId;
    private final DossierRepository dossierRepository;
    private final DossierElementRepository elementRepository;
    private final DossierAccessPolicy accessPolicy;
    private final AttachmentPieceTypes pieceTypes;
    private final Clock clock;

    public ClassifyDossierAttachmentUseCaseImpl(
            String tenantId,
            DossierRepository dossierRepository,
            DossierElementRepository elementRepository,
            DossierAccessPolicy accessPolicy,
            AttachmentPieceTypes pieceTypes,
            Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.pieceTypes = Objects.requireNonNull(pieceTypes, "pieceTypes");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Result classify(UUID dossierId, UUID elementId, String pieceKey, AccessContext.User actor) {
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(actor, "actor");
        Dossier dossier = dossierRepository.findById(dossierId)
                .filter(d -> tenantId.equals(d.tenantId()))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        accessPolicy.assertCanEdit(dossier, actor);
        if (dossier.status().blocksMutation()) {
            throw new DossierConflictException("Dossier elements cannot be changed in status " + dossier.status());
        }
        DossierElement element = elementRepository.findByIdAndTenantId(elementId, tenantId)
                .filter(e -> e.dossierId().equals(dossierId))
                .orElseThrow(() -> new DossierElementNotFoundException(elementId));
        if (element.type() != ElementType.ATTACHMENT) {
            throw new InvalidDossierElementException("only ATTACHMENT elements have a piece type");
        }
        String key = pieceKey == null || pieceKey.isBlank() ? null : pieceKey.strip();
        if (key != null) {
            pieceTypes.require(dossier, key);
        }
        String previous = element.metadata().get(PieceType.METADATA_KEY) instanceof String p ? p : null;
        Map<String, Object> metadata = new HashMap<>(element.metadata());
        if (key == null) {
            metadata.remove(PieceType.METADATA_KEY);
        } else {
            metadata.put(PieceType.METADATA_KEY, key);
        }
        metadata.put(CLASSIFIED_BY, actor.userId());
        metadata.put(CLASSIFIED_AT, clock.instant().toString());
        DossierElement saved = elementRepository.save(new DossierElement(element.id(), element.dossierId(),
                element.tenantId(), element.type(), element.label(), element.storageRef(), metadata, element.addedBy(),
                element.addedAt(), element.workflowStepId()));
        return new Result(saved, previous);
    }
}
