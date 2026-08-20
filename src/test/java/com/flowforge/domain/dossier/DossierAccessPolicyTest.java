package com.flowforge.domain.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierPermission;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierTypeAclEntry;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DossierAccessPolicyTest {

    private static final String TENANT = "tenant_dev";

    private FakeAclRepository acl;
    private DossierAccessPolicy policy;

    @BeforeEach
    void setUp() {
        acl = new FakeAclRepository();
        policy = new DossierAccessPolicy(acl);
    }

    @Test
    void system_canViewAndEdit() {
        var ctx = AccessContext.system("flowable");
        assertThatCode(() -> policy.assertCanViewType(TENANT, "SINISTRE", ctx)).doesNotThrowAnyException();
        assertThatCode(() -> policy.assertCanEditType(TENANT, "SINISTRE", ctx)).doesNotThrowAnyException();
    }

    @Test
    void tenantAdmin_bypasses() {
        var user = AccessContext.user("u1", Set.of("TENANT_ADMIN"));
        assertThatCode(() -> policy.assertCanEditType(TENANT, "SINISTRE", user)).doesNotThrowAnyException();
    }

    @Test
    void workflowAdmin_bypasses() {
        var user = AccessContext.user("u1", Set.of("WORKFLOW_ADMIN"));
        assertThatCode(() -> policy.assertCanViewType(TENANT, "SINISTRE", user)).doesNotThrowAnyException();
    }

    @Test
    void editGrant_allowsViewAndEdit() {
        acl.grant("SINISTRE", "u1", DossierPermission.EDIT);
        var user = AccessContext.user("u1", Set.of("DOSSIER_MANAGER"));
        assertThatCode(() -> policy.assertCanViewType(TENANT, "SINISTRE", user)).doesNotThrowAnyException();
        assertThatCode(() -> policy.assertCanEditType(TENANT, "SINISTRE", user)).doesNotThrowAnyException();
    }

    @Test
    void viewGrant_allowsViewOnly() {
        acl.grant("SINISTRE", "u1", DossierPermission.VIEW);
        var user = AccessContext.user("u1", Set.of("DOSSIER_VIEWER"));
        assertThatCode(() -> policy.assertCanViewType(TENANT, "SINISTRE", user)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.assertCanEditType(TENANT, "SINISTRE", user))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void noGrant_denies() {
        var user = AccessContext.user("u1", Set.of("DOSSIER_MANAGER"));
        assertThatThrownBy(() -> policy.assertCanViewType(TENANT, "SINISTRE", user))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void service_alwaysDenied() {
        var ctx = AccessContext.service("client-1");
        assertThatThrownBy(() -> policy.assertCanViewType(TENANT, "SINISTRE", ctx))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void canEdit_mirrorsAssertCanEditWithoutThrowing() {
        var admin = AccessContext.user("admin", Set.of("TENANT_ADMIN"));
        var managerWithGrant = AccessContext.user("u1", Set.of("DOSSIER_MANAGER"));
        var managerWithoutGrant = AccessContext.user("u2", Set.of("DOSSIER_MANAGER"));
        acl.grant("SINISTRE", "u1", DossierPermission.EDIT);

        assertThat(policy.canEdit(dossierOfType("SINISTRE"), admin)).isTrue();
        assertThat(policy.canEdit(dossierOfType("SINISTRE"), managerWithGrant)).isTrue();
        assertThat(policy.canEdit(dossierOfType("SINISTRE"), managerWithoutGrant)).isFalse();
    }

    @Test
    void canEdit_viewOnlyGrant_returnsFalse() {
        acl.grant("SINISTRE", "u1", DossierPermission.VIEW);
        var user = AccessContext.user("u1", Set.of("DOSSIER_VIEWER"));

        assertThat(policy.canEdit(dossierOfType("SINISTRE"), user)).isFalse();
    }

    @Test
    void canEdit_serviceContext_returnsFalseInsteadOfThrowing() {
        var ctx = AccessContext.service("client-1");

        assertThat(policy.canEdit(dossierOfType("SINISTRE"), ctx)).isFalse();
    }

    private static Dossier dossierOfType(String dossierTypeCode) {
        return new Dossier(
                UUID.randomUUID(),
                TENANT,
                "REF-1",
                dossierTypeCode,
                "Label",
                DossierStatus.DRAFT,
                Map.of(),
                null,
                0L,
                "creator",
                Instant.now(),
                "creator",
                Instant.now(),
                null,
                null,
                null);
    }

    private static final class FakeAclRepository implements DossierTypeAclRepository {
        private DossierTypeAclEntry entry;

        void grant(String type, String userId, DossierPermission permission) {
            entry = new DossierTypeAclEntry(TENANT, type, userId, permission);
        }

        @Override
        public Optional<DossierTypeAclEntry> find(String tenantId, String dossierTypeCode, String subjectUserId) {
            if (entry == null) {
                return Optional.empty();
            }
            if (entry.tenantId().equals(tenantId)
                    && entry.dossierTypeCode().equals(dossierTypeCode)
                    && entry.subjectUserId().equals(subjectUserId)) {
                return Optional.of(entry);
            }
            return Optional.empty();
        }

        @Override
        public void save(DossierTypeAclEntry aclEntry) {
            entry = aclEntry;
        }

        @Override
        public void delete(String tenantId, String dossierTypeCode, String subjectUserId) {
            entry = null;
        }

        @Override
        public List<DossierTypeAclEntry> findBySubject(String tenantId, String subjectUserId) {
            return entry == null ? List.of() : List.of(entry);
        }
    }
}
