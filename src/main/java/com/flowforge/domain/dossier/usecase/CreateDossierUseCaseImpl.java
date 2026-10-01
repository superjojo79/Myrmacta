package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.DossierFieldRules;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierFieldValidator;
import com.flowforge.domain.dossier.model.DossierFieldValidationException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.InactiveDossierTypeException;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.out.DossierReferenceGenerator;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CreateDossierUseCaseImpl implements CreateDossierUseCase {

    private final DossierTypeRepository typeRepository;
    private final DossierRepository dossierRepository;
    private final DossierReferenceGenerator referenceGenerator;
    private final DossierAccessPolicy accessPolicy;
    private final DossierFieldValidator fieldValidator;
    private final DossierFieldRules fieldRules;

    public CreateDossierUseCaseImpl(
            DossierTypeRepository typeRepository,
            DossierRepository dossierRepository,
            DossierReferenceGenerator referenceGenerator,
            DossierAccessPolicy accessPolicy) {
        this(typeRepository, dossierRepository, referenceGenerator, accessPolicy, new DossierFieldValidator());
    }

    public CreateDossierUseCaseImpl(
            DossierTypeRepository typeRepository,
            DossierRepository dossierRepository,
            DossierReferenceGenerator referenceGenerator,
            DossierAccessPolicy accessPolicy,
            DossierFieldValidator fieldValidator) {
        this(typeRepository, dossierRepository, referenceGenerator, accessPolicy, fieldValidator, DossierFieldRules.disabled());
    }

    public CreateDossierUseCaseImpl(
            DossierTypeRepository typeRepository,
            DossierRepository dossierRepository,
            DossierReferenceGenerator referenceGenerator,
            DossierAccessPolicy accessPolicy,
            DossierFieldValidator fieldValidator,
            DossierFieldRules fieldRules) {
        this.fieldRules = Objects.requireNonNull(fieldRules, "fieldRules");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.referenceGenerator = Objects.requireNonNull(referenceGenerator, "referenceGenerator");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.fieldValidator = Objects.requireNonNull(fieldValidator, "fieldValidator");
    }

    @Override
    public Dossier create(CreateDossierCommand command, AccessContext ctx) {
        Objects.requireNonNull(command, "command");
        accessPolicy.assertCanEditType(command.tenantId(), command.dossierTypeCode(), ctx);

        DossierType type = typeRepository.findByCode(command.tenantId(), command.dossierTypeCode())
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier type not found: " + command.tenantId() + "/" + command.dossierTypeCode()));
        if (!type.active()) {
            throw new InactiveDossierTypeException(type.code());
        }

        var fields = fieldValidator.applyDefaults(type.fieldDefinitions(), command.fields());
        // TODO §80 : champs calculés, visibilité et obligation conditionnelles (aucun effet sans règle déclarée).
        var ruled = fieldRules.apply(type.fieldDefinitions(), fields,
                command.fields() == null ? java.util.Set.of() : command.fields().keySet());
        fields = ruled.fields();
        var errors = new java.util.ArrayList<>(
                fieldValidator.validate(ruled.effectiveDefinitions(), fields, type.allowUnknownFields()));
        errors.addAll(ruled.errors());
        if (!errors.isEmpty()) {
            throw new DossierFieldValidationException(errors);
        }
        // TODO §76 : forme canonique des valeurs structurées (montant à l'échelle de sa devise, référence nettoyée, lignes).
        fields = fieldValidator.normalize(type.fieldDefinitions(), fields);

        Instant now = Instant.now();
        Dossier dossier = new Dossier(
                UUID.randomUUID(),
                command.tenantId(),
                referenceGenerator.generate(command.tenantId(), command.dossierTypeCode()),
                command.dossierTypeCode(),
                command.label(),
                DossierStatus.DRAFT,
                fields,
                null,
                0,
                command.createdBy(),
                now,
                command.createdBy(),
                now,
                null,
                null,
                null);
        return dossierRepository.save(dossier);
    }
}
