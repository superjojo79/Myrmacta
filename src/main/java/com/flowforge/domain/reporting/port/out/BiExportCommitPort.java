package com.flowforge.domain.reporting.port.out;

import com.flowforge.domain.reporting.model.BiExportFile;
import java.time.Instant;
import java.util.List;

/** Inserts catalogue rows and advances {@code lastBiExportAt} in one transaction. */
public interface BiExportCommitPort {

    void commitSuccessfulExport(String tenantId, List<BiExportFile> files, Instant exportedAt);
}
