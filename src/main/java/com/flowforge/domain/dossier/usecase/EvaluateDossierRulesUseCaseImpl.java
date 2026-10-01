package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.DossierFieldRules;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.port.in.EvaluateDossierRulesUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EvaluateDossierRulesUseCaseImpl implements EvaluateDossierRulesUseCase {

    private final DossierTypeRepository typeRepository;
    private final DossierAccessPolicy accessPolicy;
    private final DossierFieldRules fieldRules;

    public EvaluateDossierRulesUseCaseImpl(
            DossierTypeRepository typeRepository, DossierAccessPolicy accessPolicy, DossierFieldRules fieldRules) {
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.fieldRules = Objects.requireNonNull(fieldRules, "fieldRules");
    }

    @Override
    public Result evaluate(String tenantId, String dossierTypeCode, Map<String, Object> fields, AccessContext ctx) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
        Objects.requireNonNull(ctx, "ctx");
        accessPolicy.assertCanEditType(tenantId, dossierTypeCode, ctx);
        DossierType type = typeRepository.findByCode(tenantId, dossierTypeCode)
                .orElseThrow(() -> new DossierNotFoundException("Dossier type not found: " + tenantId + "/" + dossierTypeCode));
        List<FieldDefinition> definitions = type.fieldDefinitions();
        if (!DossierFieldRules.hasRules(definitions)) {
            return new Result(Map.of(), java.util.Set.of(), java.util.Set.of(), List.of());
        }
        // Les champs sensibles saisis ne sont jamais transmis aux règles (même politique que l'enregistrement).
        Map<String, Object> usable = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((name, value) -> {
                boolean sensitive = definitions.stream().anyMatch(d -> d.name().equals(name) && d.isSensitive());
                if (!sensitive && value != null) {
                    usable.put(name, value);
                }
            });
        }
        DossierFieldRules.Outcome outcome = fieldRules.apply(definitions, usable, java.util.Set.of());
        Map<String, Object> computed = new LinkedHashMap<>();
        for (FieldDefinition d : definitions) {
            if (d.hasFormula() && outcome.fields().containsKey(d.name())) {
                computed.put(d.name(), outcome.fields().get(d.name()));
            }
        }
        List<RuleError> errors = outcome.errors().stream().map(e -> new RuleError(e.field(), e.message())).toList();
        return new Result(computed, outcome.hidden(), outcome.requiredByRule(), errors);
    }
}
