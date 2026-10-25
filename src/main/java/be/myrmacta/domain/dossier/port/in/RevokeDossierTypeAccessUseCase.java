package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;

public interface RevokeDossierTypeAccessUseCase {
    void revoke(String tenantId, String dossierTypeCode, String subjectUserId, AccessContext.User actor);
}
