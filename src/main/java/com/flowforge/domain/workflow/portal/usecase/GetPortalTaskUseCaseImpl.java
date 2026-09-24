package com.flowforge.domain.workflow.portal.usecase;

import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.portal.PortalAccessResolver;
import com.flowforge.domain.workflow.portal.model.PortalAccessDeniedException;
import com.flowforge.domain.workflow.portal.port.in.GetPortalTaskUseCase;
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

    public GetPortalTaskUseCaseImpl(
            String tenantId,
            PortalAccessResolver accessResolver,
            TaskQuestionSchemaRepository schemaRepository,
            DossierElementRepository elementRepository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.schemaRepository = Objects.requireNonNull(schemaRepository, "schemaRepository");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
    }

    @Override
    public PortalTaskView get(String token) {
        PortalAccessResolver.ResolvedAccess access = accessResolver.resolve(token);
        TaskQuestionSchema schema = schemaRepository.findByKey(tenantId, access.link().schemaKey())
                .orElseThrow(() -> new PortalAccessDeniedException("schema gone for link " + access.link().id()));
        return new PortalTaskView(
                access.dossier().reference(),
                access.task().name(),
                access.task().instructions(),
                schema.label(),
                schema.fields(),
                access.link().expiresAt(),
                ownAttachments(access));
    }

    private List<DossierElement> ownAttachments(PortalAccessResolver.ResolvedAccess access) {
        String actorId = access.link().actorId();
        return elementRepository.findByDossierIdOrderByAddedAtAsc(access.dossier().id()).stream()
                .filter(e -> e.type() == ElementType.ATTACHMENT && actorId.equals(e.addedBy()))
                .toList();
    }
}
