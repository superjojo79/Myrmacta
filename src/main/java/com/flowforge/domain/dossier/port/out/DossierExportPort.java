package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.Dossier;
import java.util.stream.Stream;

/**
 * D-CSV: streamed read of full dossiers (including {@code fields}) for export — deliberately
 * separate from {@link DossierSearchPort}, whose {@code DossierListItem} projection carries no
 * dynamic fields. Callers must close the returned stream (try-with-resources) and consume it
 * within the same transaction that produced it — the underlying JDBC cursor is not valid once
 * that transaction commits.
 */
public interface DossierExportPort {

    Stream<Dossier> streamForExport(String tenantId, DossierSearchPort.Filter filter);
}
