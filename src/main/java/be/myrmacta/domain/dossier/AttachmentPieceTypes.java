package be.myrmacta.domain.dossier;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.model.InvalidDossierElementException;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * TODO §106 (issue #211, lot P, D-PIECE-CLASSIFY) — type de pièce d'un élément {@link ElementType#ATTACHMENT}, porté par
 * sa métadonnée {@link PieceType#METADATA_KEY}. Seule une pièce jointe peut en porter un, et seulement un type déclaré
 * par le type du dossier ; une clé inconnue (retirée du type depuis) rend la pièce « non classée » sans la modifier
 * (H9). Les messages nomment la clé, jamais le contenu de la pièce.
 */
public final class AttachmentPieceTypes {

    private final DossierTypeRepository typeRepository;

    public AttachmentPieceTypes(DossierTypeRepository typeRepository) {
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
    }

    /**
     * Refuse une métadonnée {@code pieceKey} mal formée, posée sur autre chose qu'une pièce jointe ou inconnue du type
     * du dossier. Sans {@code pieceKey} : pièce non classée, rien à vérifier.
     */
    public void requireValid(Dossier dossier, ElementType elementType, Map<String, Object> metadata) {
        if (metadata != null && (metadata.containsKey(PieceType.CLASSIFIED_BY_KEY)
                || metadata.containsKey(PieceType.CLASSIFIED_AT_KEY))) {
            // Revue adversariale : la trace du classement n'est posée que par le reclassement, jamais par l'appelant.
            throw new InvalidDossierElementException(PieceType.CLASSIFIED_BY_KEY + " and " + PieceType.CLASSIFIED_AT_KEY
                    + " are set by the server only");
        }
        Object raw = metadata == null ? null : metadata.get(PieceType.METADATA_KEY);
        if (raw == null) {
            return;
        }
        if (elementType != ElementType.ATTACHMENT) {
            throw new InvalidDossierElementException(PieceType.METADATA_KEY + " is only allowed on ATTACHMENT elements");
        }
        if (!(raw instanceof String key)) {
            throw new InvalidDossierElementException(PieceType.METADATA_KEY + " must be a string");
        }
        require(dossier, key);
    }

    /** Type déclaré pour {@code key} sur le type du dossier, sinon refus. */
    public PieceType require(Dossier dossier, String key) {
        if (!PieceType.isValidKey(key)) {
            throw new InvalidDossierElementException("invalid " + PieceType.METADATA_KEY + ": " + key);
        }
        return declaredType(dossier, key).orElseThrow(() -> new InvalidDossierElementException(
                "unknown " + PieceType.METADATA_KEY + " for dossier type " + dossier.dossierTypeCode() + ": " + key));
    }

    /** Type de pièce de l'élément, vide s'il n'est pas classé ou si son type n'est plus déclaré. */
    public Optional<PieceType> of(Dossier dossier, DossierElement element) {
        if (element.type() != ElementType.ATTACHMENT
                || !(element.metadata().get(PieceType.METADATA_KEY) instanceof String key)) {
            return Optional.empty();
        }
        return declaredType(dossier, key);
    }

    /**
     * TODO §106 (D-PIECE-PORTAL) — types proposés par une tâche externe, dans l'ordre du BPMN ; une clé que le type du
     * dossier ne déclare pas (ou plus) n'est pas proposée.
     */
    public List<PieceType> offered(Dossier dossier, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<PieceType> declared = typeRepository.findByCode(dossier.tenantId(), dossier.dossierTypeCode())
                .map(DossierType::pieceTypes)
                .orElse(List.of());
        return keys.stream()
                .flatMap(key -> declared.stream().filter(type -> type.key().equals(key)).limit(1))
                .toList();
    }

    private Optional<PieceType> declaredType(Dossier dossier, String key) {
        return typeRepository.findByCode(dossier.tenantId(), dossier.dossierTypeCode())
                .flatMap((DossierType type) -> type.pieceType(key));
    }
}
