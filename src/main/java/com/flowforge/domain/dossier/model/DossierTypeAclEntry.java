package com.flowforge.domain.dossier.model;

import java.util.Objects;

public record DossierTypeAclEntry(
        String tenantId,
        String dossierTypeCode,
        String subjectUserId,
        DossierPermission permission
) {
    public DossierTypeAclEntry {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
        Objects.requireNonNull(subjectUserId, "subjectUserId");
        Objects.requireNonNull(permission, "permission");
    }
}
