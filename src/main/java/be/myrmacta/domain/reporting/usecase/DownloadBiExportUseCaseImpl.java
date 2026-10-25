package be.myrmacta.domain.reporting.usecase;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.reporting.SlaReportAccess;
import be.myrmacta.domain.reporting.model.BiExportFile;
import be.myrmacta.domain.reporting.model.BiExportKind;
import be.myrmacta.domain.reporting.model.BiExportNotFoundException;
import be.myrmacta.domain.reporting.port.in.DownloadBiExportUseCase;
import be.myrmacta.domain.reporting.port.out.BiExportFileRepository;
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
