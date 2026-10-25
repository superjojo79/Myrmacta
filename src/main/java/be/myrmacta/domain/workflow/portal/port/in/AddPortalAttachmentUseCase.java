package be.myrmacta.domain.workflow.portal.port.in;

import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.workflow.portal.model.PortalCredential;

/** Dépôt d'une pièce jointe par le tiers (US-1/US-2, H4, D-PORTAL-SCAN). */
public interface AddPortalAttachmentUseCase {

    DossierElement add(PortalCredential credential, AttachmentUpload upload);

    default DossierElement add(String token, AttachmentUpload upload) {
        return add(PortalCredential.of(token, null), upload);
    }

    record AttachmentUpload(String label, String filename, String contentType, byte[] content) {}
}
