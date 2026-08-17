package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierConflictException;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class AddDossierElementUseCaseImpl implements AddDossierElementUseCase {

    private static final List<DossierStatus> BLOCKED_STATUSES = List.of(DossierStatus.CLOTURE, DossierStatus.ARCHIVE);

    private final String tenantId;
    private final DossierRepository dossierRepository;
    private final DossierElementRepository elementRepository;
    private final DossierAccessPolicy accessPolicy;

    public AddDossierElementUseCaseImpl(
            String tenantId,
            DossierRepository dossierRepository,
            DossierElementRepository elementRepository,
            DossierAccessPolicy accessPolicy) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
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
        if (ctx instanceof AccessContext.User && BLOCKED_STATUSES.contains(dossier.status())) {
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
        return elementRepository.save(element);
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

    private static boolean isAbsoluteUri(String value) {
        try {
            return new URI(value.trim()).isAbsolute();
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
