package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.dossier.DossierFieldRules;
import be.myrmacta.domain.dossier.DossierUniquenessCheck;
import be.myrmacta.domain.dossier.ManagedByWorkflowFields;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierFieldValidationException;
import be.myrmacta.domain.dossier.model.DossierFieldValidator;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.FieldValidationError;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.InactiveDossierTypeException;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.in.GetDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.out.DossierUniquenessPort;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
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
    private final DossierUniquenessCheck uniqueness;

    public CreateDossierTool(
            CreateDossierUseCase createDossierUseCase,
            GetDossierTypeUseCase getDossierTypeUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory) {
        this(createDossierUseCase, getDossierTypeUseCase, tenantProperties, accessContextFactory, DossierFieldRules.disabled());
    }

    public CreateDossierTool(
            CreateDossierUseCase createDossierUseCase,
            GetDossierTypeUseCase getDossierTypeUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory,
            DossierFieldRules fieldRules) {
        this(createDossierUseCase, getDossierTypeUseCase, tenantProperties, accessContextFactory, fieldRules,
                DossierUniquenessCheck.disabled());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CreateDossierTool(
            CreateDossierUseCase createDossierUseCase,
            GetDossierTypeUseCase getDossierTypeUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory,
            DossierFieldRules fieldRules,
            DossierUniquenessPort uniquenessPort) {
        this(createDossierUseCase, getDossierTypeUseCase, tenantProperties, accessContextFactory, fieldRules,
                new DossierUniquenessCheck(uniquenessPort));
    }

    private CreateDossierTool(
            CreateDossierUseCase createDossierUseCase,
            GetDossierTypeUseCase getDossierTypeUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory,
            DossierFieldRules fieldRules,
            DossierUniquenessCheck uniqueness) {
        this.uniqueness = Objects.requireNonNull(uniqueness, "uniqueness");
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
        // TODO §126 : même garde que CreateDossierUseCaseImpl — une clé gérée n'est jamais acceptée en création.
        errors.addAll(ManagedByWorkflowFields.rejectTouched(
                type.fieldDefinitions(), fields == null ? java.util.Set.of() : fields.keySet()));
        // TODO §102 : un essai à blanc annonce le doublon que la création refuserait.
        errors.addAll(uniqueness.conflicts(type, DossierStatus.DRAFT, ruled.fields(),
                errors.stream().map(FieldValidationError::field).toList(), null));
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
