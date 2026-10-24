package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.DossierUniqueKeys;
import com.flowforge.domain.dossier.model.UniqueDuplicate;
import java.util.LinkedHashSet;
import java.util.Set;
import com.flowforge.domain.dossier.DossierFieldRules;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierFieldValidator;
import com.flowforge.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CheckDossierTypeSchemaCompatibilityUseCaseImpl
        implements CheckDossierTypeSchemaCompatibilityUseCase {

    private static final int SAMPLE_LIMIT = 5;
    /** TODO §102 — nombre maximal de valeurs en double renvoyées (aperçu ; le nombre de dossiers en cause reste complet). */
    private static final int DUPLICATE_LIMIT = 20;

    private final DossierRepository dossierRepository;
    private final DossierFieldValidator fieldValidator;
    private final DossierFieldRules fieldRules;
    private final DossierUniqueKeys uniqueKeys;

    public CheckDossierTypeSchemaCompatibilityUseCaseImpl(
            DossierRepository dossierRepository, DossierFieldValidator fieldValidator) {
        this(dossierRepository, fieldValidator, DossierFieldRules.disabled());
    }

    public CheckDossierTypeSchemaCompatibilityUseCaseImpl(
            DossierRepository dossierRepository, DossierFieldValidator fieldValidator, DossierFieldRules fieldRules) {
        this.fieldRules = Objects.requireNonNull(fieldRules, "fieldRules");
        this.uniqueKeys = new DossierUniqueKeys(fieldRules);
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.fieldValidator = Objects.requireNonNull(fieldValidator, "fieldValidator");
    }

    @Override
    public Result check(Query query) {
        Objects.requireNonNull(query, "query");
        List<Dossier> dossiers = dossierRepository.findByDossierTypeCode(query.tenantId(), query.dossierTypeCode());

        List<String> incompatibleReferences = new ArrayList<>();
        for (Dossier dossier : dossiers) {
            // TODO §80 : les règles (obligation conditionnelle, champs sans objet, calculs) comptent comme le reste du schéma.
            var ruled = fieldRules.apply(query.newFieldDefinitions(), dossier.fields(), java.util.Set.of());
            var errors = new ArrayList<>(fieldValidator.validate(
                    ruled.effectiveDefinitions(), ruled.fields(), query.allowUnknownFields()));
            errors.addAll(ruled.errors());
            if (!errors.isEmpty()) {
                incompatibleReferences.add(dossier.reference());
            }
        }

        // TODO §102 (U10, D-UNIQUE-EXISTING) : un champ unique ne s'active pas tant que des dossiers ouverts en partagent la
        // valeur ; ces dossiers sont incompatibles au même titre que les autres.
        List<UniqueDuplicate> duplicates = uniqueKeys.duplicates(query.newFieldDefinitions(), dossiers);
        Set<String> incompatible = new LinkedHashSet<>(incompatibleReferences);
        duplicates.forEach(duplicate -> incompatible.addAll(duplicate.references()));

        return new Result(
                incompatible.size(),
                incompatible.stream().limit(SAMPLE_LIMIT).toList(),
                duplicates.stream().limit(DUPLICATE_LIMIT).toList());
    }
}
