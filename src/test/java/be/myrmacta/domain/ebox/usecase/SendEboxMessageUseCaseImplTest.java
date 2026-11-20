package be.myrmacta.domain.ebox.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.ebox.model.EboxConsent;
import be.myrmacta.domain.ebox.model.EboxConsent.Level;
import be.myrmacta.domain.ebox.model.EboxDelivery;
import be.myrmacta.domain.ebox.model.EboxDocument;
import be.myrmacta.domain.ebox.model.EboxLanguage;
import be.myrmacta.domain.ebox.model.EboxOutgoingMessage;
import be.myrmacta.domain.ebox.model.EboxProofOfDispatch;
import be.myrmacta.domain.ebox.model.EboxPublication;
import be.myrmacta.domain.ebox.model.EboxPublicationRejectedException;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxRegisteredMailUnresolvedException;
import be.myrmacta.domain.ebox.model.EboxRoute;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry.Outcome;
import be.myrmacta.domain.ebox.model.EboxSendOutcome;
import be.myrmacta.domain.ebox.model.EboxSendUncertainException;
import be.myrmacta.domain.ebox.model.EboxSenderAccount;
import be.myrmacta.domain.ebox.model.FallbackChannel;
import be.myrmacta.domain.ebox.model.SendEboxCommand;
import be.myrmacta.domain.ebox.port.out.EboxChannel;
import be.myrmacta.domain.ebox.port.out.EboxSendAuditTrail;
import be.myrmacta.domain.ebox.port.out.EboxSendReservations;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TODO §155 — envoi eBox : consentement, voie de secours, idempotence et journal d'audit (D-EBOX-SEND-*). */
class SendEboxMessageUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-11-20T10:15:30.123Z");
    private static final EboxSenderAccount SENDER = new EboxSenderAccount(UUID.randomUUID());
    private static final EboxRecipient ENTERPRISE = new EboxRecipient.Enterprise("0212345678");
    private static final EboxRecipient CITIZEN = new EboxRecipient.Citizen("85073003328");

    private final FakeChannel enterprise = new FakeChannel(EboxRoute.ENTERPRISE);
    private final FakeChannel citizen = new FakeChannel(EboxRoute.CITIZEN);
    private final FakeAudit audit = new FakeAudit();
    private final FakeReservations reservations = new FakeReservations();
    private final SendEboxMessageUseCaseImpl useCase = new SendEboxMessageUseCaseImpl(TENANT, List.of(enterprise, citizen), audit,
            reservations, Clock.fixed(NOW, ZoneOffset.UTC));

    private static EboxOutgoingMessage message(EboxRecipient recipient) {
        return EboxOutgoingMessage.builder(recipient, "myrmacta-commune", "decision", EboxLanguage.FR, "Votre permis")
                .document(new EboxDocument("decision.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII), true))
                .correlationId("URB-2026-0042")
                .build();
    }

    private static SendEboxCommand command(EboxRecipient recipient, String key) {
        return new SendEboxCommand(SENDER, message(recipient), key, UUID.randomUUID(), FallbackChannel.PAPER);
    }

    @Test
    void aConsentingCitizenReceivesTheMessage_andTheSendIsAudited() {
        citizen.level = Level.CONSENTED;

        EboxSendOutcome outcome = useCase.send(command(CITIZEN, "k-1"));

        assertThat(outcome.outcome()).isEqualTo(Outcome.PUBLISHED);
        assertThat(outcome.delivery()).isEqualTo(EboxDelivery.EBOX);
        assertThat(outcome.providerMessageId()).isEqualTo("CITIZEN-1");
        assertThat(outcome.replayed()).isFalse();
        assertThat(citizen.published).containsExactly("k-1");
        assertThat(enterprise.published).isEmpty();
        EboxSendAuditEntry entry = audit.entries.getFirst();
        assertThat(entry.tenantId()).isEqualTo(TENANT);
        assertThat(entry.recordedAt()).isEqualTo(NOW);
        assertThat(entry.route()).isEqualTo(EboxRoute.CITIZEN);
        assertThat(entry.consent().level()).isEqualTo(Level.CONSENTED);
        assertThat(entry.outcome()).isEqualTo(Outcome.PUBLISHED);
        assertThat(entry.publication().providerMessageId()).isEqualTo("CITIZEN-1");
        assertThat(entry.documents()).singleElement().satisfies(d -> {
            assertThat(d.mainContent()).isTrue();
            assertThat(d.size()).isEqualTo(8);
            assertThat(d.sha256()).hasSize(64);
        });
    }

    @Test
    void withoutConsentNothingIsPublished_andTheFallbackChannelIsReturned() {
        citizen.level = Level.NO_CONSENT;

        EboxSendOutcome outcome = useCase.send(command(CITIZEN, "k-2"));

        assertThat(outcome.outcome()).isEqualTo(Outcome.FALLBACK);
        assertThat(outcome.delivery()).isEqualTo(EboxDelivery.FALLBACK);
        assertThat(outcome.fallbackChannel()).isEqualTo(FallbackChannel.PAPER);
        assertThat(citizen.published).isEmpty();
        assertThat(audit.entries).singleElement().satisfies(e -> {
            assertThat(e.outcome()).isEqualTo(Outcome.FALLBACK);
            assertThat(e.publication()).isNull();
            assertThat(e.fallbackChannel()).isEqualTo(FallbackChannel.PAPER);
        });
    }

    @Test
    void anEnterpriseWithoutExclusiveEboxGetsTheEboxAndAFallbackCopy() {
        enterprise.level = Level.EBOX_NOT_EXCLUSIVE;

        EboxSendOutcome outcome = useCase.send(command(ENTERPRISE, "k-3"));

        assertThat(outcome.outcome()).isEqualTo(Outcome.PUBLISHED);
        assertThat(outcome.delivery()).isEqualTo(EboxDelivery.EBOX_AND_FALLBACK);
        assertThat(outcome.fallbackChannel()).isEqualTo(FallbackChannel.PAPER);
        assertThat(enterprise.published).containsExactly("k-3");
    }

    @Test
    void aSendAlreadyHandledIsReplayedFromTheAudit_withoutCallingTheChannel() {
        enterprise.level = Level.EXCLUSIVE_EBOX;
        useCase.send(command(ENTERPRISE, "k-4"));

        EboxSendOutcome again = useCase.send(command(ENTERPRISE, "k-4"));

        assertThat(again.replayed()).isTrue();
        assertThat(again.outcome()).isEqualTo(Outcome.PUBLISHED);
        assertThat(again.providerMessageId()).isEqualTo("ENTERPRISE-1");
        assertThat(enterprise.published).containsExactly("k-4");
        assertThat(enterprise.consentChecks).isEqualTo(1);
        assertThat(audit.entries).hasSize(1);
    }

    @Test
    void theSameKeyForAnotherRecipientIsRefused() {
        enterprise.level = Level.EXCLUSIVE_EBOX;
        useCase.send(command(ENTERPRISE, "k-5"));

        assertThatThrownBy(() -> useCase.send(command(new EboxRecipient.Enterprise("0312345678"), "k-5")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("idempotency");
    }

    @Test
    void anUncertainPublicationIsAudited_thenRetriedWithTheSameKey() {
        enterprise.level = Level.EXCLUSIVE_EBOX;
        enterprise.next = EboxPublication.Status.UNCERTAIN;

        assertThatThrownBy(() -> useCase.send(command(ENTERPRISE, "k-6"))).isInstanceOf(EboxSendUncertainException.class);
        assertThat(audit.entries).singleElement().extracting(EboxSendAuditEntry::outcome).isEqualTo(Outcome.UNCERTAIN);

        enterprise.next = EboxPublication.Status.DUPLICATE;
        EboxSendOutcome retried = useCase.send(command(ENTERPRISE, "k-6"));

        assertThat(retried.outcome()).isEqualTo(Outcome.DUPLICATE);
        assertThat(retried.replayed()).isFalse();
        assertThat(enterprise.published).containsExactly("k-6", "k-6");
        assertThat(audit.entries).extracting(EboxSendAuditEntry::outcome).containsExactly(Outcome.UNCERTAIN, Outcome.DUPLICATE);
    }

    @Test
    void aRejectedPublicationIsNotAudited() {
        enterprise.level = Level.EXCLUSIVE_EBOX;
        enterprise.reject = true;

        assertThatThrownBy(() -> useCase.send(command(ENTERPRISE, "k-7"))).isInstanceOf(EboxPublicationRejectedException.class);
        assertThat(audit.entries).isEmpty();
    }

    @Test
    void aFailedConsentCheckStopsTheSend() {
        enterprise.consentFailure = new IllegalStateException("federation down");

        assertThatThrownBy(() -> useCase.send(command(ENTERPRISE, "k-8"))).hasMessageContaining("federation down");
        assertThat(enterprise.published).isEmpty();
        assertThat(audit.entries).isEmpty();
    }

    @Test
    void theCommandIsValidated() {
        assertThatThrownBy(() -> new SendEboxCommand(SENDER, message(ENTERPRISE), " ", null, FallbackChannel.PAPER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SendEboxCommand(SENDER, message(ENTERPRISE), "x".repeat(101), null, FallbackChannel.PAPER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SendEboxMessageUseCaseImpl(TENANT, List.of(enterprise, new FakeChannel(EboxRoute.ENTERPRISE)),
                audit, reservations, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRouteWithoutChannelIsRefused() {
        SendEboxMessageUseCaseImpl enterpriseOnly =
                new SendEboxMessageUseCaseImpl(TENANT, List.of(enterprise), audit, reservations, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> enterpriseOnly.send(command(CITIZEN, "k-9"))).isInstanceOf(IllegalStateException.class);
    }

    private static SendEboxCommand registered(String key) {
        EboxOutgoingMessage message = EboxOutgoingMessage.builder(ENTERPRISE, "myrmacta-commune", "decision", EboxLanguage.FR,
                "Votre permis").document(new EboxDocument("decision.pdf", "application/pdf", new byte[] {1, 2}, true))
                .registered(true).build();
        return new SendEboxCommand(SENDER, message, key, null, FallbackChannel.PAPER);
    }

    @Test
    void aWithdrawnConsentAfterAnUncertainAttemptLeadsToTheFallback_notToTheEbox() {
        citizen.level = Level.CONSENTED;
        citizen.next = EboxPublication.Status.UNCERTAIN;
        assertThatThrownBy(() -> useCase.send(command(CITIZEN, "k-m3"))).isInstanceOf(EboxSendUncertainException.class);

        citizen.level = Level.NO_CONSENT;
        EboxSendOutcome retried = useCase.send(command(CITIZEN, "k-m3"));

        assertThat(retried.outcome()).isEqualTo(Outcome.FALLBACK);
        assertThat(retried.delivery()).isEqualTo(EboxDelivery.FALLBACK);
        assertThat(citizen.published).containsExactly("k-m3");
        assertThat(audit.entries).extracting(EboxSendAuditEntry::outcome).containsExactly(Outcome.UNCERTAIN, Outcome.FALLBACK);
    }

    @Test
    void aRegisteredMailIsNeverResentAfterAnUncertainAttempt_itsProofIsLookedUp() {
        enterprise.level = Level.EXCLUSIVE_EBOX;
        enterprise.next = EboxPublication.Status.UNCERTAIN;
        assertThatThrownBy(() -> useCase.send(registered("k-rm"))).isInstanceOf(EboxSendUncertainException.class);

        assertThatThrownBy(() -> useCase.send(registered("k-rm"))).isInstanceOf(EboxRegisteredMailUnresolvedException.class);
        assertThat(enterprise.published).containsExactly("k-rm");

        enterprise.proofs.put("k-rm", "rm-7");
        EboxSendOutcome found = useCase.send(registered("k-rm"));

        assertThat(found.outcome()).isEqualTo(Outcome.DUPLICATE);
        assertThat(found.providerMessageId()).isEqualTo("rm-7");
        assertThat(found.proof().providerMessageId()).isEqualTo("rm-7");
        assertThat(enterprise.published).containsExactly("k-rm");
        assertThat(audit.entries).extracting(EboxSendAuditEntry::outcome).containsExactly(Outcome.UNCERTAIN, Outcome.DUPLICATE);
    }

    @Test
    void aConcurrentRegisteredAttemptDoesNotSendWhileTheKeyIsReserved() {
        enterprise.level = Level.EXCLUSIVE_EBOX;
        reservations.reserve(TENANT, "k-busy", NOW);

        assertThatThrownBy(() -> useCase.send(registered("k-busy"))).isInstanceOf(EboxRegisteredMailUnresolvedException.class);
        assertThat(enterprise.published).isEmpty();
        assertThat(audit.entries).isEmpty();
    }

    @Test
    void aRefusedRegisteredMailReleasesItsKey() {
        enterprise.level = Level.EXCLUSIVE_EBOX;
        enterprise.reject = true;
        assertThatThrownBy(() -> useCase.send(registered("k-refused"))).isInstanceOf(EboxPublicationRejectedException.class);
        assertThat(reservations.keys).isEmpty();

        enterprise.reject = false;
        assertThat(useCase.send(registered("k-refused")).outcome()).isEqualTo(Outcome.PUBLISHED);
        assertThat(reservations.keys).containsExactly(TENANT + "/k-refused");
    }

    @Test
    void theReplayOfAPublishedRegisteredMailReadsItsProofBack() {
        enterprise.level = Level.EXCLUSIVE_EBOX;
        EboxSendOutcome first = useCase.send(registered("k-proof"));
        enterprise.proofs.put("k-proof", first.providerMessageId());

        EboxSendOutcome again = useCase.send(registered("k-proof"));

        assertThat(again.replayed()).isTrue();
        assertThat(again.proof()).isNotNull();
        assertThat(again.proof().providerMessageId()).isEqualTo(first.providerMessageId());
        assertThat(enterprise.proofLookups).containsExactly("k-proof/" + first.providerMessageId());

        enterprise.proofs.clear();
        assertThatThrownBy(() -> useCase.send(registered("k-proof"))).hasMessageContaining("could not be read back");
    }

    private static final class FakeReservations implements EboxSendReservations {
        final java.util.Set<String> keys = new java.util.LinkedHashSet<>();

        @Override
        public boolean reserve(String tenantId, String idempotencyKey, Instant at) {
            return keys.add(tenantId + "/" + idempotencyKey);
        }

        @Override
        public void release(String tenantId, String idempotencyKey) {
            keys.remove(tenantId + "/" + idempotencyKey);
        }
    }

    private static final class FakeChannel implements EboxChannel {
        private final EboxRoute route;
        Level level;
        RuntimeException consentFailure;
        EboxPublication.Status next = EboxPublication.Status.PUBLISHED;
        boolean reject;
        int consentChecks;
        final List<String> published = new ArrayList<>();
        /** Preuves connues : clé d'idempotence vers identifiant rendu. */
        final java.util.Map<String, String> proofs = new java.util.HashMap<>();
        final List<String> proofLookups = new ArrayList<>();

        FakeChannel(EboxRoute route) {
            this.route = route;
        }

        @Override
        public EboxRoute route() {
            return route;
        }

        @Override
        public EboxConsent consent(String tenantId, EboxSenderAccount sender, EboxRecipient recipient) {
            consentChecks++;
            if (consentFailure != null) {
                throw consentFailure;
            }
            return new EboxConsent(level, NOW.minusSeconds(1), "fake");
        }

        @Override
        public EboxPublication publish(String tenantId, EboxSenderAccount sender, EboxOutgoingMessage message, String idempotencyKey) {
            published.add(idempotencyKey);
            if (reject) {
                throw new EboxPublicationRejectedException(400);
            }
            EboxPublication.Trace trace = new EboxPublication.Trace("POST", "https://ebox.example/ebox/messages", "client", null,
                    "0".repeat(64));
            return switch (next) {
                case PUBLISHED -> new EboxPublication(next, route + "-" + published.size(), 201, trace, null);
                case DUPLICATE -> new EboxPublication(next, null, 409, trace, null);
                case UNCERTAIN -> new EboxPublication(next, null, EboxPublication.TIMEOUT, trace, null);
            };
        }

        @Override
        public Optional<EboxPublication> registeredProof(String tenantId, EboxSenderAccount sender, String idempotencyKey,
                String providerMessageId) {
            proofLookups.add(idempotencyKey + "/" + providerMessageId);
            String id = proofs.get(idempotencyKey);
            if (id == null || (providerMessageId != null && !providerMessageId.equals(id))) {
                return Optional.empty();
            }
            EboxPublication.Trace trace = new EboxPublication.Trace("GET", "https://ebox.example/proofsOfDispatch", "client", null, null);
            return Optional.of(new EboxPublication(EboxPublication.Status.DUPLICATE, id, 200, trace,
                    new EboxProofOfDispatch(idempotencyKey, id, "f".repeat(64), "{}")));
        }
    }

    private static final class FakeAudit implements EboxSendAuditTrail {
        final List<EboxSendAuditEntry> entries = new ArrayList<>();

        @Override
        public void append(EboxSendAuditEntry entry) {
            entries.add(entry);
        }

        @Override
        public Optional<EboxSendAuditEntry.Summary> last(String tenantId, String idempotencyKey, EboxRecipient recipient) {
            return entries.stream()
                    .filter(e -> e.tenantId().equals(tenantId) && e.idempotencyKey().equals(idempotencyKey))
                    .reduce((a, b) -> b)
                    .map(e -> e.summary(recipient));
        }
    }
}
