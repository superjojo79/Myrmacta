package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.DossierFieldRules;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.in.CreateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.util.Objects;
import java.util.UUID;

public final class CreateDossierTypeUseCaseImpl implements CreateDossierTypeUseCase {

    private final DossierTypeRepository repository;
    private final TenantAdminGuard tenantAdminGuard;
    private final DossierFieldRules fieldRules;

    public CreateDossierTypeUseCaseImpl(
            DossierTypeRepository repository, TenantAdminGuard tenantAdminGuard) {
        this(repository, tenantAdminGuard, DossierFieldRules.disabled());
    }

    public CreateDossierTypeUseCaseImpl(
            DossierTypeRepository repository, TenantAdminGuard tenantAdminGuard, DossierFieldRules fieldRules) {
        this.fieldRules = Objects.requireNonNull(fieldRules, "fieldRules");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
    }

    @Override
    public DossierType create(CreateDossierTypeCommand command, AccessContext.User actor) {
        Objects.requireNonNull(command, "command");
        tenantAdminGuard.requireTenantAdminOrApiService(actor);

        var type = new DossierType(
                UUID.randomUUID(),
                command.tenantId(),
                command.code(),
                command.label(),
                command.workflowKey(),
                command.active(),
                command.allowUnknownFields(),
                command.fieldDefinitions(),
                command.retentionDays(),
                command.retentionNever(),
                command.slaTargetHours(),
                command.documentTemplateKeys(),
                actor.userId(),
                actor.userId());
        fieldRules.validateDefinitions(type.fieldDefinitions()); // TODO §80 : règles (calcul, visibilité, obligation)
        repository.save(type);
        return type;
    }
}
