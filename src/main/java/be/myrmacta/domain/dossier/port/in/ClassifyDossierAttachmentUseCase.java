package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierElement;
import java.util.UUID;

/**
 * TODO §106 (issue #211, lot P, D-PIECE-CLASSIFY) — attribuer, changer ou retirer le type d'une pièce jointe déjà déposée.
 * Droit EDIT sur le type du dossier, dossier non clos ; {@code pieceKey} {@code null} rend la pièce non classée.
 */
public interface ClassifyDossierAttachmentUseCase {

    Result classify(UUID dossierId, UUID elementId, String pieceKey, AccessContext.User actor);

    /** @param previousPieceKey clé avant le changement ({@code null} si la pièce n'était pas classée) */
    record Result(DossierElement element, String previousPieceKey) {}
}
