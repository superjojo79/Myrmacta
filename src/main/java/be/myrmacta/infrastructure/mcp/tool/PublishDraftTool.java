package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.workflow.model.ConfigPublication;
import be.myrmacta.domain.workflow.model.DraftArtifactType;
import be.myrmacta.domain.workflow.model.PublicationMode;
import be.myrmacta.domain.workflow.port.in.ManageDraftsUseCase;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * TODO §82 (D6, D-MCP-LC-PUBLISH = C) — publie un brouillon <b>seulement</b> si l'instance autorise la publication directe
 * ({@code app.mcp.publication.mode=direct}, jamais en production) ; sinon refus explicite : un humain publie depuis l'écran.
 * Même cas d'usage que l'écran d'administration.
 */
@Component
public class PublishDraftTool {

    private final ManageDraftsUseCase manageDraftsUseCase;
    private final McpAccessContextFactory accessContextFactory;

    public PublishDraftTool(ManageDraftsUseCase manageDraftsUseCase, McpAccessContextFactory accessContextFactory) {
        this.manageDraftsUseCase = Objects.requireNonNull(manageDraftsUseCase, "manageDraftsUseCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "publish_draft",
            description = "Publish a BPMN or DMN draft. Refused unless the instance allows direct publication "
                    + "(app.mcp.publication.mode=direct, never in production): by default a human publishes the draft from the "
                    + "'Brouillons à publier' screen. expectedUpdatedAt (optional) guards against publishing a draft that "
                    + "changed since you read it.")
    public ConfigPublication publishDraft(
            @McpToolParam(required = true, description = "BPMN or DMN") String type,
            @McpToolParam(required = true, description = "Process definition key or decision key") String key,
            @McpToolParam(required = false, description = "ISO-8601 updatedAt of the draft you reviewed") String expectedUpdatedAt) {
        DraftArtifactType artifactType;
        try {
            artifactType = DraftArtifactType.valueOf(Objects.requireNonNull(type, "type").strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("type must be BPMN or DMN");
        }
        Instant expected;
        try {
            expected = expectedUpdatedAt == null || expectedUpdatedAt.isBlank() ? null : Instant.parse(expectedUpdatedAt.strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("expectedUpdatedAt must be an ISO-8601 instant");
        }
        return manageDraftsUseCase.publish(accessContextFactory.currentActor(), artifactType, key, expected, PublicationMode.DIRECT);
    }
}
