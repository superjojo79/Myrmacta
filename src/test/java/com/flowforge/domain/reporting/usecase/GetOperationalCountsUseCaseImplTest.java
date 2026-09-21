package com.flowforge.domain.reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.reporting.model.OperationalCounts;
import com.flowforge.domain.reporting.port.out.OperationalCountsPort;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GetOperationalCountsUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private final OperationalCountsPort countsPort = mock(OperationalCountsPort.class);
    private final DossierTypeAclRepository aclRepository = mock(DossierTypeAclRepository.class);
    private final GetOperationalCountsUseCaseImpl useCase =
            new GetOperationalCountsUseCaseImpl(countsPort, aclRepository);

    @Test
    void tenantAdmin_isUnrestricted() {
        when(countsPort.current(TENANT)).thenReturn(new OperationalCounts(3, 7));
        var admin = AccessContext.user("a1", Set.of("TENANT_ADMIN"));

        assertThat(useCase.get(TENANT, admin)).isEqualTo(new OperationalCounts(3, 7));
    }

    @Test
    void dossierManagerWithNoAclGrantAtAll_stillSeesTheTenantWideCounts() {
        // These counts are tenant-wide by design (see FlowableOperationalCountsAdapter), not
        // scoped per dossier type, so holding the role is enough regardless of ACL entries.
        when(countsPort.current(TENANT)).thenReturn(new OperationalCounts(1, 2));
        when(aclRepository.findBySubject(TENANT, "m2")).thenReturn(List.of());
        var manager = AccessContext.user("m2", Set.of("DOSSIER_MANAGER"));

        assertThat(useCase.get(TENANT, manager)).isEqualTo(new OperationalCounts(1, 2));
    }

    @Test
    void viewerWithoutDossierManagerRole_isDenied() {
        var viewer = AccessContext.user("v1", Set.of("DOSSIER_VIEWER"));

        assertThatThrownBy(() -> useCase.get(TENANT, viewer)).isInstanceOf(AccessDeniedException.class);
    }
}
