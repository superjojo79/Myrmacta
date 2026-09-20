package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.PurgeOutboxOrigin;
import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.model.RetentionActors;
import com.flowforge.domain.dossier.model.RetentionPolicyResolver;
import com.flowforge.domain.dossier.model.TenantRetentionSettings;
import com.flowforge.domain.dossier.port.in.EvaluateDossierRetentionUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import com.flowforge.domain.dossier.port.out.TenantRetentionSettingsRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EvaluateDossierRetentionUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.now();

    private DossierRepository dossierRepository;
    private DossierTypeRepository typeRepository;
    private TenantRetentionSettingsRepository settingsRepository;
    private PurgeOutboxRepository purgeOutboxRepository;
    private EvaluateDossierRetentionUseCase useCase;

    @BeforeEach
    void setUp() {
        dossierRepository = mock(DossierRepository.class);
        typeRepository = mock(DossierTypeRepository.class);
        settingsRepository = mock(TenantRetentionSettingsRepository.class);
        purgeOutboxRepository = mock(PurgeOutboxRepository.class);
        useCase = new EvaluateDossierRetentionUseCaseImpl(
                dossierRepository,
                typeRepository,
                settingsRepository,
                purgeOutboxRepository,
                new RetentionPolicyResolver());

        when(dossierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purgeOutboxRepository.existsByDossierId(any())).thenReturn(false);
    }

    @Test
    void evaluate_settingsAbsentIsNoOp() {
        when(settingsRepository.findByTenantId(TENANT)).thenReturn(Optional.empty());

        EvaluateDossierRetentionUseCase.EvaluationResult result = useCase.evaluate(TENANT, 100);

        assertThat(result.scanned()).isZero();
        assertThat(result.archived()).isZero();
        assertThat(result.settingsAbsent()).isTrue();
        verify(dossierRepository, never()).findClosedForRetention(anyString(), anyInt());
        verify(dossierRepository, never()).save(any());
        verify(purgeOutboxRepository, never()).insert(any());
    }

    @Test
    void evaluate_expiredClotureDossierIsArchivedWithOutbox() {
        settings(30);
        Dossier expired = dossier("CLAIM", NOW.minus(java.time.Duration.ofDays(31)));
        when(dossierRepository.findClosedForRetention(TENANT, 100)).thenReturn(List.of(expired));
        when(typeRepository.findByCode(TENANT, "CLAIM")).thenReturn(Optional.of(inheritType("CLAIM")));

        EvaluateDossierRetentionUseCase.EvaluationResult result = evaluateAt(NOW);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.archived()).isEqualTo(1);

        ArgumentCaptor<Dossier> savedCaptor = ArgumentCaptor.forClass(Dossier.class);
        verify(dossierRepository).save(savedCaptor.capture());
        Dossier saved = savedCaptor.getValue();
        assertThat(saved.status()).isEqualTo(DossierStatus.ARCHIVE);
        assertThat(saved.purgeEligibleAt()).isNotNull();
        assertThat(saved.updatedBy()).isEqualTo(RetentionActors.JOB);

        ArgumentCaptor<PurgeOutboxRecord> outboxCaptor = ArgumentCaptor.forClass(PurgeOutboxRecord.class);
        verify(purgeOutboxRepository).insert(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().dossierId()).isEqualTo(expired.id());
        assertThat(outboxCaptor.getValue().eventType()).isEqualTo("DOSSIER_PURGE_REQUESTED");
        assertThat(outboxCaptor.getValue().origin()).isEqualTo(PurgeOutboxOrigin.RETENTION);
    }

    @Test
    void evaluate_notYetExpiredDossierIsUnchanged() {
        settings(30);
        Dossier recent = dossier("CLAIM", NOW.minus(java.time.Duration.ofDays(1)));
        when(dossierRepository.findClosedForRetention(TENANT, 100)).thenReturn(List.of(recent));
        when(typeRepository.findByCode(TENANT, "CLAIM")).thenReturn(Optional.of(inheritType("CLAIM")));

        EvaluateDossierRetentionUseCase.EvaluationResult result = evaluateAt(NOW);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.archived()).isZero();
        verify(dossierRepository, never()).save(any());
        verify(purgeOutboxRepository, never()).insert(any());
    }

    @Test
    void evaluate_retentionNeverTypeIsNeverArchived() {
        settings(30);
        Dossier expired = dossier("LOG", NOW.minus(java.time.Duration.ofDays(365)));
        when(dossierRepository.findClosedForRetention(TENANT, 100)).thenReturn(List.of(expired));
        when(typeRepository.findByCode(TENANT, "LOG")).thenReturn(Optional.of(neverType("LOG")));

        EvaluateDossierRetentionUseCase.EvaluationResult result = evaluateAt(NOW);

        assertThat(result.archived()).isZero();
        verify(dossierRepository, never()).save(any());
        verify(purgeOutboxRepository, never()).insert(any());
    }

    @Test
    void evaluate_dossierWithNullClosedAtIsUnchanged() {
        settings(30);
        Dossier noClosedAt = new Dossier(
                UUID.randomUUID(), TENANT, "DOS-1", "CLAIM", "Dossier", DossierStatus.CLOTURE,
                Map.of(), null, 0, "creator", NOW, "creator", NOW, null, null, null);
        when(dossierRepository.findClosedForRetention(TENANT, 100)).thenReturn(List.of(noClosedAt));

        EvaluateDossierRetentionUseCase.EvaluationResult result = evaluateAt(NOW);

        assertThat(result.archived()).isZero();
        verify(dossierRepository, never()).save(any());
        verify(typeRepository, never()).findByCode(any(), any());
    }

    @Test
    void evaluate_alreadyInOutboxIsIdempotentNoOp() {
        settings(30);
        Dossier expired = dossier("CLAIM", NOW.minus(java.time.Duration.ofDays(31)));
        when(dossierRepository.findClosedForRetention(TENANT, 100)).thenReturn(List.of(expired));
        when(purgeOutboxRepository.existsByDossierId(expired.id())).thenReturn(true);

        EvaluateDossierRetentionUseCase.EvaluationResult result = evaluateAt(NOW);

        assertThat(result.archived()).isZero();
        verify(dossierRepository, never()).save(any());
        verify(purgeOutboxRepository, never()).insert(any());
    }

    @Test
    void evaluate_preexistingArchiveIsNeverRevisited() {
        // D-FORCE: findClosedForRetention only ever returns CLOTURE at the persistence layer;
        // even if a caller mock returned an ARCHIVE dossier, the use case must not re-touch it.
        settings(30);
        Dossier alreadyArchived = new Dossier(
                UUID.randomUUID(), TENANT, "DOS-2", "CLAIM", "Dossier", DossierStatus.ARCHIVE,
                Map.of(), null, 0, "creator", NOW, "someone", NOW,
                "someone", NOW.minus(java.time.Duration.ofDays(400)), null);
        when(dossierRepository.findClosedForRetention(TENANT, 100)).thenReturn(List.of(alreadyArchived));
        when(typeRepository.findByCode(TENANT, "CLAIM")).thenReturn(Optional.of(inheritType("CLAIM")));

        EvaluateDossierRetentionUseCase.EvaluationResult result = evaluateAt(NOW);

        assertThat(result.archived()).isZero();
        verify(dossierRepository, never()).save(any());
        verify(purgeOutboxRepository, never()).insert(any());
    }

    @Test
    void evaluate_passesLimitThroughToPort() {
        when(settingsRepository.findByTenantId(TENANT)).thenReturn(Optional.of(
                new TenantRetentionSettings(TENANT, 30, NOW, "admin")));
        when(dossierRepository.findClosedForRetention(TENANT, 7)).thenReturn(List.of());

        useCase.evaluate(TENANT, 7);

        verify(dossierRepository).findClosedForRetention(TENANT, 7);
    }

    private void settings(int defaultRetentionDays) {
        when(settingsRepository.findByTenantId(TENANT)).thenReturn(Optional.of(
                new TenantRetentionSettings(TENANT, defaultRetentionDays, NOW, "admin")));
    }

    private EvaluateDossierRetentionUseCase.EvaluationResult evaluateAt(Instant now) {
        // Business logic reads Instant.now() internally; tests use closedAt offsets from NOW that
        // remain valid regardless of the small clock drift between NOW and the real now used
        // inside the use case (seconds-level, irrelevant at day granularity).
        return useCase.evaluate(TENANT, 100);
    }

    private static Dossier dossier(String typeCode, Instant closedAt) {
        return new Dossier(
                UUID.randomUUID(), TENANT, "DOS-" + UUID.randomUUID(), typeCode, "Dossier",
                DossierStatus.CLOTURE, Map.of(), null, 0, "creator", closedAt, "closer", closedAt,
                "closer", closedAt, null);
    }

    private static DossierType inheritType(String code) {
        return DossierType.inherit(
                UUID.randomUUID(), TENANT, code, code, "wf-" + code.toLowerCase(), true, false, List.of());
    }

    private static DossierType neverType(String code) {
        return new DossierType(
                UUID.randomUUID(), TENANT, code, code, "wf-" + code.toLowerCase(), true, false,
                List.of(), null, true, null, "system", "system");
    }
}
