package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.PurgeOutboxOrigin;
import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.model.RetentionActors;
import com.flowforge.domain.dossier.model.TenantRetentionSettings;
import com.flowforge.domain.dossier.port.in.EvaluateDossierRetentionUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import com.flowforge.domain.dossier.port.out.TenantRetentionSettingsRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class RetentionPersistenceIT {

    private static final String TENANT = "tenant_dev";

    @Autowired
    private TenantRetentionSettingsRepository settingsRepository;

    @Autowired
    private DossierTypeRepository typeRepository;

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private PurgeOutboxRepository purgeOutboxRepository;

    @Autowired
    private EvaluateDossierRetentionUseCase evaluateDossierRetentionUseCase;

    @Test
    void settingsAndTypeRetention_roundTrip() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        TenantRetentionSettings settings =
                new TenantRetentionSettings(TENANT, 45, now, "admin-p8");
        settingsRepository.save(settings);

        assertThat(settingsRepository.findByTenantId(TENANT))
                .get()
                .extracting(TenantRetentionSettings::defaultRetentionDays)
                .isEqualTo(45);

        String code = "RET" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DossierType neverType = new DossierType(
                UUID.randomUUID(),
                TENANT,
                code,
                "Label " + code,
                "wf-" + code.toLowerCase(),
                true,
                false,
                List.of(),
                null,
                true, null,
                "system", "system");
        typeRepository.save(neverType);

        assertThat(typeRepository.findByCode(TENANT, code))
                .get()
                .satisfies(loaded -> {
                    assertThat(loaded.retentionNever()).isTrue();
                    assertThat(loaded.retentionDays()).isNull();
                });
    }

    @Test
    void purgeOutbox_insertIsIdempotentOnExists() {
        UUID dossierId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        PurgeOutboxRecord first = PurgeOutboxRecord.pending(
                UUID.randomUUID(),
                dossierId,
                "DOSSIER_PURGE_REQUESTED",
                PurgeOutboxOrigin.RETENTION,
                "{\"dossierId\":\"" + dossierId + "\"}",
                now);
        purgeOutboxRepository.insert(first);
        assertThat(purgeOutboxRepository.existsByDossierId(dossierId)).isTrue();

        PurgeOutboxRecord duplicate = PurgeOutboxRecord.pending(
                UUID.randomUUID(),
                dossierId,
                "DOSSIER_PURGE_REQUESTED",
                PurgeOutboxOrigin.RETENTION,
                "{\"dossierId\":\"" + dossierId + "\"}",
                now);
        purgeOutboxRepository.insert(duplicate);
        assertThat(purgeOutboxRepository.existsByDossierId(dossierId)).isTrue();
    }

    @Test
    void evaluate_seededExpiredCloture_archivesAndEnqueuesOutbox() {
        Instant now = Instant.now();
        settingsRepository.save(new TenantRetentionSettings(TENANT, 30, now, "admin-p8"));

        String code = "EXP" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        typeRepository.save(DossierType.inherit(
                UUID.randomUUID(), TENANT, code, "Label " + code, "wf-" + code.toLowerCase(), true, false, List.of()));

        Instant closedAt = now.minus(31, ChronoUnit.DAYS);
        UUID dossierId = UUID.randomUUID();
        Dossier closed = new Dossier(
                dossierId,
                TENANT,
                "DOS-" + dossierId.toString().replace("-", "").substring(0, 12),
                code,
                "Expired retention candidate",
                DossierStatus.CLOTURE,
                Map.of(),
                null,
                0,
                "creator",
                closedAt,
                "closer",
                closedAt,
                "closer",
                closedAt,
                null);
        dossierRepository.save(closed);

        EvaluateDossierRetentionUseCase.EvaluationResult result =
                evaluateDossierRetentionUseCase.evaluate(now, TENANT, 100);

        assertThat(result.settingsAbsent()).isFalse();
        assertThat(result.archived()).isGreaterThanOrEqualTo(1);

        Dossier archived = dossierRepository.findById(dossierId).orElseThrow();
        assertThat(archived.status()).isEqualTo(DossierStatus.ARCHIVE);
        assertThat(archived.purgeEligibleAt()).isNotNull();
        assertThat(archived.updatedBy()).isEqualTo(RetentionActors.JOB);
        assertThat(purgeOutboxRepository.existsByDossierId(dossierId)).isTrue();
    }
}
