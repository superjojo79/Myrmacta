package be.myrmacta.domain.workflow.portal;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import be.myrmacta.domain.workflow.portal.model.PortalCredential;
import be.myrmacta.domain.workflow.portal.model.PortalIdentityField;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalLinkIdentity;
import be.myrmacta.domain.workflow.portal.model.PortalTaskDefinition;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityRepository;
import be.myrmacta.domain.workflow.portal.port.out.PortalLinkRepository;
import be.myrmacta.domain.workflow.portal.port.out.PortalTaskLookupPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    private final PortalIdentityRepository identities;

    public PortalAccessResolver(
            PortalLinkRepository repository,
            PortalTokenPort tokenPort,
            PortalTaskLookupPort taskLookup,
            DossierRepository dossierRepository,
            Clock clock) {
        this(repository, tokenPort, taskLookup, dossierRepository, clock, null);
    }

    /** {@code identities} nul : aucune preuve d'identité n'est acceptée (authentification forte absente). */
    public PortalAccessResolver(
            PortalLinkRepository repository,
            PortalTokenPort tokenPort,
            PortalTaskLookupPort taskLookup,
            DossierRepository dossierRepository,
            Clock clock,
            PortalIdentityRepository identities) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tokenPort = Objects.requireNonNull(tokenPort, "tokenPort");
        this.taskLookup = Objects.requireNonNull(taskLookup, "taskLookup");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identities = identities;
    }

    /**
     * F-PORTAL-1 — contrôle préalable à la lecture d'un corps volumineux (upload) : même refus que
     * {@link #resolve}, sans effet de bord ni lecture de la tâche ou du dossier. Ne remplace pas
     * {@link #resolve}, que le use case appelle ensuite.
     */
    public void assertUsableToken(String token) {
        assertUsable(new PortalCredential.LinkToken(token));
    }

    /**
     * Même contrôle préalable pour tout justificatif (TODO §104) : jeton vivant, ou preuve valable dont le lien est
     * vivant. Comme pour le jeton, aucune lecture de la tâche : l'exigence d'identité est vérifiée par {@link #resolve}
     * dans le use case. Le porteur du seul jeton d'une tâche à authentification forte peut donc faire lire un upload
     * avant son refus, comme tout porteur du lien le pouvait déjà ; la limite par IP s'applique.
     */
    public void assertUsable(PortalCredential credential) {
        usable(credential, clock.instant());
    }

    public ResolvedAccess resolve(String token) {
        return resolve(new PortalCredential.LinkToken(token));
    }

    /**
     * Accès pour agir (dépôt, soumission) : une tâche à authentification forte exige une identité vérifiée pour ce lien
     * (TODO §104, T3b). Tout manquement donne le refus neutre.
     */
    public ResolvedAccess resolve(PortalCredential credential) {
        ResolvedAccess access = resolveAllowingPendingIdentity(credential);
        if (access.identityRequired() && access.identity() == null) {
            throw new PortalAccessDeniedException("identity required for link " + access.link().id());
        }
        return access;
    }

    /**
     * Accès qui tolère une identité encore à vérifier : vue réduite de la tâche et démarrage de l'authentification
     * (TODO §104, T3b). L'appelant ne montre rien d'autre que le nom de la tâche et la référence tant que
     * {@link ResolvedAccess#identityRequired()} est vrai sans identité.
     */
    public ResolvedAccess resolveAllowingPendingIdentity(PortalCredential credential) {
        Instant now = clock.instant();
        Credentialed found = usable(credential, now);
        PortalLink link = found.link();
        PortalTaskLookupPort.OpenTask task = taskLookup.findOpenTask(link.taskId())
                .orElseThrow(() -> new PortalAccessDeniedException("task gone for link " + link.id()));
        Dossier dossier = dossierRepository.findById(link.dossierId())
                .orElseThrow(() -> new PortalAccessDeniedException("dossier gone for link " + link.id()));
        PortalLink accessed = link.accessed(now);
        if (!repository.transition(link, accessed)) {
            throw new PortalAccessDeniedException("link " + link.id() + " changed concurrently");
        }
        Optional<PortalTaskDefinition> definition = taskLookup.findExternalTaskDefinition(link.taskId());
        return new ResolvedAccess(accessed, task, dossier,
                definition.map(PortalTaskDefinition::identityField).orElse(null), found.identity(),
                definition.map(PortalTaskDefinition::pieceTypeKeys).orElse(List.of()));
    }

    private record Credentialed(PortalLink link, PortalLinkIdentity identity) {}

    private Credentialed usable(PortalCredential credential, Instant now) {
        return switch (Objects.requireNonNull(credential, "credential")) {
            case PortalCredential.LinkToken token -> new Credentialed(usableLink(token.value(), now), null);
            case PortalCredential.IdentityProof proof -> {
                PortalLinkIdentity identity = usableIdentity(proof.value(), now);
                PortalLink link = repository.findById(identity.linkId())
                        .filter(candidate -> candidate.isUsable(now))
                        .orElseThrow(() -> new PortalAccessDeniedException(
                                "link " + identity.linkId() + " of the identity proof is not usable"));
                yield new Credentialed(link, identity);
            }
        };
    }

    private PortalLinkIdentity usableIdentity(String proof, Instant now) {
        if (identities == null || proof == null || proof.isBlank() || proof.length() > MAX_TOKEN_LENGTH) {
            throw new PortalAccessDeniedException("missing or malformed identity proof");
        }
        return identities.findByProofHash(tokenPort.hash(proof))
                .filter(identity -> identity.isUsable(now))
                .orElseThrow(() -> new PortalAccessDeniedException("unknown, expired or used identity proof"));
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

    /**
     * {@code identityField} : champ d'identité de la tâche, nul sans authentification forte. {@code identity} : identité
     * vérifiée présentée par la preuve, nulle sur un accès par le jeton. {@code pieceTypeKeys} : types de pièces que la
     * tâche propose au tiers (TODO §105), vide sans {@code flowable:portalPieceTypes}.
     */
    public record ResolvedAccess(
            PortalLink link, PortalTaskLookupPort.OpenTask task, Dossier dossier, PortalIdentityField identityField,
            PortalLinkIdentity identity, List<String> pieceTypeKeys) {

        public ResolvedAccess {
            pieceTypeKeys = pieceTypeKeys == null ? List.of() : List.copyOf(pieceTypeKeys);
        }

        public ResolvedAccess(PortalLink link, PortalTaskLookupPort.OpenTask task, Dossier dossier) {
            this(link, task, dossier, null, null, List.of());
        }

        public ResolvedAccess(PortalLink link, PortalTaskLookupPort.OpenTask task, Dossier dossier,
                PortalIdentityField identityField, PortalLinkIdentity identity) {
            this(link, task, dossier, identityField, identity, List.of());
        }

        public boolean identityRequired() {
            return identityField != null;
        }
    }
}
