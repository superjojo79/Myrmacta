package be.myrmacta.infrastructure.web.dossier;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierPermission;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.model.DossierRelationView;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.RelationDirection;
import be.myrmacta.domain.dossier.model.RelationType;
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
        List<FieldDefinition> fieldDefinitions,
        Integer retentionDays,
        Boolean retentionNever,
        Integer slaTargetHours,
        List<String> documentTemplateKeys) {}

record UpdateDossierTypeRequest(
        String label,
        String workflowKey,
        Boolean active,
        Boolean allowUnknownFields,
        List<FieldDefinition> fieldDefinitions,
        Integer retentionDays,
        Boolean retentionNever,
        Integer slaTargetHours,
        Boolean clearSlaTargetHours,
        List<String> documentTemplateKeys) {}

record DossierTypeResponse(
        UUID id,
        String tenantId,
        String code,
        String label,
        String workflowKey,
        boolean active,
        boolean allowUnknownFields,
        List<FieldDefinition> fieldDefinitions,
        Integer retentionDays,
        boolean retentionNever,
        Integer slaTargetHours,
        List<String> documentTemplateKeys) {

    static DossierTypeResponse from(DossierType type) {
        return new DossierTypeResponse(
                type.id(),
                type.tenantId(),
                type.code(),
                type.label(),
                type.workflowKey(),
                type.active(),
                type.allowUnknownFields(),
                type.fieldDefinitions(),
                type.retentionDays(),
                type.retentionNever(),
                type.slaTargetHours(),
                type.documentTemplateKeys());
    }
}

/**
 * TODO §52 -- deliberately omits {@code htmlContent}/{@code version}/{@code status}: this endpoint
 * is reachable by any user who can view the dossier type (not just {@code TENANT_ADMIN}, unlike
 * {@code GET /api/document-templates}), so it must never leak template content.
 */
record AllowedDocumentTemplateResponse(String templateKey, String variantKey, String locale, String label) {

    static AllowedDocumentTemplateResponse from(DocumentTemplate template) {
        return new AllowedDocumentTemplateResponse(
                template.templateKey(), template.variantKey(), template.locale(), template.label());
    }
}

record CreateDossierRequest(String dossierTypeCode, String label, Map<String, Object> fields) {}

record UpdateDossierStatusRequest(DossierStatus status, Long expectedVersion) {}

record UpdateDossierFieldsRequest(Map<String, Object> fields, Long expectedVersion) {}

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
        Instant closedAt,
        Instant purgeEligibleAt,
        boolean canEdit) {

    static DossierResponse from(Dossier dossier, boolean canEdit) {
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
                dossier.closedAt(),
                dossier.purgeEligibleAt(),
                canEdit);
    }
}

record GrantDossierTypeAccessRequest(String subjectUserId, DossierPermission permission) {}

record DossierListItemResponse(
        UUID id,
        String reference,
        String label,
        String dossierTypeCode,
        String dossierTypeLabel,
        DossierStatus status,
        Instant createdAt,
        Instant updatedAt) {

    static DossierListItemResponse from(be.myrmacta.domain.dossier.model.DossierListItem item) {
        return new DossierListItemResponse(
                item.id(),
                item.reference(),
                item.label(),
                item.dossierTypeCode(),
                item.dossierTypeLabel(),
                item.status(),
                item.createdAt(),
                item.updatedAt());
    }
}

record CreateDossierRelationRequest(UUID targetDossierId, RelationType type) {}

record AddDossierElementRequest(ElementType type, String label, Map<String, Object> metadata) {}

record DossierRelationResponse(
        UUID id,
        RelationType type,
        RelationDirection direction,
        DossierListItemResponse relatedDossier,
        String createdBy,
        Instant createdAt) {

    static DossierRelationResponse from(DossierRelationView view) {
        return new DossierRelationResponse(
                view.id(),
                view.type(),
                view.direction(),
                DossierListItemResponse.from(view.relatedDossier()),
                view.createdBy(),
                view.createdAt());
    }
}

record DossierPageResponse(
        List<DossierListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static DossierPageResponse from(be.myrmacta.domain.dossier.model.DossierPage page) {
        return new DossierPageResponse(
                page.content().stream().map(DossierListItemResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}