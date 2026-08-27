package com.flowforge.domain.dossier;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierPermission;
import com.flowforge.domain.dossier.model.DossierTypeAclEntry;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import java.util.Objects;

public final class DossierAccessPolicy {

    private final DossierTypeAclRepository aclRepository;

    public DossierAccessPolicy(DossierTypeAclRepository aclRepository) {
        this.aclRepository = Objects.requireNonNull(aclRepository, "aclRepository");
    }

    public void assertCanViewType(String tenantId, String dossierTypeCode, AccessContext ctx) {
        assertPermission(tenantId, dossierTypeCode, ctx, DossierPermission.VIEW);
    }

    public void assertCanEditType(String tenantId, String dossierTypeCode, AccessContext ctx) {
        assertPermission(tenantId, dossierTypeCode, ctx, DossierPermission.EDIT);
    }

    public void assertCanView(Dossier dossier, AccessContext ctx) {
        assertCanViewType(dossier.tenantId(), dossier.dossierTypeCode(), ctx);
    }

    public void assertCanEdit(Dossier dossier, AccessContext ctx) {
        assertCanEditType(dossier.tenantId(), dossier.dossierTypeCode(), ctx);
    }

    /**
     * Non-throwing mirror of {@link #assertCanEdit(Dossier, AccessContext)}, for callers that need
     * to expose the effective permission to the caller (e.g. an API response field) rather than
     * reject the request.
     */
    public boolean canEdit(Dossier dossier, AccessContext ctx) {
        return hasPermission(dossier.tenantId(), dossier.dossierTypeCode(), ctx, DossierPermission.EDIT);
    }

    private void assertPermission(
            String tenantId, String dossierTypeCode, AccessContext ctx, DossierPermission required) {
        if (ctx instanceof AccessContext.Service) {
            throw new AccessDeniedException("Service context cannot access dossiers");
        }
        if (!hasPermission(tenantId, dossierTypeCode, ctx, required)) {
            throw new AccessDeniedException("Access denied for permission " + required);
        }
    }

    private boolean hasPermission(
            String tenantId, String dossierTypeCode, AccessContext ctx, DossierPermission required) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
        Objects.requireNonNull(ctx, "ctx");
        return switch (ctx) {
            case AccessContext.System ignored -> true;
            case AccessContext.Service ignored -> false;
            case AccessContext.User user -> {
                if (user.hasRole("TENANT_ADMIN") || user.hasRole("WORKFLOW_ADMIN") || user.hasRole("API_SERVICE")) {
                    yield true;
                }
                DossierTypeAclEntry entry =
                        aclRepository.find(tenantId, dossierTypeCode, user.userId()).orElse(null);
                yield entry != null && entry.permission().implies(required);
            }
        };
    }
}
