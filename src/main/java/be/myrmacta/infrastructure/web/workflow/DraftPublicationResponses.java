package be.myrmacta.infrastructure.web.workflow;

import be.myrmacta.domain.user.port.out.KnownUserRepository;
import be.myrmacta.domain.workflow.model.ConfigPublication;
import be.myrmacta.domain.workflow.model.DraftArtifactType;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.domain.workflow.model.PublicationMode;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.web.user.UserDisplayNames;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TODO §145 (D-DRAFT-PUB-NAME) — forme HTTP du journal des publications : le sujet technique reste dans
 * {@code publishedBy} ; {@code publishedByDisplayName} vient de l'annuaire local (D-ASSIGN-DIR), avec repli sur le sujet.
 */
final class DraftPublicationResponses {

    private final UserDisplayNames directory;

    DraftPublicationResponses(TenantProperties tenant, KnownUserRepository knownUsers) {
        this.directory = new UserDisplayNames(tenant, knownUsers);
    }

    ConfigPublicationResponse of(ConfigPublication publication) {
        return of(publication, directory.asMap());
    }

    List<ConfigPublicationResponse> of(List<ConfigPublication> publications) {
        Map<String, String> names = directory.asMap();
        return publications.stream().map(p -> of(p, names)).toList();
    }

    private ConfigPublicationResponse of(ConfigPublication p, Map<String, String> names) {
        return new ConfigPublicationResponse(
                p.id(),
                p.tenantId(),
                p.artifactType(),
                p.artifactKey(),
                p.version(),
                p.draftProvenance(),
                p.draftAuthor(),
                p.publishedBy(),
                directory.resolve(p.publishedBy(), names),
                p.publishedAt(),
                p.mode(),
                p.configProvenance(),
                p.configAuthor());
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
