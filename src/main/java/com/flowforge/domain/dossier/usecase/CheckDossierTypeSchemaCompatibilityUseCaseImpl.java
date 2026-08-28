package com.flowforge.domain.dossier.usecase;

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

    private final DossierRepository dossierRepository;
    private final DossierFieldValidator fieldValidator;

    public CheckDossierTypeSchemaCompatibilityUseCaseImpl(
            DossierRepository dossierRepository, DossierFieldValidator fieldValidator) {
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.fieldValidator = Objects.requireNonNull(fieldValidator, "fieldValidator");
    }

    @Override
    public Result check(Query query) {
        Objects.requireNonNull(query, "query");
        List<Dossier> dossiers = dossierRepository.findByDossierTypeCode(query.tenantId(), query.dossierTypeCode());

        List<String> incompatibleReferences = new ArrayList<>();
        for (Dossier dossier : dossiers) {
            var errors = fieldValidator.validate(
                    query.newFieldDefinitions(), dossier.fields(), query.allowUnknownFields());
            if (!errors.isEmpty()) {
                incompatibleReferences.add(dossier.reference());
            }
        }

        return new Result(
                incompatibleReferences.size(),
                incompatibleReferences.stream().limit(SAMPLE_LIMIT).toList());
    }
}
