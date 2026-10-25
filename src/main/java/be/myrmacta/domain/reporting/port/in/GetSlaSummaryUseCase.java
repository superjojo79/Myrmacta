package be.myrmacta.domain.reporting.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.reporting.model.SlaSummary;

public interface GetSlaSummaryUseCase {

    SlaSummary get(Query query, AccessContext ctx);

    record Query(String tenantId, String from, String to, String dossierTypeCode, String groupBy) {}
}
