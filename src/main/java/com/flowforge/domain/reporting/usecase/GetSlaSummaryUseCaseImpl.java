package com.flowforge.domain.reporting.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.reporting.model.DossierSlaRow;
import com.flowforge.domain.reporting.model.SlaCohortTooLargeException;
import com.flowforge.domain.reporting.model.SlaPeriod;
import com.flowforge.domain.reporting.model.SlaSummary;
import com.flowforge.domain.reporting.model.SlaSummaryAssembler;
import com.flowforge.domain.reporting.model.TypeSlaMeta;
import com.flowforge.domain.reporting.port.in.GetSlaReportUseCase;
import com.flowforge.domain.reporting.port.in.GetSlaSummaryUseCase;
import com.flowforge.domain.reporting.port.out.SlaReportQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GetSlaSummaryUseCaseImpl implements GetSlaSummaryUseCase {

    private final SlaReportQueryPort queryPort;
    private final DossierTypeRepository typeRepository;
    private final DossierTypeAclRepository aclRepository;
    private final Clock clock;

    public GetSlaSummaryUseCaseImpl(
            SlaReportQueryPort queryPort,
            DossierTypeRepository typeRepository,
            DossierTypeAclRepository aclRepository,
            Clock clock) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.aclRepository = Objects.requireNonNull(aclRepository, "aclRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public SlaSummary get(Query query, AccessContext ctx) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(ctx, "ctx");
        Instant now = clock.instant();
        String groupBy = SlaSummaryAssembler.resolveGroupBy(query.groupBy());
        SlaPeriod period = SlaPeriod.resolve(query.from(), query.to(), now);

        Set<String> typeFilter =
                SlaReportAccess.typeFilter(SlaReportAccess.allowedTypeCodes(aclRepository, query.tenantId(), ctx),
                        query.dossierTypeCode());
        if (typeFilter != null && typeFilter.isEmpty()) {
            return new SlaSummary(period.from(), period.to(), groupBy, List.of());
        }

        long count = queryPort.countDossiers(query.tenantId(), period.from(), period.to(), typeFilter);
        if (count > GetSlaReportUseCase.MAX_COHORT) {
            throw new SlaCohortTooLargeException(count);
        }

        List<DossierSlaRow> rows = queryPort.findDossiers(query.tenantId(), period.from(), period.to(), typeFilter);
        List<TypeSlaMeta> types = typeRepository.findAll(query.tenantId()).stream()
                .filter(type -> typeFilter == null || typeFilter.contains(type.code()))
                .map(GetSlaSummaryUseCaseImpl::toMeta)
                .toList();
        return SlaSummaryAssembler.assemble(period.from(), period.to(), groupBy, types, rows, now);
    }

    private static TypeSlaMeta toMeta(DossierType type) {
        return new TypeSlaMeta(type.code(), type.label(), type.workflowKey(), type.slaTargetHours());
    }
}
