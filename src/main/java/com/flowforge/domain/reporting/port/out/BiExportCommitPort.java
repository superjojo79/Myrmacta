package com.flowforge.domain.reporting.port.out;

import com.flowforge.domain.reporting.model.BiExportFile;
import java.time.Instant;
import java.util.List;

/** Inserts catalogue rows and advances {@code lastBiExportAt} in one transaction. */
public interface BiExportCommitPort {

    /**
     * @param expectedLastBiExportAt valeur de {@code lastBiExportAt} observée au début de l'export ({@code null} = jamais
     *     exporté) : l'avancement est un compare-and-set — si un autre exécutant a déjà exporté ce créneau,
     *     {@link BiExportAlreadyCommittedException} est levée et rien n'est écrit (TODO §79, D5).
     */
    void commitSuccessfulExport(
            String tenantId, List<BiExportFile> files, Instant exportedAt, Instant expectedLastBiExportAt);
}
