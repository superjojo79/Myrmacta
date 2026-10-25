package com.flowforge.infrastructure.persistence.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.workflow.portal.model.IdentityAssuranceLevel;
import com.flowforge.domain.workflow.portal.model.IdentityMatch;
import com.flowforge.domain.workflow.portal.model.IdentityVerification;
import com.flowforge.domain.workflow.portal.model.PortalIdentityAttempt;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.model.PortalLinkIdentity;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatusReason;
import com.flowforge.domain.workflow.portal.port.out.PortalIdentityRepository;
import com.flowforge.domain.workflow.portal.port.out.PortalLinkRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** TODO §104 (D-BEAUTH-STORE, T9) — tentatives à usage unique, identité par lien, trace sans NRN, nettoyage, cascade. */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class PortalIdentityRepositoryIT {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired private PortalIdentityRepository identities;
    @Autowired private PortalLinkRepository links;
    @Autowired private JdbcTemplate jdbcTemplate;

    private PortalLink link;

    @BeforeEach
    void activeLink() {
        PortalLink pending = PortalLink.pendingSend(UUID.randomUUID(), TENANT, UUID.randomUUID(),
                "task-" + UUID.randomUUID(), "pi-1", "avis", "alice@example.org", 7, NOW, "test");
        links.insert(pending);
        link = pending.activated("hash-" + UUID.randomUUID(), NOW);
        assertThat(links.transition(pending, link)).isTrue();
    }

    private PortalIdentityAttempt attempt(String state, Instant expiresAt) {
        return new PortalIdentityAttempt(UUID.randomUUID(), TENANT, link.id(), state, "fas", "nonce", "enc:v1:pkce", NOW,
                expiresAt);
    }

    private PortalLinkIdentity identity(String proof, Instant expiresAt) {
        return new PortalLinkIdentity(link.id(), TENANT, proof, "fas", IdentityAssuranceLevel.HIGH, "Alice", "Martin",
                "enc:v1:nrn", IdentityMatch.PREMIER_ENREGISTREMENT, NOW, expiresAt, null);
    }

    @Test
    void anAttemptIsConsumedOnlyOnceAndNeverAfterItsExpiry() {
        String state = "state-" + UUID.randomUUID();
        identities.insertAttempt(attempt(state, NOW.plus(Duration.ofMinutes(10))));

        assertThat(identities.consumeAttempt(state, NOW)).hasValueSatisfying(a -> {
            assertThat(a.linkId()).isEqualTo(link.id());
            assertThat(a.pkceVerifierEnc()).isEqualTo("enc:v1:pkce");
        });
        assertThat(identities.consumeAttempt(state, NOW)).as("rejeu").isEmpty();

        String expired = "state-" + UUID.randomUUID();
        identities.insertAttempt(attempt(expired, NOW.minusSeconds(1)));
        assertThat(identities.consumeAttempt(expired, NOW)).isEmpty();
        assertThat(identities.consumeAttempt("inconnu", NOW)).isEmpty();
    }

    @Test
    void aLinkHasOneIdentityAndANewAuthenticationReplacesIt() {
        identities.saveIdentity(identity("proof-1-" + link.id(), NOW.plus(Duration.ofHours(1))));
        identities.saveIdentity(identity("proof-2-" + link.id(), NOW.plus(Duration.ofHours(1))));

        assertThat(identities.findByProofHash("proof-1-" + link.id())).isEmpty();
        assertThat(identities.findByProofHash("proof-2-" + link.id())).hasValueSatisfying(i -> {
            assertThat(i.linkId()).isEqualTo(link.id());
            assertThat(i.isUsable(NOW)).isTrue();
            assertThat(i.nrnEnc()).isEqualTo("enc:v1:nrn");
        });
    }

    /** T8, T9 — la soumission efface la preuve et le NRN ; la trace reste, montrée à l'instructeur. */
    @Test
    void submissionErasesTheProofAndTheNumberButKeepsTheTrace() {
        identities.saveIdentity(identity("proof-" + link.id(), NOW.plus(Duration.ofHours(1))));

        assertThat(identities.markSubmitted(link.id(), NOW)).isTrue();
        assertThat(identities.markSubmitted(link.id(), NOW)).as("une seule fois").isFalse();

        PortalLinkIdentity trace = identities.findByLinkId(link.id()).orElseThrow();
        assertThat(trace.proofHash()).isNull();
        assertThat(trace.nrnEnc()).isNull();
        assertThat(trace.isUsable(NOW)).isFalse();
        assertThat(identities.findByProofHash("proof-" + link.id())).isEmpty();
        assertThat(identities.tracesByLinkId(List.of(link.id(), UUID.randomUUID()))).containsExactly(
                java.util.Map.entry(link.id(), new IdentityVerification("fas", IdentityAssuranceLevel.HIGH, "Alice",
                        "Martin", IdentityMatch.PREMIER_ENREGISTREMENT, NOW)));

        identities.deleteUnsubmitted(link.id());
        assertThat(identities.findByLinkId(link.id())).as("la trace d'une soumission reste").isPresent();
        identities.purgeExpired(NOW.plus(Duration.ofDays(1)));
        assertThat(identities.findByLinkId(link.id())).as("même après le nettoyage").isPresent();
    }

    @Test
    void cleanupRemovesExpiredIdentitiesAndThoseOfALinkThatIsNoLongerActive() {
        identities.saveIdentity(identity("proof-" + link.id(), NOW.plus(Duration.ofHours(1))));
        identities.purgeExpired(NOW);
        assertThat(identities.findByLinkId(link.id())).as("encore valable").isPresent();

        assertThat(links.transition(link, link.revoked(NOW, "u1", PortalLinkStatusReason.REVOKED_BY_USER))).isTrue();
        identities.purgeExpired(NOW);
        assertThat(identities.findByLinkId(link.id())).as("lien révoqué").isEmpty();
    }

    @Test
    void attemptsAndIdentitiesDisappearWithTheirLink() {
        identities.insertAttempt(attempt("state-" + UUID.randomUUID(), NOW.plus(Duration.ofMinutes(10))));
        identities.saveIdentity(identity("proof-" + link.id(), NOW.plus(Duration.ofHours(1))));

        links.deleteAllByDossierId(link.dossierId());

        assertThat(jdbcTemplate.queryForObject("select count(*) from portal_identity_attempt where portal_link_id = ?",
                Integer.class, link.id())).isZero();
        assertThat(identities.findByLinkId(link.id())).isEmpty();
    }
}
