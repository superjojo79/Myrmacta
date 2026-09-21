package com.flowforge.domain.reporting.usecase;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.reporting.SlaReportAccess;
import com.flowforge.domain.reporting.model.BiExportFile;
import com.flowforge.domain.reporting.model.BiExportKind;
import com.flowforge.domain.reporting.model.BiExportNotFoundException;
import com.flowforge.domain.reporting.port.in.DownloadBiExportUseCase;
import com.flowforge.domain.reporting.port.out.BiExportFileRepository;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DownloadBiExportUseCaseImpl implements DownloadBiExportUseCase {

    private final BiExportFileRepository fileRepository;
    private final DossierTypeAclRepository aclRepository;
    private final DocumentStoragePort storagePort;

    public DownloadBiExportUseCaseImpl(
            BiExportFileRepository fileRepository,
            DossierTypeAclRepository aclRepository,
            DocumentStoragePort storagePort) {
        this.fileRepository = Objects.requireNonNull(fileRepository, "fileRepository");
        this.aclRepository = Objects.requireNonNull(aclRepository, "aclRepository");
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort");
    }

    @Override
    public Download download(String tenantId, UUID id, AccessContext ctx) {
        Objects.requireNonNull(id, "id");
        Set<String> viewable = SlaReportAccess.allowedTypeCodes(aclRepository, tenantId, ctx);
        BiExportFile file = fileRepository
                .findById(tenantId, id)
                .filter(candidate -> visible(candidate, viewable))
                .orElseThrow(() -> new BiExportNotFoundException("BI export not found"));
        byte[] content = storagePort.retrieve(tenantId, file.storageRef());
        return new Download(file.fileName(), content);
    }

    static boolean visible(BiExportFile file, Set<String> viewableTypeCodes) {
        if (viewableTypeCodes == null) {
            return true;
        }
        if (file.kind() == BiExportKind.SLA) {
            return true;
        }
        return viewableTypeCodes.contains(file.dossierTypeCode());
    }
}
