package be.myrmacta.domain.workflow.portal.usecase;

import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.portal.PortalAccessResolver;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import be.myrmacta.domain.workflow.portal.model.PortalCredential;
import be.myrmacta.domain.workflow.portal.model.PortalLinkIdentity;
import be.myrmacta.domain.workflow.portal.port.in.GetPortalTaskUseCase;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityProviderPort;
import java.util.List;
import java.util.Objects;

/**
 * TODO §68 D-PORTAL-DATA — référence du dossier, nom et consignes de la tâche, formulaire et
 * pièces déposées <strong>par ce lien</strong> uniquement (T-IDOR-1).
 */
public final class GetPortalTaskUseCaseImpl implements GetPortalTaskUseCase {

    private final String tenantId;
    private final PortalAccessResolver accessResolver;
    private final TaskQuestionSchemaRepository schemaRepository;
    private final DossierElementRepository elementRepository;
    private final PortalIdentityProviderPort identityProviders;

    public GetPortalTaskUseCaseImpl(
            String tenantId,
            PortalAccessResolver accessResolver,
            TaskQuestionSchemaRepository schemaRepository,
            DossierElementRepository elementRepository) {
        this(tenantId, accessResolver, schemaRepository, elementRepository, PortalIdentityProviderPort.NONE);
    }

    public GetPortalTaskUseCaseImpl(
            String tenantId,
            PortalAccessResolver accessResolver,
            TaskQuestionSchemaRepository schemaRepository,
            DossierElementRepository elementRepository,
            PortalIdentityProviderPort identityProviders) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.schemaRepository = Objects.requireNonNull(schemaRepository, "schemaRepository");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.identityProviders = Objects.requireNonNull(identityProviders, "identityProviders");
    }

    @Override
    public PortalTaskView get(PortalCredential credential) {
        PortalAccessResolver.ResolvedAccess access = accessResolver.resolveAllowingPendingIdentity(credential);
        if (access.identityRequired() && access.identity() == null) {
            // TODO §104 (T3b) — avant l'authentification : référence et nom de la tâche, rien d'autre.
            return new PortalTaskView(access.dossier().reference(), access.task().name(), null, null, List.of(),
                    access.link().expiresAt(), List.of(), true, identityProviders.providers(), null);
        }
        TaskQuestionSchema schema = schemaRepository.findByKey(tenantId, access.link().schemaKey())
                .orElseThrow(() -> new PortalAccessDeniedException("schema gone for link " + access.link().id()));
        return new PortalTaskView(
                access.dossier().reference(),
                access.task().name(),
                access.task().instructions(),
                schema.label(),
                schema.fields(),
                access.link().expiresAt(),
                ownAttachments(access),
                access.identityRequired(),
                List.of(),
                access.identity() == null ? null : verifiedName(access.identity()));
    }

    private static String verifiedName(PortalLinkIdentity identity) {
        String name = ((identity.givenName() == null ? "" : identity.givenName()) + " "
                + (identity.familyName() == null ? "" : identity.familyName())).trim();
        return name.isEmpty() ? null : name;
    }

    private List<DossierElement> ownAttachments(PortalAccessResolver.ResolvedAccess access) {
        String actorId = access.link().actorId();
        return elementRepository.findByDossierIdOrderByAddedAtAsc(access.dossier().id()).stream()
                .filter(e -> e.type() == ElementType.ATTACHMENT && actorId.equals(e.addedBy()))
                .toList();
    }
}
