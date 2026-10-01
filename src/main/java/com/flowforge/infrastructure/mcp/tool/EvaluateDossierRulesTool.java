package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.dossier.port.in.EvaluateDossierRulesUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * TODO §80 (D1, règles) — {@code evaluate_dossier_rules} : aperçu, sans écriture, des champs calculés, des champs sans objet et
 * des champs rendus obligatoires d'un type de dossier pour des valeurs données. Même moteur et mêmes règles que l'enregistrement ;
 * les champs sensibles fournis sont ignorés. Sert à mettre au point les règles d'un type (voir {@code define_dossier_type}).
 */
@Component
public class EvaluateDossierRulesTool {

    private final EvaluateDossierRulesUseCase useCase;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;

    public EvaluateDossierRulesTool(
            EvaluateDossierRulesUseCase useCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "evaluate_dossier_rules",
            description = "Preview, without writing anything, the rules of a dossier type for the given field values: the computed "
                    + "fields' values (formula), the fields that are not applicable (visibleWhen is false) and the fields made "
                    + "required by a rule (requiredWhen is true). Same engine and bounds as saving a dossier. Sensitive fields "
                    + "in the input are ignored: rules never read them.")
    public Result evaluateDossierRules(
            @McpToolParam(required = true, description = "Dossier type code") String dossierTypeCode,
            @McpToolParam(required = false, description = "Current field values (name -> value)") Map<String, Object> fields) {
        EvaluateDossierRulesUseCase.Result result = useCase.evaluate(
                tenantProperties.id(), dossierTypeCode, fields, accessContextFactory.currentActor());
        return new Result(
                result.computed(),
                result.hidden().stream().sorted().toList(),
                result.requiredByRule().stream().sorted().toList(),
                result.errors().stream().map(e -> new RuleError(e.field(), e.message())).toList());
    }

    public record RuleError(String field, String message) {}

    public record Result(Map<String, Object> computed, List<String> hidden, List<String> requiredByRule, List<RuleError> errors) {}
}
