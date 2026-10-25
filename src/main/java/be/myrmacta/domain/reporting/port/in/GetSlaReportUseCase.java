package be.myrmacta.domain.reporting.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.reporting.model.SlaPeriod;
import be.myrmacta.domain.reporting.model.SlaReport;

public interface GetSlaReportUseCase {

    int MAX_COHORT = 10_000;
    int MAX_PERIOD_DAYS = SlaPeriod.MAX_PERIOD_DAYS;
    int DEFAULT_PERIOD_DAYS = SlaPeriod.DEFAULT_PERIOD_DAYS;

    SlaReport get(Query query, AccessContext ctx);

    record Query(String tenantId, String from, String to, String dossierTypeCode) {}
}
