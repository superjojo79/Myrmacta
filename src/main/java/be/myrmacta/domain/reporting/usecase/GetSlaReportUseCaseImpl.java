package be.myrmacta.domain.reporting.usecase;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.reporting.SlaReportAccess;
import be.myrmacta.domain.reporting.model.DossierSlaRow;
import be.myrmacta.domain.reporting.model.SlaCohortTooLargeException;
import be.myrmacta.domain.reporting.model.SlaPeriod;
import be.myrmacta.domain.reporting.model.SlaReport;
import be.myrmacta.domain.reporting.model.SlaReportAssembler;
import be.myrmacta.domain.reporting.model.TenantSlaSettings;
import be.myrmacta.domain.reporting.model.TypeSlaMeta;
import be.myrmacta.domain.reporting.port.in.GetSlaReportUseCase;
import be.myrmacta.domain.reporting.port.out.SlaReportQueryPort;
import be.myrmacta.domain.reporting.port.out.TenantSlaSettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class GetSlaReportUseCaseImpl implements GetSlaReportUseCase {

    private final SlaReportQueryPort queryPort;
    private final DossierTypeRepository typeRepository;
    private final DossierTypeAclRepository aclRepository;
    private final TenantSlaSettingsRepository slaSettingsRepository;
    private final Clock clock;

    public GetSlaReportUseCaseImpl(
            SlaReportQueryPort queryPort,
            DossierTypeRepository typeRepository,
            DossierTypeAclRepository aclRepository,
            TenantSlaSettingsRepository slaSettingsRepository,
            Clock clock) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.aclRepository = Objects.requireNonNull(aclRepository, "aclRepository");
        this.slaSettingsRepository = Objects.requireNonNull(slaSettingsRepository, "slaSettingsRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public SlaReport get(Query query, AccessContext ctx) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(ctx, "ctx");
        Instant now = clock.instant();
        SlaPeriod period = SlaPeriod.resolve(query.from(), query.to(), now);
        Instant from = period.from();
        Instant to = period.to();

        Set<String> typeFilter = SlaReportAccess.typeFilter(
                SlaReportAccess.allowedTypeCodes(aclRepository, query.tenantId(), ctx), query.dossierTypeCode());
        if (typeFilter != null && typeFilter.isEmpty()) {
            return empty(from, to, targetPercent(query.tenantId()));
        }

        long count = queryPort.countDossiers(query.tenantId(), from, to, typeFilter);
        if (count > MAX_COHORT) {
            throw new SlaCohortTooLargeException(count);
        }

        Duration window = Duration.between(from, to);
        Instant previousFrom = from.minus(window);
        Instant previousTo = from;

        List<DossierSlaRow> current = queryPort.findDossiers(query.tenantId(), from, to, typeFilter);
        List<DossierSlaRow> previous = queryPort.findDossiers(query.tenantId(), previousFrom, previousTo, typeFilter);
        Set<UUID> ids = current.stream().map(DossierSlaRow::id).collect(Collectors.toSet());

        List<TypeSlaMeta> types = typeRepository.findAll(query.tenantId()).stream()
                .filter(type -> typeFilter == null || typeFilter.contains(type.code()))
                .map(GetSlaReportUseCaseImpl::toMeta)
                .toList();

        return SlaReportAssembler.assemble(
                from,
                to,
                targetPercent(query.tenantId()),
                types,
                current,
                previous,
                queryPort.countProducedDocsByType(ids),
                queryPort.findActiveTasks(query.tenantId(), typeFilter),
                queryPort.findCompletedUserTaskDurations(ids),
                now);
    }

    private int targetPercent(String tenantId) {
        return slaSettingsRepository
                .findByTenantId(tenantId)
                .map(TenantSlaSettings::complianceTargetPercent)
                .orElse(TenantSlaSettings.DEFAULT_COMPLIANCE_TARGET_PERCENT);
    }

    private static SlaReport empty(Instant from, Instant to, int target) {
        return SlaReportAssembler.assemble(
                from, to, target, List.of(), List.of(), List.of(), java.util.Map.of(), List.of(), List.of(), to);
    }

    private static TypeSlaMeta toMeta(DossierType type) {
        return new TypeSlaMeta(type.code(), type.label(), type.workflowKey(), type.slaTargetHours());
    }
}
