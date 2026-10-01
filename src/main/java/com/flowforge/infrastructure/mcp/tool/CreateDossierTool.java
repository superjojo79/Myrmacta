package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.dossier.DossierFieldRules;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierFieldValidationException;
import com.flowforge.domain.dossier.model.DossierFieldValidator;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.InactiveDossierTypeException;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierTypeUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class CreateDossierTool {

    private final CreateDossierUseCase createDossierUseCase;
    private final GetDossierTypeUseCase getDossierTypeUseCase;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;
    private final DossierFieldValidator fieldValidator;
    private final DossierFieldRules fieldRules;

    public CreateDossierTool(
            CreateDossierUseCase createDossierUseCase,
            GetDossierTypeUseCase getDossierTypeUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory) {
        this(createDossierUseCase, getDossierTypeUseCase, tenantProperties, accessContextFactory, DossierFieldRules.disabled());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CreateDossierTool(
            CreateDossierUseCase createDossierUseCase,
            GetDossierTypeUseCase getDossierTypeUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory,
            DossierFieldRules fieldRules) {
        this.fieldRules = Objects.requireNonNull(fieldRules, "fieldRules");
        this.createDossierUseCase = Objects.requireNonNull(createDossierUseCase, "createDossierUseCase");
        this.getDossierTypeUseCase = Objects.requireNonNull(getDossierTypeUseCase, "getDossierTypeUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
        this.fieldValidator = new DossierFieldValidator();
    }

    @McpTool(
            name = "create_dossier",
            description = "Create a dossier of the given type (DRAFT). fields must match the type schema "
                    + "(see get_dossier_type). dryRun=true validates without persisting.")
    public Object createDossier(
            @McpToolParam(required = true, description = "Dossier type code") String dossierTypeCode,
            @McpToolParam(required = true, description = "Display label") String label,
            @McpToolParam(required = false, description = "Field values matching the type schema")
                    Map<String, Object> fields,
            @McpToolParam(required = false, description = "Validate without persisting; default false")
                    Boolean dryRun) {
        AccessContext.User actor = accessContextFactory.currentActor();
        String typeCode = McpArgs.requireNonBlank(dossierTypeCode, "dossierTypeCode");
        String rawLabel = McpArgs.requireNonBlank(label, "label");
        Map<String, Object> rawFields = McpArgs.optionalMap(fields, "fields");
        if (Boolean.TRUE.equals(dryRun)) {
            return preview(typeCode, rawLabel, rawFields, actor);
        }
        Dossier dossier = createDossierUseCase.create(
                new CreateDossierUseCase.CreateDossierCommand(
                        tenantProperties.id(), typeCode, rawLabel, rawFields, actor.userId()),
                actor);
        return DossierMcpView.from(dossier);
    }

    private Map<String, Object> preview(
            String typeCode, String label, Map<String, Object> fields, AccessContext.User actor) {
        DossierType type = getDossierTypeUseCase.getByCode(tenantProperties.id(), typeCode, actor);
        if (!type.active()) {
            throw new InactiveDossierTypeException(type.code());
        }
        var merged = fieldValidator.applyDefaults(type.fieldDefinitions(), fields);
        var ruled = fieldRules.apply(type.fieldDefinitions(), merged, fields == null ? java.util.Set.of() : fields.keySet());
        var errors = new java.util.ArrayList<>(
                fieldValidator.validate(ruled.effectiveDefinitions(), ruled.fields(), type.allowUnknownFields()));
        errors.addAll(ruled.errors());
        if (!errors.isEmpty()) {
            throw new DossierFieldValidationException(errors);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", true);
        result.put("valid", true);
        result.put("dossierTypeCode", typeCode);
        result.put("label", label);
        return result;
    }
}
