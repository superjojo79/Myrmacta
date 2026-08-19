package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.model.PurgeOutboxStatus;
import com.flowforge.domain.dossier.model.RetentionActors;
import com.flowforge.domain.dossier.model.RetentionPolicyResolver;
import com.flowforge.domain.dossier.model.TenantRetentionSettings;
import com.flowforge.domain.dossier.port.in.EvaluateDossierRetentionUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import com.flowforge.domain.dossier.port.out.TenantRetentionSettingsRepository;
import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * D-SCAN batch: reads up to {@code batchSize} {@code CLOTURE} dossiers, resolves the effective
 * retention policy in memory, and archives expired candidates + enqueues a purge outbox row —
 * both in a single call, so the caller (job) can wrap it in one transaction (D-ARCHIVE-PATH).
 * Never routes through {@code UpdateDossierStatusUseCase}.
 */
public final class EvaluateDossierRetentionUseCaseImpl implements EvaluateDossierRetentionUseCase {

    private static final String EVENT_TYPE = "DOSSIER_PURGE_REQUESTED";

    private final DossierRepository dossierRepository;
    private final DossierTypeRepository typeRepository;
    private final TenantRetentionSettingsRepository settingsRepository;
    private final PurgeOutboxRepository purgeOutboxRepository;
    private final RetentionPolicyResolver policyResolver;

    public EvaluateDossierRetentionUseCaseImpl(
            DossierRepository dossierRepository,
            DossierTypeRepository typeRepository,
            TenantRetentionSettingsRepository settingsRepository,
            PurgeOutboxRepository purgeOutboxRepository,
            RetentionPolicyResolver policyResolver) {
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository");
        this.purgeOutboxRepository = Objects.requireNonNull(purgeOutboxRepository, "purgeOutboxRepository");
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
    }

    @Override
    public EvaluationResult evaluate(String tenantId, int batchSize) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (batchSize <= 0) {
            return new EvaluationResult(0, 0);
        }

        // D-BOOTSTRAP: no tenant settings configured yet ⇒ no-op, not an error.
        Optional<TenantRetentionSettings> settings = settingsRepository.findByTenantId(tenantId);
        if (settings.isEmpty()) {
            return new EvaluationResult(0, 0);
        }

        List<Dossier> candidates = dossierRepository.findClosedForRetention(batchSize);
        Instant now = Instant.now();
        int archived = 0;
        for (Dossier dossier : candidates) {
            if (archiveIfExpired(dossier, settings.get(), now)) {
                archived++;
            }
        }
        return new EvaluationResult(candidates.size(), archived);
    }

    private boolean archiveIfExpired(Dossier dossier, TenantRetentionSettings settings, Instant now) {
        // D-FORCE: defense in depth — even though findClosedForRetention only reads CLOTURE rows,
        // never re-touch a dossier that isn't (or is no longer) CLOTURE.
        if (dossier.status() != DossierStatus.CLOTURE || dossier.closedAt() == null) {
            return false;
        }
        if (purgeOutboxRepository.existsByDossierId(dossier.id())) {
            // Already archived + enqueued by a previous run — idempotent no-op (D-OUTBOX-UNIQ).
            return false;
        }
        Optional<DossierType> type = typeRepository.findByCode(dossier.tenantId(), dossier.dossierTypeCode());
        if (type.isEmpty()) {
            return false;
        }
        OptionalInt effectiveDays = policyResolver.effectiveDays(type.get(), settings);
        if (effectiveDays.isEmpty()) {
            // NEVER — no expiration.
            return false;
        }
        Instant expiration = dossier.closedAt().plus(Period.ofDays(effectiveDays.getAsInt()));
        if (expiration.isAfter(now)) {
            return false;
        }

        Dossier archived = dossier.withRetentionArchive(RetentionActors.JOB, now);
        Dossier saved = dossierRepository.save(archived);
        purgeOutboxRepository.insert(new PurgeOutboxRecord(
                UUID.randomUUID(),
                saved.id(),
                EVENT_TYPE,
                PurgeOutboxStatus.PENDING,
                payloadJson(saved, type.get()),
                now));
        return true;
    }

    private static String payloadJson(Dossier dossier, DossierType type) {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        sb.append("\"tenantId\":\"").append(escape(dossier.tenantId())).append("\",");
        sb.append("\"dossierId\":\"").append(dossier.id()).append("\",");
        sb.append("\"typeCode\":\"").append(escape(type.code())).append("\",");
        sb.append("\"purgeEligibleAt\":\"").append(dossier.purgeEligibleAt()).append("\"");
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
