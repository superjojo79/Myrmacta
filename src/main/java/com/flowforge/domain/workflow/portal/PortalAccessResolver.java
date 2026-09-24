package com.flowforge.domain.workflow.portal;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.workflow.portal.model.PortalAccessDeniedException;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.port.out.PortalLinkRepository;
import com.flowforge.domain.workflow.portal.port.out.PortalTaskLookupPort;
import com.flowforge.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * TODO §68 — jeton → lien {@code ACTIVE}, non expiré (contrôle paresseux, indépendant du job
 * d'expiration), tâche encore ouverte, dossier existant. Tout échec lève la même
 * {@link PortalAccessDeniedException}. Aucun identifiant fourni par le client n'est accepté : le
 * dossier et la tâche sont <em>dérivés</em> du lien (anti-IDOR).
 */
public final class PortalAccessResolver {

    /** Un jeton légitime fait 43 caractères ; au-delà, inutile de hacher (coût) ni de chercher. */
    private static final int MAX_TOKEN_LENGTH = 128;

    private final PortalLinkRepository repository;
    private final PortalTokenPort tokenPort;
    private final PortalTaskLookupPort taskLookup;
    private final DossierRepository dossierRepository;
    private final Clock clock;

    public PortalAccessResolver(
            PortalLinkRepository repository,
            PortalTokenPort tokenPort,
            PortalTaskLookupPort taskLookup,
            DossierRepository dossierRepository,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tokenPort = Objects.requireNonNull(tokenPort, "tokenPort");
        this.taskLookup = Objects.requireNonNull(taskLookup, "taskLookup");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * F-PORTAL-1 — contrôle préalable à la lecture d'un corps volumineux (upload) : même refus que
     * {@link #resolve}, sans effet de bord ni lecture de la tâche ou du dossier. Ne remplace pas
     * {@link #resolve}, que le use case appelle ensuite.
     */
    public void assertUsableToken(String token) {
        usableLink(token, clock.instant());
    }

    public ResolvedAccess resolve(String token) {
        Instant now = clock.instant();
        PortalLink link = usableLink(token, now);
        PortalTaskLookupPort.OpenTask task = taskLookup.findOpenTask(link.taskId())
                .orElseThrow(() -> new PortalAccessDeniedException("task gone for link " + link.id()));
        Dossier dossier = dossierRepository.findById(link.dossierId())
                .orElseThrow(() -> new PortalAccessDeniedException("dossier gone for link " + link.id()));
        PortalLink accessed = link.accessed(now);
        if (!repository.transition(link, accessed)) {
            throw new PortalAccessDeniedException("link " + link.id() + " changed concurrently");
        }
        return new ResolvedAccess(accessed, task, dossier);
    }

    private PortalLink usableLink(String token, Instant now) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new PortalAccessDeniedException("missing or malformed token");
        }
        PortalLink link = repository.findByTokenHash(tokenPort.hash(token))
                .orElseThrow(() -> new PortalAccessDeniedException("unknown token"));
        if (!link.isUsable(now)) {
            throw new PortalAccessDeniedException("link " + link.id() + " not usable (" + link.status() + ")");
        }
        return link;
    }

    public record ResolvedAccess(PortalLink link, PortalTaskLookupPort.OpenTask task, Dossier dossier) {}
}
