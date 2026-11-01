package be.myrmacta.domain.dossier;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierPermission;
import be.myrmacta.domain.dossier.model.DossierTypeAclEntry;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
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

    /**
     * Non-throwing VIEW check on a type (TODO §57 M12 generate ACL).
     */
    public boolean canViewType(String tenantId, String dossierTypeCode, AccessContext ctx) {
        return hasPermission(tenantId, dossierTypeCode, ctx, DossierPermission.VIEW);
    }

    /** TODO §113 (D-MAILROOM-DEPOSIT) — EDIT sur un type, sans exception : points de dépôt proposés à l'utilisateur. */
    public boolean canEditType(String tenantId, String dossierTypeCode, AccessContext ctx) {
        return hasPermission(tenantId, dossierTypeCode, ctx, DossierPermission.EDIT);
    }

    private void assertPermission(
            String tenantId, String dossierTypeCode, AccessContext ctx, DossierPermission required) {
        if (ctx instanceof AccessContext.Service) {
            throw new AccessDeniedException("Service context cannot access dossiers");
        }
        if (ctx instanceof AccessContext.External) {
            throw new AccessDeniedException("External portal context cannot access dossiers");
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
            // TODO §68 D-PORTAL-ACTOR : un tiers externe n'a aucun droit sur les dossiers ; le seul
            // chemin qui l'accepte (AddDossierElementUseCaseImpl, pièce jointe) a déjà validé le lien.
            case AccessContext.External ignored -> false;
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
