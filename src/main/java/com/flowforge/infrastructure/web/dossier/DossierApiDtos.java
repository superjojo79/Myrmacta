package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierPermission;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record CreateDossierTypeRequest(
        String code,
        String label,
        String workflowKey,
        Boolean active,
        Boolean allowUnknownFields,
        List<FieldDefinition> fieldDefinitions) {}

record UpdateDossierTypeRequest(
        String label,
        String workflowKey,
        Boolean active,
        Boolean allowUnknownFields,
        List<FieldDefinition> fieldDefinitions) {}

record DossierTypeResponse(
        UUID id,
        String tenantId,
        String code,
        String label,
        String workflowKey,
        boolean active,
        boolean allowUnknownFields,
        List<FieldDefinition> fieldDefinitions) {

    static DossierTypeResponse from(DossierType type) {
        return new DossierTypeResponse(
                type.id(),
                type.tenantId(),
                type.code(),
                type.label(),
                type.workflowKey(),
                type.active(),
                type.allowUnknownFields(),
                type.fieldDefinitions());
    }
}

record CreateDossierRequest(String dossierTypeCode, String label, Map<String, Object> fields) {}

record UpdateDossierStatusRequest(DossierStatus status, Long expectedVersion) {}

record DossierResponse(
        UUID id,
        String tenantId,
        String reference,
        String dossierTypeCode,
        String label,
        DossierStatus status,
        Map<String, Object> fields,
        UUID currentWorkflowLinkId,
        long version,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        String closedBy,
        Instant closedAt) {

    static DossierResponse from(Dossier dossier) {
        return new DossierResponse(
                dossier.id(),
                dossier.tenantId(),
                dossier.reference(),
                dossier.dossierTypeCode(),
                dossier.label(),
                dossier.status(),
                dossier.fields(),
                dossier.currentWorkflowLinkId(),
                dossier.version(),
                dossier.createdBy(),
                dossier.createdAt(),
                dossier.updatedBy(),
                dossier.updatedAt(),
                dossier.closedBy(),
                dossier.closedAt());
    }
}

record GrantDossierTypeAccessRequest(String subjectUserId, DossierPermission permission) {}
