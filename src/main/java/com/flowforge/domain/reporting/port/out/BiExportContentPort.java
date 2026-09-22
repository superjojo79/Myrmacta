package com.flowforge.domain.reporting.port.out;

import com.flowforge.domain.reporting.model.BiExportKind;
import java.time.Instant;
import java.util.List;

public interface BiExportContentPort {

    List<BiExportPayload> render(String tenantId, Instant generatedAt);

    record BiExportPayload(BiExportKind kind, String dossierTypeCode, String fileName, byte[] content) {}
}
