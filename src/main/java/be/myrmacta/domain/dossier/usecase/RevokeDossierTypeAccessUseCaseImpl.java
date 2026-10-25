package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.port.in.RevokeDossierTypeAccessUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import java.util.Objects;

public final class RevokeDossierTypeAccessUseCaseImpl implements RevokeDossierTypeAccessUseCase {

    private final DossierTypeAclRepository aclRepository;
    private final TenantAdminGuard tenantAdminGuard;

    public RevokeDossierTypeAccessUseCaseImpl(
            DossierTypeAclRepository aclRepository, TenantAdminGuard tenantAdminGuard) {
        this.aclRepository = Objects.requireNonNull(aclRepository, "aclRepository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
    }

    @Override
    public void revoke(String tenantId, String dossierTypeCode, String subjectUserId, AccessContext.User actor) {
        tenantAdminGuard.requireTenantAdmin(actor);
        aclRepository.delete(
                Objects.requireNonNull(tenantId, "tenantId"),
                Objects.requireNonNull(dossierTypeCode, "dossierTypeCode"),
                Objects.requireNonNull(subjectUserId, "subjectUserId"));
    }
}
