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
import com.flowforge.domain.reporting.model.SlaSummary;
import com.flowforge.domain.reporting.port.in.GetSlaSummaryUseCase;
import com.flowforge.domain.reporting.port.out.SlaReportQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetSlaSummaryUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-03-15T00:00:00Z");
    private static final String TENANT = "tenant_dev";

    @Mock
    private SlaReportQueryPort queryPort;
    @Mock
    private DossierTypeRepository typeRepository;
    @Mock
    private DossierTypeAclRepository aclRepository;

    private GetSlaSummaryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSlaSummaryUseCaseImpl(
                queryPort, typeRepository, aclRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void omittedGroupBy_defaultsToDossierTypeCode() {
        when(typeRepository.findAll(TENANT)).thenReturn(List.of(type("SIN", 48)));
        when(queryPort.countDossiers(eq(TENANT), any(), any(), isNull())).thenReturn(1L);
        when(queryPort.findDossiers(eq(TENANT), any(), any(), isNull())).thenReturn(List.of(row("SIN")));

        SlaSummary summary = useCase.get(
                new GetSlaSummaryUseCase.Query(TENANT, null, null, null, null),
                AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(summary.groupBy()).isEqualTo("dossierTypeCode");
        assertThat(summary.from()).isEqualTo(NOW.minusSeconds(30L * 24 * 3600));
        assertThat(summary.buckets()).extracting(b -> b.key()).containsExactly("SIN");
    }

    @Test
    void calendarGroupBy_isRejected() {
        assertThatThrownBy(() -> useCase.get(
                        new GetSlaSummaryUseCase.Query(TENANT, null, null, null, "week"),
                        AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groupBy");
        verifyNoInteractions(queryPort);
    }

    @Test
    void dossierManager_emptyAcl_returnsEmptyBuckets() {
        when(aclRepository.findBySubject(TENANT, "mgr")).thenReturn(List.of());

        SlaSummary summary = useCase.get(
                new GetSlaSummaryUseCase.Query(TENANT, null, null, null, null),
                AccessContext.user("mgr", Set.of("DOSSIER_MANAGER")));

        assertThat(summary.buckets()).isEmpty();
        assertThat(summary.groupBy()).isEqualTo("dossierTypeCode");
        verifyNoInteractions(queryPort);
    }

    @Test
    void dossierManager_filtersByAcl() {
        when(aclRepository.findBySubject(TENANT, "mgr"))
                .thenReturn(List.of(new DossierTypeAclEntry(TENANT, "SIN", "mgr", DossierPermission.VIEW)));
        when(typeRepository.findAll(TENANT)).thenReturn(List.of(type("SIN", 48), type("REC", null)));
        when(queryPort.countDossiers(eq(TENANT), any(), any(), eq(Set.of("SIN")))).thenReturn(0L);
        when(queryPort.findDossiers(eq(TENANT), any(), any(), eq(Set.of("SIN")))).thenReturn(List.of());

        useCase.get(
                new GetSlaSummaryUseCase.Query(TENANT, null, null, null, "status"),
                AccessContext.user("mgr", Set.of("DOSSIER_MANAGER")));

        verify(queryPort).countDossiers(eq(TENANT), any(), any(), eq(Set.of("SIN")));
    }

    @Test
    void viewer_denied() {
        assertThatThrownBy(() -> useCase.get(
                        new GetSlaSummaryUseCase.Query(TENANT, null, null, null, null),
                        AccessContext.user("v", Set.of("DOSSIER_VIEWER"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cohortTooLarge_rejected() {
        when(queryPort.countDossiers(eq(TENANT), any(), any(), isNull())).thenReturn(10_001L);
        assertThatThrownBy(() -> useCase.get(
                        new GetSlaSummaryUseCase.Query(TENANT, null, null, null, null),
                        AccessContext.user("admin", Set.of("TENANT_ADMIN"))))
                .isInstanceOf(SlaCohortTooLargeException.class);
    }

    private static DossierType type(String code, Integer hours) {
        return new DossierType(
                UUID.randomUUID(), TENANT, code, code, "wf-" + code, true, false, List.of(), null, false, hours);
    }

    private static DossierSlaRow row(String type) {
        Instant created = Instant.parse("2026-03-01T00:00:00Z");
        return new DossierSlaRow(
                UUID.randomUUID(), type, created, Instant.parse("2026-03-02T00:00:00Z"), created, DossierStatus.CLOTURE);
    }
}
