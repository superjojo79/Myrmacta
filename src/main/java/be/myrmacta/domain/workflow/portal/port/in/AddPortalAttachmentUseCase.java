package be.myrmacta.domain.workflow.portal.port.in;

import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.workflow.portal.model.PortalCredential;

/** Dépôt d'une pièce jointe par le tiers (US-1/US-2, H4, D-PORTAL-SCAN). */
public interface AddPortalAttachmentUseCase {

    DossierElement add(PortalCredential credential, AttachmentUpload upload);

    default DossierElement add(String token, AttachmentUpload upload) {
        return add(PortalCredential.of(token, null), upload);
    }

    /** {@code pieceKey} : type de pièce choisi parmi ceux que propose la tâche (TODO §105), sinon {@code null}. */
    record AttachmentUpload(String label, String filename, String contentType, byte[] content, String pieceKey) {

        public AttachmentUpload(String label, String filename, String contentType, byte[] content) {
            this(label, filename, contentType, content, null);
        }
    }
}
