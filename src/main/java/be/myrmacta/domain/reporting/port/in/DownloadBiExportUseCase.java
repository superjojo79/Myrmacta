package be.myrmacta.domain.reporting.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import java.util.UUID;

public interface DownloadBiExportUseCase {

    Download download(String tenantId, UUID id, AccessContext ctx);

    record Download(String fileName, byte[] content) {}
}
