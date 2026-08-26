package com.flowforge.domain.reporting.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierPermission;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.DossierTypeAclEntry;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.reporting.model.DossierSlaRow;
import com.flowforge.domain.reporting.model.SlaCohortTooLargeException;
import com.flowforge.domain.reporting.model.SlaReport;
import com.flowforge.domain.reporting.model.SlaReportAssembler;
import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.model.TypeSlaMeta;
import com.flowforge.domain.reporting.port.in.GetSlaReportUseCase;
import com.flowforge.domain.reporting.port.out.SlaReportQueryPort;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
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
        Instant from = query.from() == null ? now.minus(Duration.ofDays(DEFAULT_PERIOD_DAYS)) : query.from();
        Instant to = query.to() == null ? now : query.to();
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (Duration.between(from, to).toDays() > MAX_PERIOD_DAYS) {
            throw new IllegalArgumentException("period must be at most " + MAX_PERIOD_DAYS + " days");
        }

        Set<String> allowed = allowedTypeCodes(query.tenantId(), ctx);
        if (allowed != null && allowed.isEmpty()) {
            return empty(from, to, targetPercent(query.tenantId()));
        }
        if (query.dossierTypeCode() != null) {
            if (allowed != null && !allowed.contains(query.dossierTypeCode())) {
                return empty(from, to, targetPercent(query.tenantId()));
            }
            allowed = Set.of(query.dossierTypeCode());
        }
        final Set<String> typeFilter = allowed;

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

    private Set<String> allowedTypeCodes(String tenantId, AccessContext ctx) {
        return switch (ctx) {
            case AccessContext.System ignored -> null;
            case AccessContext.Service ignored -> throw new AccessDeniedException(
                    "Service context cannot access SLA reports");
            case AccessContext.User user -> {
                if (user.hasRole("TENANT_ADMIN") || user.hasRole("WORKFLOW_ADMIN")) {
                    yield null;
                }
                if (!user.hasRole("DOSSIER_MANAGER")) {
                    throw new AccessDeniedException("Access denied for SLA reports");
                }
                yield aclRepository.findBySubject(tenantId, user.userId()).stream()
                        .filter(entry -> entry.permission().implies(DossierPermission.VIEW))
                        .map(DossierTypeAclEntry::dossierTypeCode)
                        .collect(Collectors.toSet());
            }
        };
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
