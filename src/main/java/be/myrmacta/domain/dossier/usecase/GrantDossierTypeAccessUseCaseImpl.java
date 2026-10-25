package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierTypeAclEntry;
import be.myrmacta.domain.dossier.port.in.GrantDossierTypeAccessUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.util.Objects;

public final class GrantDossierTypeAccessUseCaseImpl implements GrantDossierTypeAccessUseCase {

    private final DossierTypeRepository typeRepository;
    private final DossierTypeAclRepository aclRepository;
    private final TenantAdminGuard tenantAdminGuard;

    public GrantDossierTypeAccessUseCaseImpl(
            DossierTypeRepository typeRepository,
            DossierTypeAclRepository aclRepository,
            TenantAdminGuard tenantAdminGuard) {
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.aclRepository = Objects.requireNonNull(aclRepository, "aclRepository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
    }

    @Override
    public void grant(GrantCommand command, AccessContext.User actor) {
        Objects.requireNonNull(command, "command");
        tenantAdminGuard.requireTenantAdmin(actor);

        typeRepository.findByCode(command.tenantId(), command.dossierTypeCode())
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier type not found: " + command.tenantId() + "/" + command.dossierTypeCode()));

        aclRepository.save(new DossierTypeAclEntry(
                command.tenantId(),
                command.dossierTypeCode(),
                command.subjectUserId(),
                command.permission()));
    }
}
