package com.flowforge.domain.reporting.usecase;

import com.flowforge.domain.document.model.DocumentStorageException;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.reporting.UnitOfWork;
import com.flowforge.domain.reporting.model.BiExportFile;
import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.port.in.RunBiExportUseCase;
import com.flowforge.domain.reporting.port.out.BiExportAlreadyCommittedException;
import com.flowforge.domain.reporting.port.out.BiExportCommitPort;
import com.flowforge.domain.reporting.port.out.BiExportContentPort;
import com.flowforge.domain.reporting.port.out.BiExportFileRepository;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RunBiExportUseCaseImpl implements RunBiExportUseCase {

    private final TenantSlaSettingsRepository settingsRepository;
    private final BiExportContentPort contentPort;
    private final DocumentStoragePort storagePort;
    private final BiExportCommitPort commitPort;
    private final BiExportFileRepository fileRepository;

    public RunBiExportUseCaseImpl(
            TenantSlaSettingsRepository settingsRepository,
            BiExportContentPort contentPort,
            DocumentStoragePort storagePort,
            BiExportCommitPort commitPort,
            BiExportFileRepository fileRepository) {
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository");
        this.contentPort = Objects.requireNonNull(contentPort, "contentPort");
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort");
        this.commitPort = Objects.requireNonNull(commitPort, "commitPort");
        this.fileRepository = Objects.requireNonNull(fileRepository, "fileRepository");
    }

    @Override
    public void run(String tenantId, Instant now, UnitOfWork unitOfWork) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(unitOfWork, "unitOfWork");
        Optional<TenantSlaSettings> found = settingsRepository.findByTenantId(tenantId);
        if (found.isEmpty()) {
            return;
        }
        TenantSlaSettings settings = found.get();
        if (settings.biExportEnabled() && isDue(settings, now)) {
            export(settings, now, unitOfWork);
        }
        purge(settings, now);
    }

    private void export(TenantSlaSettings settings, Instant now, UnitOfWork unitOfWork) {
        List<BiExportContentPort.BiExportPayload> rendered = contentPort.render(settings.tenantId(), now);
        List<String> storedRefs = new ArrayList<>();
        List<BiExportFile> files = new ArrayList<>();
        try {
            for (BiExportContentPort.BiExportPayload payload : rendered) {
                String storageRef = storagePort.store(settings.tenantId(), "text/csv", payload.content());
                storedRefs.add(storageRef);
                files.add(new BiExportFile(
                        UUID.randomUUID(),
                        settings.tenantId(),
                        payload.kind(),
                        payload.dossierTypeCode(),
                        storageRef,
                        payload.fileName(),
                        now,
                        payload.content().length));
            }
            unitOfWork.run(() -> commitPort.commitSuccessfulExport(
                    settings.tenantId(), files, now, settings.lastBiExportAt()));
        } catch (RuntimeException ex) {
            for (String storageRef : storedRefs) {
                try {
                    storagePort.delete(settings.tenantId(), storageRef);
                } catch (DocumentStorageException ignored) {
                    // Best-effort: a leftover object is removed by a later retention pass once catalogued,
                    // or stays orphaned if the catalogue row was never committed.
                }
            }
            if (ex instanceof BiExportAlreadyCommittedException) {
                return; // un autre exécutant a exporté ce créneau (D5) : nos fichiers sont retirés, ce n'est pas une erreur
            }
            throw ex;
        }
    }

    static boolean isDue(TenantSlaSettings settings, Instant now) {
        Instant anchor = settings.biEnabledAt();
        if (anchor == null) {
            return false;
        }
        Instant reference = settings.lastBiExportAt();
        if (reference == null || reference.isBefore(anchor)) {
            reference = anchor;
        }
        return !now.isBefore(reference.plus(Duration.ofHours(settings.biIntervalHours())));
    }

    private void purge(TenantSlaSettings settings, Instant now) {
        Instant cutoff = now.minus(Duration.ofDays(settings.biRetentionDays()));
        for (BiExportFile file : fileRepository.findCreatedBefore(settings.tenantId(), cutoff)) {
            storagePort.delete(settings.tenantId(), file.storageRef());
            fileRepository.delete(file.id());
        }
    }
}
