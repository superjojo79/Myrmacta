package com.flowforge.domain.workflow.portal.port.in;

import com.flowforge.domain.dossier.model.DossierElement;

/** Dépôt d'une pièce jointe par le tiers (US-1/US-2, H4, D-PORTAL-SCAN). */
public interface AddPortalAttachmentUseCase {

    DossierElement add(String token, AttachmentUpload upload);

    record AttachmentUpload(String label, String filename, String contentType, byte[] content) {}
}
