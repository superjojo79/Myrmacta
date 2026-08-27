package com.flowforge.domain.reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierPermission;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.DossierTypeAclEntry;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.reporting.model.DossierSlaRow;
import com.flowforge.domain.reporting.model.SlaCohortTooLargeException;
import com.flowforge.domain.reporting.model.SlaReport;
import com.flowforge.domain.reporting.port.in.GetSlaReportUseCase;
import com.flowforge.domain.reporting.port.out.SlaReportQueryPort;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetSlaReportUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-03-15T00:00:00Z");
    private static final String TENANT = "tenant_dev";

    @Mock
    private SlaReportQueryPort queryPort;
    @Mock
    private DossierTypeRepository typeRepository;
    @Mock
    private DossierTypeAclRepository aclRepository;
    @Mock
    private TenantSlaSettingsRepository slaSettingsRepository;

    private GetSlaReportUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSlaReportUseCaseImpl(
                queryPort,
                typeRepository,
                aclRepository,
                slaSettingsRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void tenantAdmin_unrestrictedCodes() {
        when(typeRepository.findAll(TENANT)).thenReturn(List.of(type("SIN", 48)));
        when(queryPort.countDossiers(eq(TENANT), any(), any(), isNull())).thenReturn(1L);
        DossierSlaRow row = row("SIN");
        when(queryPort.findDossiers(eq(TENANT), any(), any(), isNull())).thenReturn(List.of(row), List.of());
        when(queryPort.countProducedDocsByType(any())).thenReturn(Map.of());
        when(queryPort.findActiveTasks(eq(TENANT), isNull())).thenReturn(List.of());
        when(queryPort.findCompletedUserTaskDurations(any())).thenReturn(List.of());
        when(slaSettingsRepository.findByTenantId(TENANT)).thenReturn(Optional.empty());

        SlaReport report = useCase.get(
                new GetSlaReportUseCase.Query(TENANT, null, null, null),
                AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(report.complianceTargetPercent()).isEqualTo(95);
        assertThat(report.from()).isEqualTo(NOW.minusSeconds(30L * 24 * 3600));
        verify(queryPort).countDossiers(eq(TENANT), any(), any(), isNull());
    }

    @Test
    void dossierManager_emptyAcl_returnsEmptySnapshot() {
        when(aclRepository.findBySubject(TENANT, "mgr")).thenReturn(List.of());

        SlaReport report = useCase.get(
                new GetSlaReportUseCase.Query(TENANT, null, null, null),
                AccessContext.user("mgr", Set.of("DOSSIER_MANAGER")));

        assertThat(report.types()).isEmpty();
        assertThat(report.complianceTargetPercent()).isEqualTo(95);
        verifyNoInteractions(queryPort);
    }

    @Test
    void dossierManager_filtersByAcl() {
        when(aclRepository.findBySubject(TENANT, "mgr"))
                .thenReturn(List.of(new DossierTypeAclEntry(TENANT, "SIN", "mgr", DossierPermission.VIEW)));
        when(typeRepository.findAll(TENANT)).thenReturn(List.of(type("SIN", 48), type("REC", null)));
        when(queryPort.countDossiers(eq(TENANT), any(), any(), eq(Set.of("SIN")))).thenReturn(0L);
        when(queryPort.findDossiers(eq(TENANT), any(), any(), eq(Set.of("SIN")))).thenReturn(List.of());
        when(queryPort.countProducedDocsByType(any())).thenReturn(Map.of());
        when(queryPort.findActiveTasks(eq(TENANT), eq(Set.of("SIN")))).thenReturn(List.of());
        when(queryPort.findCompletedUserTaskDurations(any())).thenReturn(List.of());
        when(slaSettingsRepository.findByTenantId(TENANT)).thenReturn(Optional.empty());

        useCase.get(
                new GetSlaReportUseCase.Query(TENANT, null, null, null),
                AccessContext.user("mgr", Set.of("DOSSIER_MANAGER")));

        verify(queryPort).countDossiers(eq(TENANT), any(), any(), eq(Set.of("SIN")));
    }

    @Test
    void viewer_denied() {
        assertThatThrownBy(() -> useCase.get(
                        new GetSlaReportUseCase.Query(TENANT, null, null, null),
                        AccessContext.user("v", Set.of("DOSSIER_VIEWER"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void service_denied() {
        assertThatThrownBy(() -> useCase.get(
                        new GetSlaReportUseCase.Query(TENANT, null, null, null), AccessContext.service("client")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void invertedPeriod_rejected() {
        assertThatThrownBy(() -> useCase.get(
                        new GetSlaReportUseCase.Query(
                                TENANT, "2026-03-10T00:00:00Z", "2026-03-01T00:00:00Z", null),
                        AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from must be before to");
    }

    @Test
    void periodTooLong_rejected() {
        assertThatThrownBy(() -> useCase.get(
                        new GetSlaReportUseCase.Query(
                                TENANT, "2024-01-01T00:00:00Z", "2026-03-15T00:00:00Z", null),
                        AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366");
    }

    @Test
    void cohortTooLarge_rejected() {
        when(queryPort.countDossiers(eq(TENANT), any(), any(), isNull())).thenReturn(10_001L);
        assertThatThrownBy(() -> useCase.get(
                        new GetSlaReportUseCase.Query(TENANT, null, null, null),
                        AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(SlaCohortTooLargeException.class);
    }

    private static DossierType type(String code, Integer hours) {
        return new DossierType(
                UUID.randomUUID(), TENANT, code, code, "wf-" + code, true, false, List.of(), null, false, hours,
                "system", "system");
    }

    private static DossierSlaRow row(String type) {
        Instant created = Instant.parse("2026-03-01T00:00:00Z");
        return new DossierSlaRow(
                UUID.randomUUID(), type, created, Instant.parse("2026-03-02T00:00:00Z"), created, DossierStatus.CLOTURE);
    }
}
