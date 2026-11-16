package be.myrmacta.infrastructure.web.workflow;

import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.port.out.KnownUserRepository;
import be.myrmacta.domain.workflow.model.ConfigPublication;
import be.myrmacta.domain.workflow.model.DraftArtifactType;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.domain.workflow.model.PublicationMode;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * TODO §145 (D-DRAFT-PUB-NAME) — forme HTTP du journal des publications : le sujet technique reste dans
 * {@code publishedBy} ; {@code publishedByDisplayName} vient de l'annuaire local (D-ASSIGN-DIR), avec repli sur le sujet.
 */
final class DraftPublicationResponses {

    private final String tenantId;
    private final KnownUserRepository knownUsers;

    DraftPublicationResponses(TenantProperties tenant, KnownUserRepository knownUsers) {
        this.tenantId = Objects.requireNonNull(tenant, "tenant").id();
        this.knownUsers = Objects.requireNonNull(knownUsers, "knownUsers");
    }

    ConfigPublicationResponse of(ConfigPublication publication) {
        return of(publication, displayNames());
    }

    List<ConfigPublicationResponse> of(List<ConfigPublication> publications) {
        Map<String, String> names = displayNames();
        return publications.stream().map(p -> of(p, names)).toList();
    }

    private ConfigPublicationResponse of(ConfigPublication p, Map<String, String> names) {
        String displayName = names.getOrDefault(p.publishedBy(), p.publishedBy());
        return new ConfigPublicationResponse(
                p.id(),
                p.tenantId(),
                p.artifactType(),
                p.artifactKey(),
                p.version(),
                p.draftProvenance(),
                p.draftAuthor(),
                p.publishedBy(),
                displayName,
                p.publishedAt(),
                p.mode(),
                p.configProvenance(),
                p.configAuthor());
    }

    private Map<String, String> displayNames() {
        return knownUsers.findAll(tenantId).stream()
                .collect(Collectors.toMap(KnownUser::userId, KnownUser::displayName, (first, second) -> first));
    }
}

record ConfigPublicationResponse(
        UUID id,
        String tenantId,
        DraftArtifactType artifactType,
        String artifactKey,
        Integer version,
        Provenance draftProvenance,
        String draftAuthor,
        String publishedBy,
        String publishedByDisplayName,
        Instant publishedAt,
        PublicationMode mode,
        Provenance configProvenance,
        String configAuthor) {}
