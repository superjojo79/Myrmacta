package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.DossierTypeSchemaIncompatibleException;
import com.flowforge.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.util.Objects;

public final class UpdateDossierTypeUseCaseImpl implements UpdateDossierTypeUseCase {

    private final DossierTypeRepository repository;
    private final TenantAdminGuard tenantAdminGuard;
    private final CheckDossierTypeSchemaCompatibilityUseCase checkCompatibilityUseCase;

    public UpdateDossierTypeUseCaseImpl(
            DossierTypeRepository repository,
            TenantAdminGuard tenantAdminGuard,
            CheckDossierTypeSchemaCompatibilityUseCase checkCompatibilityUseCase) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
        this.checkCompatibilityUseCase =
                Objects.requireNonNull(checkCompatibilityUseCase, "checkCompatibilityUseCase");
    }

    @Override
    public DossierType update(UpdateDossierTypeCommand command, AccessContext.User actor) {
        Objects.requireNonNull(command, "command");
        tenantAdminGuard.requireTenantAdminOrApiService(actor);

        DossierType existing = repository.findByCode(command.tenantId(), command.code())
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier type not found: " + command.tenantId() + "/" + command.code()));

        // Structural validation (D-NAME-UNIQUE, retention, SLA — all in-memory, no I/O) runs first
        // so a malformed request fails fast with 400 without paying for the compatibility check's
        // dossier query below.
        DossierType updated = existing.withUpdate(
                command.label(),
                command.workflowKey(),
                command.active(),
                command.allowUnknownFields(),
                command.fieldDefinitions(),
                command.retentionDays(),
                command.retentionNever(),
                command.slaTargetHours(),
                actor.userId());

        // D-UI-SCHEMA-COMPAT=block — single source of truth for both REST and MCP callers
        // (D-DESIGN-CHECK-PLACEMENT); rejects before any write when the new schema would make
        // existing dossiers of this type invalid.
        var compatibility = checkCompatibilityUseCase.check(new CheckDossierTypeSchemaCompatibilityUseCase.Query(
                command.tenantId(), command.code(), command.fieldDefinitions(), command.allowUnknownFields()));
        if (!compatibility.compatible()) {
            throw new DossierTypeSchemaIncompatibleException(
                    compatibility.incompatibleCount(), compatibility.sampleReferences());
        }

        repository.save(updated);
        return updated;
    }
}
