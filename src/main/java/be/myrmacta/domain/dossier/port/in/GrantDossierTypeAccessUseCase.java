package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierPermission;

public interface GrantDossierTypeAccessUseCase {
    void grant(GrantCommand command, AccessContext.User actor);

    record GrantCommand(
            String tenantId, String dossierTypeCode, String subjectUserId, DossierPermission permission) {}
}
