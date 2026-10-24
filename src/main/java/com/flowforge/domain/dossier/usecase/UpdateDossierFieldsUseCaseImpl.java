package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.DossierFieldRules;
import com.flowforge.domain.dossier.DossierUniquenessCheck;
import com.flowforge.domain.dossier.DossierFieldMerger;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierConflictException;
import com.flowforge.domain.dossier.model.DossierFieldValidationException;
import com.flowforge.domain.dossier.model.DossierFieldValidator;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.in.UpdateDossierFieldsUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class UpdateDossierFieldsUseCaseImpl implements UpdateDossierFieldsUseCase {

    private final DossierRepository dossierRepository;
    private final DossierTypeRepository typeRepository;
    private final DossierAccessPolicy accessPolicy;
    private final DossierFieldValidator fieldValidator;
    private final DossierFieldRules fieldRules;
    private final DossierUniquenessCheck uniqueness;

    public UpdateDossierFieldsUseCaseImpl(
            DossierRepository dossierRepository, DossierTypeRepository typeRepository, DossierAccessPolicy accessPolicy) {
        this(dossierRepository, typeRepository, accessPolicy, new DossierFieldValidator());
    }

    public UpdateDossierFieldsUseCaseImpl(
            DossierRepository dossierRepository,
            DossierTypeRepository typeRepository,
            DossierAccessPolicy accessPolicy,
            DossierFieldValidator fieldValidator) {
        this(dossierRepository, typeRepository, accessPolicy, fieldValidator, DossierFieldRules.disabled());
    }

    public UpdateDossierFieldsUseCaseImpl(
            DossierRepository dossierRepository,
            DossierTypeRepository typeRepository,
            DossierAccessPolicy accessPolicy,
            DossierFieldValidator fieldValidator,
            DossierFieldRules fieldRules) {
        this(dossierRepository, typeRepository, accessPolicy, fieldValidator, fieldRules, DossierUniquenessCheck.disabled());
    }

    public UpdateDossierFieldsUseCaseImpl(
            DossierRepository dossierRepository,
            DossierTypeRepository typeRepository,
            DossierAccessPolicy accessPolicy,
            DossierFieldValidator fieldValidator,
            DossierFieldRules fieldRules,
            DossierUniquenessCheck uniqueness) {
        this.uniqueness = Objects.requireNonNull(uniqueness, "uniqueness");
        this.fieldRules = Objects.requireNonNull(fieldRules, "fieldRules");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.fieldValidator = Objects.requireNonNull(fieldValidator, "fieldValidator");
    }

    @Override
    public UpdateDossierFieldsResult update(UpdateDossierFieldsCommand command, AccessContext ctx) {
        Objects.requireNonNull(command, "command");
        if (command.fieldsPatch() == null || command.fieldsPatch().isEmpty()) {
            throw new IllegalArgumentException("fieldsPatch must not be empty");
        }

        Dossier dossier = dossierRepository.findById(command.dossierId())
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + command.dossierId()));

        accessPolicy.assertCanEdit(dossier, ctx);

        if (dossier.status().blocksMutation()) {
            throw new DossierConflictException("Dossier fields cannot be edited in status " + dossier.status());
        }
        if (command.expectedVersion() != null && command.expectedVersion() != dossier.version()) {
            throw new DossierConflictException(
                    "Expected version " + command.expectedVersion() + " but found " + dossier.version());
        }

        DossierType type = typeRepository.findByCode(dossier.tenantId(), dossier.dossierTypeCode())
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier type not found: " + dossier.tenantId() + "/" + dossier.dossierTypeCode()));

        Map<String, Object> before = dossier.fields();
        Map<String, Object> merged = DossierFieldMerger.merge(before, command.fieldsPatch());

        // TODO §80 : champs calculés, visibilité et obligation conditionnelles (aucun effet sans règle déclarée).
        var ruled = fieldRules.apply(type.fieldDefinitions(), merged, command.fieldsPatch().keySet());
        merged = ruled.fields();
        var errors = new java.util.ArrayList<>(
                fieldValidator.validate(ruled.effectiveDefinitions(), merged, type.allowUnknownFields()));
        errors.addAll(ruled.errors());
        // TODO §102 (U8) : doublon signalé avec les autres erreurs ; le dossier lui-même ne se gêne pas.
        errors.addAll(uniqueness.conflicts(type, dossier.status(), merged,
                errors.stream().map(com.flowforge.domain.dossier.model.FieldValidationError::field).toList(),
                dossier.id()));
        if (!errors.isEmpty()) {
            throw new DossierFieldValidationException(errors);
        }
        // TODO §76 : normalisé AVANT la détection des champs sensibles modifiés (le stocké l'est déjà : sinon un montant
        // inchangé mais écrit « 12.5 » serait vu comme modifié).
        merged = fieldValidator.normalize(type.fieldDefinitions(), merged);

        List<String> sensitiveFieldsChanged =
                DossierFieldMerger.changedSensitiveFieldNames(type.fieldDefinitions(), before, merged);

        Dossier updated = dossier.withFields(merged, command.updatedBy(), Instant.now());
        Dossier saved = dossierRepository.save(updated);
        return new UpdateDossierFieldsResult(saved, sensitiveFieldsChanged);
    }
}
