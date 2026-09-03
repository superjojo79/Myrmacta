package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.document.port.in.GenerateDocumentUseCase;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.infrastructure.config.TenantProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("documentGenerationTask")
public class DocumentGenerationTask implements JavaDelegate {

    private final TenantProperties tenantProperties;
    private final GenerateDocumentUseCase generateDocumentUseCase;
    private final DocumentStoragePort documentStoragePort;

    public DocumentGenerationTask(
            TenantProperties tenantProperties,
            GenerateDocumentUseCase generateDocumentUseCase,
            DocumentStoragePort documentStoragePort) {
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.generateDocumentUseCase = Objects.requireNonNull(generateDocumentUseCase, "generateDocumentUseCase");
        this.documentStoragePort = Objects.requireNonNull(documentStoragePort, "documentStoragePort");
    }

    @Override
    public void execute(DelegateExecution execution) {
        Object documentType = execution.getVariable(WorkflowVariables.DOCUMENT_TYPE);
        if (documentType == null || documentType.toString().isBlank()) {
            throw new IllegalArgumentException("documentType variable is required");
        }
        String variant = stringOrNull(execution.getVariable("documentVariant"));
        String locale = stringOrNull(execution.getVariable("documentLocale"));
        Map<String, Object> variables = new HashMap<>(execution.getVariables());
        byte[] pdf = generateDocumentUseCase.generate(new GenerateDocumentUseCase.GenerateDocumentCommand(
                documentType.toString(), variant, locale, variables));
        String storageRef =
                documentStoragePort.store(tenantProperties.id(), "application/pdf", pdf);
        execution.setVariable("generatedDocumentId", storageRef);
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : value.toString();
    }
}
