package com.flowforge.domain.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierTypeAclEntry;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SlaReportAccessTest {

    private static final String TENANT = "tenant_dev";

    private final DossierTypeAclRepository aclRepository = new DossierTypeAclRepository() {
        @Override
        public Optional<DossierTypeAclEntry> find(String tenantId, String dossierTypeCode, String subjectUserId) {
            return Optional.empty();
        }

        @Override
        public void save(DossierTypeAclEntry entry) {}

        @Override
        public void delete(String tenantId, String dossierTypeCode, String subjectUserId) {}

        @Override
        public List<DossierTypeAclEntry> findBySubject(String tenantId, String subjectUserId) {
            return List.of();
        }
    };

    @Test
    void apiService_isUnrestricted() {
        var user = AccessContext.user("svc", Set.of("API_SERVICE"));
        assertThat(SlaReportAccess.allowedTypeCodes(aclRepository, TENANT, user)).isNull();
    }

    @Test
    void service_isAlwaysDenied() {
        var ctx = AccessContext.service("client-1");
        assertThatThrownBy(() -> SlaReportAccess.allowedTypeCodes(aclRepository, TENANT, ctx))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void viewerWithoutDossierManagerRole_isDenied() {
        var user = AccessContext.user("v1", Set.of("DOSSIER_VIEWER"));
        assertThatThrownBy(() -> SlaReportAccess.allowedTypeCodes(aclRepository, TENANT, user))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void external_isAlwaysDenied() {
        // T-ACTOR-1 (TODO §68)
        var ctx = AccessContext.external(java.util.UUID.randomUUID());
        assertThatThrownBy(() -> SlaReportAccess.allowedTypeCodes(aclRepository, TENANT, ctx))
                .isInstanceOf(AccessDeniedException.class);
    }
}
