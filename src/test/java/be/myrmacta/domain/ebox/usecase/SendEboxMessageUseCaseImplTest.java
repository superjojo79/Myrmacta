package be.myrmacta.domain.ebox.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.ebox.model.EboxConsent;
import be.myrmacta.domain.ebox.model.EboxConsent.Level;
import be.myrmacta.domain.ebox.model.EboxDelivery;
import be.myrmacta.domain.ebox.model.EboxDocument;
import be.myrmacta.domain.ebox.model.EboxLanguage;
import be.myrmacta.domain.ebox.model.EboxOutgoingMessage;
import be.myrmacta.domain.ebox.model.EboxPublication;
import be.myrmacta.domain.ebox.model.EboxPublicationRejectedException;
import be.myrmacta.domain.ebox.model.EboxRecipient;
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
    private final SendEboxMessageUseCaseImpl useCase =
            new SendEboxMessageUseCaseImpl(TENANT, List.of(enterprise, citizen), audit, Clock.fixed(NOW, ZoneOffset.UTC));

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
                audit, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRouteWithoutChannelIsRefused() {
        SendEboxMessageUseCaseImpl enterpriseOnly =
                new SendEboxMessageUseCaseImpl(TENANT, List.of(enterprise), audit, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> enterpriseOnly.send(command(CITIZEN, "k-9"))).isInstanceOf(IllegalStateException.class);
    }

    private static final class FakeChannel implements EboxChannel {
        private final EboxRoute route;
        Level level;
        RuntimeException consentFailure;
        EboxPublication.Status next = EboxPublication.Status.PUBLISHED;
        boolean reject;
        int consentChecks;
        final List<String> published = new ArrayList<>();

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
