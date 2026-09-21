package com.flowforge.domain.reporting.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.reporting.UnitOfWork;
import com.flowforge.domain.reporting.model.BiExportFile;
import com.flowforge.domain.reporting.model.BiExportKind;
import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.port.out.BiExportCommitPort;
import com.flowforge.domain.reporting.port.out.BiExportContentPort;
import com.flowforge.domain.reporting.port.out.BiExportFileRepository;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunBiExportUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");
    private static final UnitOfWork INLINE = Runnable::run;

    private TenantSlaSettingsRepository settingsRepository;
    private BiExportContentPort contentPort;
    private DocumentStoragePort storagePort;
    private BiExportCommitPort commitPort;
    private BiExportFileRepository fileRepository;
    private RunBiExportUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        settingsRepository = mock(TenantSlaSettingsRepository.class);
        contentPort = mock(BiExportContentPort.class);
        storagePort = mock(DocumentStoragePort.class);
        commitPort = mock(BiExportCommitPort.class);
        fileRepository = mock(BiExportFileRepository.class);
        useCase = new RunBiExportUseCaseImpl(
                settingsRepository, contentPort, storagePort, commitPort, fileRepository);
        when(fileRepository.findCreatedBefore(eq(TENANT), any())).thenReturn(List.of());
    }

    @Test
    void run_withoutSettings_doesNothing() {
        when(settingsRepository.findByTenantId(TENANT)).thenReturn(Optional.empty());
        useCase.run(TENANT, NOW, INLINE);
        verify(contentPort, never()).render(any(), any());
        verify(fileRepository, never()).findCreatedBefore(any(), any());
    }

    @Test
    void run_disabledOrNotYetDue_purgesWithoutCreatingFiles() {
        when(settingsRepository.findByTenantId(TENANT)).thenReturn(Optional.of(settings(false, NOW, null)));
        useCase.run(TENANT, NOW, INLINE);

        when(settingsRepository.findByTenantId(TENANT)).thenReturn(Optional.of(settings(true, NOW, null)));
        useCase.run(TENANT, NOW, INLINE);

        when(settingsRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(settings(true, NOW, NOW.minus(Duration.ofDays(10)))));
        useCase.run(TENANT, NOW, INLINE);

        verify(contentPort, never()).render(any(), any());
        verify(fileRepository, org.mockito.Mockito.times(3)).findCreatedBefore(eq(TENANT), any());
    }

    @Test
    void run_due_storesThenCommitsInsideUnitOfWork() {
        when(settingsRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(settings(true, NOW.minus(Duration.ofHours(24)), null)));
        byte[] bytes = "a,b\r\n".getBytes();
        when(contentPort.render(TENANT, NOW))
                .thenReturn(List.of(new BiExportContentPort.BiExportPayload(
                        BiExportKind.SLA, null, "sla-report-" + NOW + ".csv", bytes)));
        when(storagePort.store(TENANT, "text/csv", bytes)).thenReturn("ref-1");
        List<String> trace = new java.util.ArrayList<>();
        UnitOfWork watching = action -> {
            trace.add("begin");
            action.run();
            trace.add("end");
        };
        org.mockito.Mockito.doAnswer(inv -> {
                    trace.add("commit");
                    return null;
                })
                .when(commitPort)
                .commitSuccessfulExport(eq(TENANT), any(), eq(NOW));

        useCase.run(TENANT, NOW, watching);

        verify(storagePort).store(TENANT, "text/csv", bytes);
        verify(storagePort, never()).delete(any(), any());
        org.assertj.core.api.Assertions.assertThat(trace).containsExactly("begin", "commit", "end");
    }

    @Test
    void run_commitFailure_deletesStoredObjectsAndDoesNotAdvance() {
        when(settingsRepository.findByTenantId(TENANT))
                .thenReturn(Optional.of(settings(true, NOW.minus(Duration.ofHours(24)), null)));
        byte[] bytes = "csv".getBytes();
        when(contentPort.render(TENANT, NOW))
                .thenReturn(List.of(new BiExportContentPort.BiExportPayload(BiExportKind.SLA, null, "sla.csv", bytes)));
        when(storagePort.store(TENANT, "text/csv", bytes)).thenReturn("ref-1");
        org.mockito.Mockito.doThrow(new IllegalStateException("db"))
                .when(commitPort)
                .commitSuccessfulExport(eq(TENANT), any(), eq(NOW));

        assertThatThrownBy(() -> useCase.run(TENANT, NOW, INLINE)).isInstanceOf(IllegalStateException.class);
        verify(storagePort).delete(TENANT, "ref-1");
        verify(fileRepository, never()).findCreatedBefore(any(), any());
    }

    @Test
    void run_purgesExpiredFilesEvenWhenExportIsDisabled() {
        when(settingsRepository.findByTenantId(TENANT)).thenReturn(Optional.of(settings(false, NOW.minus(Duration.ofDays(40)), null)));
        BiExportFile old = new BiExportFile(
                UUID.randomUUID(), TENANT, BiExportKind.SLA, null, "old-ref", "sla.csv", NOW.minus(Duration.ofDays(31)), 4);
        when(fileRepository.findCreatedBefore(eq(TENANT), eq(NOW.minus(Duration.ofDays(30))))).thenReturn(List.of(old));

        useCase.run(TENANT, NOW, INLINE);

        verify(storagePort).delete(TENANT, "old-ref");
        verify(fileRepository).delete(old.id());
        verify(contentPort, never()).render(any(), any());
    }

    private static TenantSlaSettings settings(boolean biEnabled, Instant biEnabledAt, Instant lastExport) {
        return new TenantSlaSettings(
                TENANT,
                95,
                false,
                60,
                List.of(),
                List.of(),
                biEnabled,
                24,
                30,
                null,
                null,
                biEnabledAt,
                lastExport,
                NOW,
                "admin");
    }
}
