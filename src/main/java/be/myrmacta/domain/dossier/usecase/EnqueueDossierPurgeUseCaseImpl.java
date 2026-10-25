package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotEligibleForPurgeException;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.PurgeOutboxOrigin;
import be.myrmacta.domain.dossier.model.PurgeOutboxPayloads;
import be.myrmacta.domain.dossier.model.PurgeOutboxRecord;
import be.myrmacta.domain.dossier.port.in.EnqueueDossierPurgeUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.PurgeOutboxRepository;
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
