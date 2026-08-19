package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotEligibleForPurgeException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.PurgeOutboxOrigin;
import com.flowforge.domain.dossier.model.PurgeOutboxPayloads;
import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.port.in.EnqueueDossierPurgeUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class EnqueueDossierPurgeUseCaseImpl implements EnqueueDossierPurgeUseCase {

    private static final String EVENT_TYPE = "DOSSIER_PURGE_REQUESTED";

    private final DossierRepository dossierRepository;
    private final PurgeOutboxRepository purgeOutboxRepository;
    private final TenantAdminGuard tenantAdminGuard;

    public EnqueueDossierPurgeUseCaseImpl(
            DossierRepository dossierRepository,
            PurgeOutboxRepository purgeOutboxRepository,
            TenantAdminGuard tenantAdminGuard) {
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.purgeOutboxRepository = Objects.requireNonNull(purgeOutboxRepository, "purgeOutboxRepository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
    }

    @Override
    public Result enqueue(UUID dossierId, AccessContext.User actor) {
        Objects.requireNonNull(dossierId, "dossierId");
        tenantAdminGuard.requireTenantAdmin(actor);
        Dossier dossier = dossierRepository
                .findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        if (dossier.status() != DossierStatus.CLOTURE && dossier.status() != DossierStatus.ARCHIVE) {
            throw new DossierNotEligibleForPurgeException(
                    "Purge enqueue requires CLOTURE or ARCHIVE, was " + dossier.status());
        }
        if (purgeOutboxRepository.existsByDossierId(dossierId)) {
            return Result.ALREADY_EXISTS;
        }
        Instant now = Instant.now();
        Instant eligibleAt = dossier.purgeEligibleAt() != null ? dossier.purgeEligibleAt() : now;
        purgeOutboxRepository.insert(PurgeOutboxRecord.pending(
                UUID.randomUUID(),
                dossierId,
                EVENT_TYPE,
                PurgeOutboxOrigin.ADMIN,
                PurgeOutboxPayloads.json(dossier.tenantId(), dossierId, dossier.dossierTypeCode(), eligibleAt),
                now));
        return Result.CREATED;
    }
}
