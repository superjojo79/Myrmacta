package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierConflictException;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AddDossierElementUseCaseImpl implements AddDossierElementUseCase {

    private final String tenantId;
    private final DossierRepository dossierRepository;
    private final DossierElementRepository elementRepository;
    private final DossierAccessPolicy accessPolicy;
    private final DossierWorkflowLinkRepository linkRepository;
    private final WorkflowParticipantPort participantPort;
    private final SendNotificationUseCase sendNotificationUseCase;

    public AddDossierElementUseCaseImpl(
            String tenantId,
            DossierRepository dossierRepository,
            DossierElementRepository elementRepository,
            DossierAccessPolicy accessPolicy,
            DossierWorkflowLinkRepository linkRepository,
            WorkflowParticipantPort participantPort,
            SendNotificationUseCase sendNotificationUseCase) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.participantPort = Objects.requireNonNull(participantPort, "participantPort");
        this.sendNotificationUseCase = Objects.requireNonNull(sendNotificationUseCase, "sendNotificationUseCase");
    }

    @Override
    public DossierElement add(AddCommand command, AccessContext ctx) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(ctx, "ctx");
        ElementType type = Objects.requireNonNull(command.type(), "type");
        if (command.label() == null || command.label().isBlank()) {
            throw new InvalidDossierElementException("label is required");
        }
        String storageRef = validateForType(type, command);

        Dossier dossier = dossierRepository
                .findById(Objects.requireNonNull(command.dossierId(), "dossierId"))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + command.dossierId()));
        if (!tenantId.equals(dossier.tenantId())) {
            throw new DossierNotFoundException("Dossier not found: " + command.dossierId());
        }
        accessPolicy.assertCanEdit(dossier, ctx);
        if (ctx instanceof AccessContext.User && dossier.status().blocksMutation()) {
            throw new DossierConflictException("Dossier elements cannot be added in status " + dossier.status());
        }

        Map<String, Object> metadata = command.metadata() == null ? Map.of() : command.metadata();
        DossierElement element = new DossierElement(
                UUID.randomUUID(),
                dossier.id(),
                tenantId,
                type,
                command.label().trim(),
                storageRef,
                metadata,
                command.addedBy() == null || command.addedBy().isBlank() ? "system" : command.addedBy(),
                Instant.now(),
                command.workflowStepId());
        DossierElement saved = elementRepository.save(element);
        if (type == ElementType.PRODUCED_DOC) {
            notifyParticipants(dossier.id());
        }
        return saved;
    }

    /**
     * M7 (TODO §66): {@code PRODUCED_DOC} has two real producers —
     * {@code AttachGeneratedDocumentTask} (BPMN delegate) and {@code GenerateDocumentTool} (MCP
     * tool) — both call this method, neither has a dossier-workflow-link concept of its own. This
     * is the single point common to both, avoiding duplicated participant-resolution logic in
     * each producer. No active link (dossier without a workflow, or a closed one) is a silent
     * no-op, not every PRODUCED_DOC element has participants to notify.
     */
    private void notifyParticipants(UUID dossierId) {
        Optional<DossierWorkflowLink> link = linkRepository.findActiveByDossierId(dossierId);
        if (link.isEmpty()) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "type", "DOCUMENT_GENERATED",
                "title", "Document généré",
                "body", "Un document a été généré sur le dossier suivi.",
                "linkId", link.get().id().toString(),
                "dossierId", dossierId.toString());
        for (String userId : participantPort.findParticipantUserIds(link.get())) {
            sendNotificationUseCase.send(
                    new NotificationEvent(List.of(NotificationChannel.BROWSER), null, null, null, userId, null, payload),
                    tenantId);
        }
    }

    /** Returns the storageRef to persist (null for metadata-only types), or throws on invalid input for the type. */
    private String validateForType(ElementType type, AddCommand command) {
        return switch (type) {
            case PRODUCED_DOC, ATTACHMENT -> requireValidStorageRef(command.storageRef());
            case INFORMATION -> {
                if (command.metadata() == null || command.metadata().isEmpty()) {
                    throw new InvalidDossierElementException("metadata is required for INFORMATION");
                }
                yield null;
            }
            case NOTE -> {
                if (!(metadataValue(command, "text") instanceof String text) || text.isBlank()) {
                    throw new InvalidDossierElementException("metadata.text is required for NOTE");
                }
                yield null;
            }
            case LINK -> {
                if (!(metadataValue(command, "url") instanceof String url) || !isAbsoluteUri(url)) {
                    throw new InvalidDossierElementException("metadata.url must be a valid absolute URI for LINK");
                }
                yield null;
            }
        };
    }

    private static Object metadataValue(AddCommand command, String key) {
        return command.metadata() == null ? null : command.metadata().get(key);
    }

    // Restricted to http(s): ElementsList.vue renders LINK.metadata.url as a clickable <a href>,
    // so a scheme like javascript: would let anyone with EDIT on the dossier type store a link
    // that executes in a viewer's session on click. LINK is never fetched server-side either way;
    // this only tightens what counts as a "valid absolute URI" for this element type.
    private static boolean isAbsoluteUri(String value) {
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            return uri.isAbsolute() && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String requireValidStorageRef(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) {
            throw new InvalidDossierElementException("storageRef is required");
        }
        String trimmed = storageRef.trim();
        if (!trimmed.startsWith(tenantId + "/") || trimmed.contains("..")) {
            throw new InvalidDossierElementException("storageRef must be tenant-scoped and opaque");
        }
        return trimmed;
    }
}
