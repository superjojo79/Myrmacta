package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.infrastructure.config.TenantProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("attachGeneratedDocumentTask")
public class AttachGeneratedDocumentTask implements JavaDelegate {

    private final TenantProperties tenantProperties;
    private final DocumentStoragePort documentStoragePort;
    private final AddDossierElementUseCase addDossierElementUseCase;

    public AttachGeneratedDocumentTask(
            TenantProperties tenantProperties,
            DocumentStoragePort documentStoragePort,
            AddDossierElementUseCase addDossierElementUseCase) {
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.documentStoragePort = Objects.requireNonNull(documentStoragePort, "documentStoragePort");
        this.addDossierElementUseCase =
                Objects.requireNonNull(addDossierElementUseCase, "addDossierElementUseCase");
    }

    /**
     * TODO §66 F4 (Phase 10 adversarial review): {@code addDossierElementUseCase.add(...)} below
     * (and the {@code DOCUMENT_GENERATED} notification it triggers for {@code PRODUCED_DOC}) run
     * inside Flowable's own Spring-managed command transaction — a notification failure here rolls
     * back the element write together with it and fails the job (retried per the engine's async
     * job policy), the same rollback-together semantics now applied explicitly to the MCP path
     * ({@link com.flowforge.infrastructure.mcp.tool.GenerateDocumentTool}).
     */
    @Override
    public void execute(DelegateExecution execution) {
        Object dossierIdVar = execution.getVariable("dossierId");
        Object storageRefVar = execution.getVariable("generatedDocumentId");
        if (dossierIdVar == null || storageRefVar == null) {
            throw new IllegalArgumentException("dossierId and generatedDocumentId variables are required");
        }
        String storageRef = storageRefVar.toString();
        if (!documentStoragePort.exists(tenantProperties.id(), storageRef)) {
            throw new IllegalStateException("Generated document not found in storage: " + storageRef);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        Object documentType = execution.getVariable("documentType");
        if (documentType != null && !documentType.toString().isBlank()) {
            metadata.put("templateKey", documentType.toString());
        }
        metadata.put("contentType", "application/pdf");
        Object variant = execution.getVariable("documentVariant");
        if (variant != null && !variant.toString().isBlank()) {
            metadata.put("variantKey", variant.toString());
        }
        Object locale = execution.getVariable("documentLocale");
        if (locale != null && !locale.toString().isBlank()) {
            metadata.put("locale", locale.toString());
        }
        String activityName = execution.getCurrentActivityName();
        if (activityName == null || activityName.isBlank()) {
            activityName = execution.getCurrentActivityId();
        }
        String label = "Document généré — " + activityName;
        addDossierElementUseCase.add(
                new AddDossierElementUseCase.AddCommand(
                        UUID.fromString(dossierIdVar.toString()),
                        ElementType.PRODUCED_DOC,
                        label,
                        storageRef,
                        metadata,
                        "system",
                        execution.getCurrentActivityId()),
                AccessContext.system("flowable-delegate"));
    }
}
