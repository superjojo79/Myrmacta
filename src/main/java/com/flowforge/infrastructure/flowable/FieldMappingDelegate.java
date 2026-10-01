package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierFieldValidationException;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierFieldsUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.expression.model.ExpressionResult;
import com.flowforge.domain.expression.port.out.ExpressionEnginePort;
import com.flowforge.domain.workflow.palette.BlockAttributeRules;
import com.flowforge.domain.workflow.palette.FieldMappingSpec;
import com.flowforge.infrastructure.config.TenantProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §78 (D3b) — brique « Enregistrer des valeurs dans le dossier » : évalue chaque expression de
 * {@code flowable:fieldMapping} (attribut littéral, jamais une variable) sur le document racine borné de LC2 et écrit les
 * résultats <b>en un seul patch</b> par le chemin métier {@link UpdateDossierFieldsUseCase} — validation de type,
 * normalisation, versionnage : tout ou rien, jamais un contournement du {@code DossierFieldValidator}.
 *
 * <p>Une expression sans valeur ou {@code null} fait échouer l'activité en nommant le champ (cohérent avec D-EXPR-UNDEFINED :
 * l'auteur borne le cas dans l'expression). Les valeurs ne sont jamais journalisées (elles peuvent être sensibles).
 */
@Component("fieldMappingTask")
class FieldMappingDelegate implements JavaDelegate {

    private static final Logger LOG = LoggerFactory.getLogger(FieldMappingDelegate.class);
    private static final String DELEGATE = "fieldMappingTask";

    private final ExpressionEnginePort engine;
    private final GetDossierUseCase getDossier;
    private final DossierTypeRepository dossierTypes;
    private final UpdateDossierFieldsUseCase updateFields;
    private final TenantProperties tenantProperties;
    private final ObjectMapper objectMapper;

    FieldMappingDelegate(
            ExpressionEnginePort engine,
            GetDossierUseCase getDossier,
            DossierTypeRepository dossierTypes,
            UpdateDossierFieldsUseCase updateFields,
            TenantProperties tenantProperties,
            ObjectMapper objectMapper) {
        this.engine = engine;
        this.getDossier = getDossier;
        this.dossierTypes = dossierTypes;
        this.updateFields = updateFields;
        this.tenantProperties = tenantProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String activity = execution.getCurrentActivityId();
        String raw = DeployedActivityParameters.getLiteral(execution, "fieldMapping");
        if (raw == null) {
            throw new IllegalStateException("fieldMappingTask requires the literal attribute flowable:fieldMapping");
        }
        // Défense en profondeur : même validation qu'au déploiement (un BPMN peut arriver sans passer par le parseur).
        BlockAttributeRules.validate(activity, DELEGATE, Map.of("fieldMapping", raw));
        List<FieldMappingSpec.Entry> entries =
                FieldMappingSpec.parse(raw, "BPMN attribute flowable:fieldMapping on " + activity);

        Object dossierId = execution.getVariable("dossierId");
        if (dossierId == null) {
            throw new IllegalStateException("fieldMappingTask needs a dossier (process variable dossierId)");
        }
        BusinessExpressionContext.Evaluated evaluated = BusinessExpressionContext.forExecution(
                execution, getDossier, dossierTypes, tenantProperties, objectMapper);

        Map<String, Object> patch = new LinkedHashMap<>();
        for (FieldMappingSpec.Entry entry : entries) {
            ExpressionResult result = engine.evaluate(entry.expression(), evaluated.context());
            if (!result.defined() || result.value() == null) {
                throw new IllegalStateException("mapping of field '" + entry.field() + "' in " + activity
                        + " produced no value; give it a default (e.g. `a.b ? a.b : 0`)");
            }
            patch.put(entry.field(), result.value());
        }
        try {
            updateFields.update(
                    new UpdateDossierFieldsUseCase.UpdateDossierFieldsCommand(
                            evaluated.dossier().id(), patch, "workflow", null),
                    AccessContext.system("flowable-field-mapping"));
        } catch (DossierFieldValidationException e) {
            // Les messages du validateur ne recopient jamais la valeur : on les remonte pour que l'auteur sache quel champ corriger.
            String detail = e.errors().stream().limit(10)
                    .map(err -> err.field() + " (" + err.code() + ": " + err.message() + ")")
                    .collect(java.util.stream.Collectors.joining("; "));
            throw new IllegalStateException(
                    "the dossier rejected the values mapped by " + activity + " (nothing was written): " + detail);
        }
        LOG.debug("{} field(s) mapped by activity {}", patch.size(), activity);
    }
}
