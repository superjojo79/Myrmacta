package be.myrmacta.domain.workflow.portal.usecase;

import be.myrmacta.domain.workflow.portal.PortalAccessResolver;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import be.myrmacta.domain.workflow.portal.model.PortalCredential;
import be.myrmacta.domain.workflow.portal.model.PortalIdentityAttempt;
import be.myrmacta.domain.workflow.portal.port.in.StartPortalIdentityUseCase;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityProviderPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityProviderPort.AuthorizationRequest;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityProviderPort.IdentityProviderException;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityRepository;
import be.myrmacta.domain.workflow.portal.port.out.PortalSecretCipherPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalTokenPort;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §104 (T3, D-BEAUTH-STORE) — ouvre une tentative d'authentification forte pour un lien vivant dont la tâche
 * l'exige : {@code state} et {@code nonce} aléatoires (seul le hash du state est stocké), vérificateur PKCE chiffré,
 * dix minutes, usage unique. Lien inconnu, tâche sans champ d'identité ou fournisseur inconnu : refus neutre.
 */
public final class StartPortalIdentityUseCaseImpl implements StartPortalIdentityUseCase {

    static final Duration ATTEMPT_TTL = Duration.ofMinutes(10);

    private final String tenantId;
    private final PortalAccessResolver accessResolver;
    private final PortalIdentityProviderPort providers;
    private final PortalIdentityRepository identities;
    private final PortalTokenPort tokenPort;
    private final PortalSecretCipherPort cipher;
    private final Clock clock;

    public StartPortalIdentityUseCaseImpl(
            String tenantId,
            PortalAccessResolver accessResolver,
            PortalIdentityProviderPort providers,
            PortalIdentityRepository identities,
            PortalTokenPort tokenPort,
            PortalSecretCipherPort cipher,
            Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.tokenPort = Objects.requireNonNull(tokenPort, "tokenPort");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public URI start(PortalCredential credential, String provider) {
        PortalAccessResolver.ResolvedAccess access = accessResolver.resolveAllowingPendingIdentity(credential);
        if (!access.identityRequired()) {
            throw new PortalAccessDeniedException("link " + access.link().id() + " does not require strong authentication");
        }
        String state = tokenPort.newToken();
        String nonce = tokenPort.newToken();
        AuthorizationRequest request;
        try {
            request = providers.begin(provider, state, nonce);
        } catch (IdentityProviderException ex) {
            throw new PortalAccessDeniedException("identity provider unavailable: " + ex.getMessage());
        }
        Instant now = clock.instant();
        identities.insertAttempt(new PortalIdentityAttempt(UUID.randomUUID(), tenantId, access.link().id(),
                tokenPort.hash(state), provider, nonce, cipher.encrypt(request.pkceVerifier()), now,
                now.plus(ATTEMPT_TTL)));
        return request.authorizationUri();
    }
}
