package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierElementView;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class AddDossierElementTool {

    private static final Set<ElementType> REJECTED_TYPES = Set.of(ElementType.PRODUCED_DOC, ElementType.ATTACHMENT);

    private final AddDossierElementUseCase addDossierElementUseCase;
    private final GetDossierUseCase getDossierUseCase;
    private final DossierAccessPolicy accessPolicy;
    private final McpAccessContextFactory accessContextFactory;

    public AddDossierElementTool(
            AddDossierElementUseCase addDossierElementUseCase,
            GetDossierUseCase getDossierUseCase,
            DossierAccessPolicy accessPolicy,
            McpAccessContextFactory accessContextFactory) {
        this.addDossierElementUseCase =
                Objects.requireNonNull(addDossierElementUseCase, "addDossierElementUseCase");
        this.getDossierUseCase = Objects.requireNonNull(getDossierUseCase, "getDossierUseCase");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "add_dossier_element",
            description = "Add a NOTE, LINK or INFORMATION element to a dossier. Binary types "
                    + "(ATTACHMENT, PRODUCED_DOC) are rejected — use generate_document for PDFs. "
                    + "dryRun=true validates without persisting.")
    public Object addDossierElement(
            @McpToolParam(required = true, description = "Dossier id (UUID)") String dossierId,
            @McpToolParam(required = true, description = "NOTE, LINK or INFORMATION") String type,
            @McpToolParam(required = true, description = "Element label") String label,
            @McpToolParam(
                            required = false,
                            description = "Type-specific metadata: NOTE.text, LINK.url (absolute URI), "
                                    + "INFORMATION non-empty map")
                    Map<String, Object> metadata,
            @McpToolParam(required = false, description = "Validate without persisting; default false")
                    Boolean dryRun) {
        AccessContext.User actor = accessContextFactory.currentActor();
        UUID id = McpArgs.requireUuid(dossierId, "dossierId");
        ElementType elementType = parseType(type);
        String rawLabel = McpArgs.requireNonBlank(label, "label");
        Map<String, Object> rawMetadata = McpArgs.optionalMap(metadata, "metadata");

        Dossier dossier = getDossierUseCase.get(id, actor);
        accessPolicy.assertCanEdit(dossier, actor);
        if (Boolean.TRUE.equals(dryRun)) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("dryRun", true);
            preview.put("valid", true);
            preview.put("dossierId", id.toString());
            preview.put("type", elementType.name());
            return preview;
        }
        DossierElement element = addDossierElementUseCase.add(
                new AddDossierElementUseCase.AddCommand(
                        id, elementType, rawLabel, null, rawMetadata, actor.userId(), null),
                actor);
        return DossierElementView.from(element);
    }

    private static ElementType parseType(String type) {
        String raw = McpArgs.requireNonBlank(type, "type");
        ElementType parsed;
        try {
            parsed = ElementType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("type must be NOTE, LINK or INFORMATION");
        }
        if (REJECTED_TYPES.contains(parsed)) {
            throw new IllegalArgumentException("type " + parsed + " must be added via its dedicated endpoint");
        }
        return parsed;
    }
}
