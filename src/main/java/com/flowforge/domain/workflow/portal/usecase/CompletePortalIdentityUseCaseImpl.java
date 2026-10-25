package com.flowforge.domain.workflow.portal.usecase;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.workflow.portal.PortalIdentityFieldValue;
import com.flowforge.domain.workflow.portal.model.IdentityMatch;
import com.flowforge.domain.workflow.portal.model.PortalIdentityAttempt;
import com.flowforge.domain.workflow.portal.model.PortalIdentityField;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.model.PortalLinkIdentity;
import com.flowforge.domain.workflow.portal.model.PortalTaskDefinition;
import com.flowforge.domain.workflow.portal.model.VerifiedIdentity;
import com.flowforge.domain.workflow.portal.port.in.CompletePortalIdentityUseCase;
import com.flowforge.domain.workflow.portal.port.out.PortalIdentityProviderPort;
import com.flowforge.domain.workflow.portal.port.out.PortalIdentityProviderPort.IdentityProviderException;
import com.flowforge.domain.workflow.portal.port.out.PortalIdentityRepository;
import com.flowforge.domain.workflow.portal.port.out.PortalLinkRepository;
import com.flowforge.domain.workflow.portal.port.out.PortalSecretCipherPort;
import com.flowforge.domain.workflow.portal.port.out.PortalTaskLookupPort;
import com.flowforge.domain.workflow.portal.port.out.PortalTokenPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * TODO §104 (T4-T7, T10, D-BEAUTH-PROOF) — retour du fournisseur, dans l'ordre :
 * <ol>
 *   <li>tentative consommée (usage unique, avant tout appel au fournisseur : un {@code state} rejoué ne sert plus) ;</li>
 *   <li>lien vivant, tâche ouverte à authentification forte ;</li>
 *   <li>échange du code et validation de l'ID token par le fournisseur ;</li>
 *   <li>niveau substantiel ou élevé (T4) ;</li>
 *   <li>correspondance avec le champ d'identité (T5, T7) : non conforme, refus et rien n'est conservé ;</li>
 *   <li>identité enregistrée pour le lien (NRN chiffré), et preuve remise au navigateur.</li>
 * </ol>
 * Tout refus a la même apparence pour le tiers ; le motif ne contient jamais de NRN.
 */
public final class CompletePortalIdentityUseCaseImpl implements CompletePortalIdentityUseCase {

    private final String tenantId;
    private final PortalIdentityRepository identities;
    private final PortalLinkRepository links;
    private final PortalTaskLookupPort taskLookup;
    private final DossierRepository dossiers;
    private final PortalIdentityProviderPort providers;
    private final PortalTokenPort tokenPort;
    private final PortalSecretCipherPort cipher;
    private final Clock clock;
    private final Duration identityTtl;

    public CompletePortalIdentityUseCaseImpl(
            String tenantId,
            PortalIdentityRepository identities,
            PortalLinkRepository links,
            PortalTaskLookupPort taskLookup,
            DossierRepository dossiers,
            PortalIdentityProviderPort providers,
            PortalTokenPort tokenPort,
            PortalSecretCipherPort cipher,
            Clock clock,
            Duration identityTtl) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.links = Objects.requireNonNull(links, "links");
        this.taskLookup = Objects.requireNonNull(taskLookup, "taskLookup");
        this.dossiers = Objects.requireNonNull(dossiers, "dossiers");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.tokenPort = Objects.requireNonNull(tokenPort, "tokenPort");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identityTtl = Objects.requireNonNull(identityTtl, "identityTtl");
    }

    @Override
    public Outcome complete(String state, String code, String error) {
        Instant now = clock.instant();
        if (state == null || state.isBlank() || state.length() > 128) {
            return Outcome.refused("missing or malformed state");
        }
        Optional<PortalIdentityAttempt> consumed = identities.consumeAttempt(tokenPort.hash(state), now);
        if (consumed.isEmpty()) {
            return Outcome.refused("unknown, used or expired state");
        }
        PortalIdentityAttempt attempt = consumed.get();
        if (error != null || code == null || code.isBlank()) {
            return Outcome.refused("provider returned no code (cancelled or error)");
        }
        Optional<PortalLink> link = links.findById(attempt.linkId()).filter(candidate -> candidate.isUsable(now));
        if (link.isEmpty() || taskLookup.findOpenTask(link.get().taskId()).isEmpty()) {
            return Outcome.refused("link or task no longer usable");
        }
        PortalIdentityField field = taskLookup.findExternalTaskDefinition(link.get().taskId())
                .map(PortalTaskDefinition::identityField).orElse(null);
        Optional<Dossier> dossier = dossiers.findById(link.get().dossierId());
        if (field == null || dossier.isEmpty()) {
            return Outcome.refused("task without identity field or dossier gone");
        }
        VerifiedIdentity identity;
        try {
            identity = providers.complete(attempt.provider(), code, cipher.decrypt(attempt.pkceVerifierEnc()),
                    attempt.nonce());
        } catch (IdentityProviderException ex) {
            return Outcome.refused("provider exchange failed: " + ex.getMessage());
        }
        if (!identity.level().isAccepted()) {
            return Outcome.refused("assurance level " + identity.level() + " is not accepted");
        }
        IdentityMatch match = IdentityMatch.of(PortalIdentityFieldValue.of(dossier.get(), field),
                identity.nrnOrNull());
        if (!match.allowsSubmission()) {
            return Outcome.refused("verified identity does not match the identity field");
        }
        String proof = tokenPort.newToken();
        identities.saveIdentity(new PortalLinkIdentity(link.get().id(), tenantId, tokenPort.hash(proof),
                identity.provider(), identity.level(), identity.givenName(), identity.familyName(),
                identity.nrn().map(nrn -> cipher.encrypt(nrn.digits())).orElse(null), match, now, now.plus(identityTtl),
                null));
        return Outcome.verified(proof);
    }
}
