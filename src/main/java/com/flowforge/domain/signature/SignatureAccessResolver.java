package com.flowforge.domain.signature;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.SignatureStatus;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerKind;
import com.flowforge.domain.signature.model.SignerStatus;
import com.flowforge.domain.signature.port.out.SignatureRequestRepository;
import com.flowforge.domain.workflow.portal.model.PortalAccessDeniedException;
import com.flowforge.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * TODO §72 — jeton → signataire externe {@code PENDING} d'une demande {@code PENDING} non échue
 * (contrôle paresseux de l'échéance, indépendant du job d'expiration), dossier existant. Tout échec
 * lève la <strong>même</strong> {@link PortalAccessDeniedException} (pas d'oracle, US-3). Aucun
 * identifiant fourni par le client n'est accepté : demande, signataire et dossier sont
 * <em>dérivés</em> du jeton (anti-IDOR, S-ACCESS-3/4). En séquentiel, un signataire {@code WAITING}
 * n'a pas d'accès (S-DEC-5).
 */
public final class SignatureAccessResolver {

    /** Un jeton légitime fait 43 caractères ; au-delà, inutile de hacher ni de chercher. */
    private static final int MAX_TOKEN_LENGTH = 128;

    private final SignatureRequestRepository repository;
    private final PortalTokenPort tokenPort;
    private final DossierRepository dossierRepository;
    private final Clock clock;

    public SignatureAccessResolver(
            SignatureRequestRepository repository,
            PortalTokenPort tokenPort,
            DossierRepository dossierRepository,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tokenPort = Objects.requireNonNull(tokenPort, "tokenPort");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ResolvedAccess resolve(String token) {
        Instant now = clock.instant();
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new PortalAccessDeniedException("missing or malformed signature token");
        }
        Signer signer = repository.findSignerByTokenHash(tokenPort.hash(token))
                .orElseThrow(() -> new PortalAccessDeniedException("unknown signature token"));
        if (signer.kind() != SignerKind.EXTERNAL || signer.status() != SignerStatus.PENDING) {
            throw new PortalAccessDeniedException("signer " + signer.id() + " not usable (" + signer.status() + ")");
        }
        SignatureRequest request = repository.findById(signer.requestId())
                .orElseThrow(() -> new PortalAccessDeniedException("request gone for signer " + signer.id()));
        if (request.status() != SignatureStatus.PENDING || !request.deadlineAt().isAfter(now)) {
            throw new PortalAccessDeniedException("request " + request.id() + " not usable (" + request.status() + ")");
        }
        Dossier dossier = dossierRepository.findById(request.dossierId())
                .orElseThrow(() -> new PortalAccessDeniedException("dossier gone for request " + request.id()));
        if (dossier.status().blocksMutation()) {
            throw new PortalAccessDeniedException("dossier " + dossier.id() + " is " + dossier.status());
        }
        return new ResolvedAccess(request, signer, dossier);
    }

    public record ResolvedAccess(SignatureRequest request, Signer signer, Dossier dossier) {}
}
