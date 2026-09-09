package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.document.port.in.GenerateDocumentUseCase;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierElementView;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.infrastructure.config.DocumentGenerationProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import com.flowforge.infrastructure.web.document.DocumentGenerationRateLimitExceededException;
import com.flowforge.infrastructure.web.document.DocumentGenerationRateLimiter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class GenerateDocumentTool {

    private final GetDossierUseCase getDossierUseCase;
    private final GetDossierTypeUseCase getDossierTypeUseCase;
    private final DossierAccessPolicy accessPolicy;
    private final GenerateDocumentUseCase generateDocumentUseCase;
    private final DocumentStoragePort documentStoragePort;
    private final AddDossierElementUseCase addDossierElementUseCase;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;
    private final DocumentGenerationRateLimiter rateLimiter;
    private final DocumentGenerationProperties documentGenerationProperties;

    public GenerateDocumentTool(
            GetDossierUseCase getDossierUseCase,
            GetDossierTypeUseCase getDossierTypeUseCase,
            DossierAccessPolicy accessPolicy,
            GenerateDocumentUseCase generateDocumentUseCase,
            DocumentStoragePort documentStoragePort,
            AddDossierElementUseCase addDossierElementUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory,
            DocumentGenerationRateLimiter rateLimiter,
            DocumentGenerationProperties documentGenerationProperties) {
        this.getDossierUseCase = Objects.requireNonNull(getDossierUseCase, "getDossierUseCase");
        this.getDossierTypeUseCase = Objects.requireNonNull(getDossierTypeUseCase, "getDossierTypeUseCase");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.generateDocumentUseCase =
                Objects.requireNonNull(generateDocumentUseCase, "generateDocumentUseCase");
        this.documentStoragePort = Objects.requireNonNull(documentStoragePort, "documentStoragePort");
        this.addDossierElementUseCase =
                Objects.requireNonNull(addDossierElementUseCase, "addDossierElementUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.documentGenerationProperties =
                Objects.requireNonNull(documentGenerationProperties, "documentGenerationProperties");
    }

    @McpTool(
            name = "generate_document",
            description = "Generate a PDF for a dossier from a template listed on that dossier's type, "
                    + "store it and attach a PRODUCED_DOC element. Does not return PDF bytes. "
                    + "dryRun=true validates without generating.")
    public Object generateDocument(
            @McpToolParam(required = true, description = "Dossier id (UUID)") String dossierId,
            @McpToolParam(required = true, description = "Document template key (must be listed on the dossier type)")
                    String templateKey,
            @McpToolParam(required = false, description = "Optional template variant") String variantKey,
            @McpToolParam(required = false, description = "Optional locale") String locale,
            @McpToolParam(
                            required = false,
                            description = "Extra template variables overlaid on the dossier fields; reserved keys rejected")
                    Map<String, Object> variables,
            @McpToolParam(required = false, description = "Validate without generating; default false")
                    Boolean dryRun) {
        AccessContext.User actor = accessContextFactory.currentActor();
        UUID id = McpArgs.requireUuid(dossierId, "dossierId");
        String key = McpArgs.requireNonBlank(templateKey, "templateKey");
        Map<String, Object> overlay = McpArgs.optionalMap(variables, "variables");
        WorkflowVariables.assertNoReservedKeys(overlay);

        Dossier dossier = getDossierUseCase.get(id, actor);
        accessPolicy.assertCanEdit(dossier, actor);
        DossierType type = getDossierTypeUseCase.getByCode(tenantProperties.id(), dossier.dossierTypeCode(), actor);
        if (!type.documentTemplateKeys().contains(key)) {
            throw new AccessDeniedException("Dossier type does not list this document template");
        }

        if (Boolean.TRUE.equals(dryRun)) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("dryRun", true);
            preview.put("valid", true);
            preview.put("dossierId", id.toString());
            preview.put("templateKey", key);
            return preview;
        }

        if (!rateLimiter.tryConsume(actor.userId(), documentGenerationProperties.rateLimitPerMinute())) {
            throw new DocumentGenerationRateLimitExceededException(actor.userId());
        }

        Map<String, Object> merged = new LinkedHashMap<>(DossierMcpView.from(dossier).fields());
        merged.putAll(overlay);
        byte[] pdf = generateDocumentUseCase.generate(new GenerateDocumentUseCase.GenerateDocumentCommand(
                key, blankToNull(variantKey), blankToNull(locale), merged));
        String storageRef = documentStoragePort.store(tenantProperties.id(), "application/pdf", pdf);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("templateKey", key);
        metadata.put("contentType", "application/pdf");
        if (blankToNull(variantKey) != null) {
            metadata.put("variantKey", variantKey);
        }
        if (blankToNull(locale) != null) {
            metadata.put("locale", locale);
        }
        DossierElement element = addDossierElementUseCase.add(
                new AddDossierElementUseCase.AddCommand(
                        id,
                        ElementType.PRODUCED_DOC,
                        "Document généré — " + key,
                        storageRef,
                        metadata,
                        actor.userId(),
                        null),
                actor);
        return DossierElementView.from(element);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
