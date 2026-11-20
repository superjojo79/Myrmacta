package be.myrmacta.infrastructure.persistence.ebox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.ebox.model.EboxConsent;
import be.myrmacta.domain.ebox.model.EboxDelivery;
import be.myrmacta.domain.ebox.model.EboxDocument;
import be.myrmacta.domain.ebox.model.EboxLanguage;
import be.myrmacta.domain.ebox.model.EboxOutgoingMessage;
import be.myrmacta.domain.ebox.model.EboxSendOutcome;
import be.myrmacta.domain.ebox.model.EboxSenderAccount;
import be.myrmacta.domain.ebox.model.SendEboxCommand;
import be.myrmacta.domain.ebox.port.in.SendEboxMessageUseCase;
import be.myrmacta.domain.ebox.model.EboxProofOfDispatch;
import be.myrmacta.domain.ebox.model.EboxPublication;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxRoute;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry.DocumentTrace;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry.Outcome;
import be.myrmacta.domain.ebox.model.FallbackChannel;
import be.myrmacta.domain.ebox.port.out.EboxSendAuditTrail;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** TODO §155 (D-EBOX-SEND-AUDIT) — journal d'audit des envois eBox sur PostgreSQL (changeset 054) : ajout seul, NISS jamais en clair. */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class EboxSendAuditPersistenceIT {

    private static final String TENANT = "tenant_dev";
    private static final String SSIN = "85073003328";

    @Autowired private EboxSendAuditTrail audit;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SendEboxMessageUseCase sendEbox;

    @Test
    void theWiredUseCaseSendsACitizenThroughTheStub_whichFallsBackByDefault() {
        String key = "k-" + UUID.randomUUID();
        EboxOutgoingMessage message = EboxOutgoingMessage.builder(new EboxRecipient.Citizen(SSIN), "myrmacta-commune", "decision",
                EboxLanguage.FR, "Votre permis").document(new EboxDocument("decision.pdf", "application/pdf", new byte[] {37, 80}, true))
                .build();

        EboxSendOutcome outcome = sendEbox.send(new SendEboxCommand(new EboxSenderAccount(UUID.randomUUID()), message, key, null,
                FallbackChannel.EMAIL));

        assertThat(outcome.outcome()).isEqualTo(Outcome.FALLBACK);
        assertThat(outcome.fallbackChannel()).isEqualTo(FallbackChannel.EMAIL);
        assertThat(jdbc.queryForMap("select consent_source, consent_level, outcome from ebox_send_audit where idempotency_key = ?", key))
                .containsEntry("consent_source", "citizen-stub").containsEntry("consent_level", "NO_CONSENT")
                .containsEntry("outcome", "FALLBACK");
    }

    @Test
    void anEntryIsAppended_andTheLastOneOfAKeyIsSummarized() {
        String key = "k-" + UUID.randomUUID();
        EboxRecipient enterprise = new EboxRecipient.Enterprise("0212345678");
        audit.append(entry(enterprise, key, Outcome.UNCERTAIN, Instant.parse("2026-11-20T10:00:00.001Z")));
        audit.append(entry(enterprise, key, Outcome.PUBLISHED, Instant.parse("2026-11-20T10:05:00.002Z")));

        EboxSendAuditEntry.Summary last = audit.last(TENANT, key, enterprise).orElseThrow();

        assertThat(last.outcome()).isEqualTo(Outcome.PUBLISHED);
        assertThat(last.providerMessageId()).isEqualTo("msg-1");
        assertThat(last.delivery()).isEqualTo(EboxDelivery.EBOX);
        assertThat(last.sameRecipient()).isTrue();
        assertThat(audit.last(TENANT, key, new EboxRecipient.Enterprise("0312345678")).orElseThrow().sameRecipient()).isFalse();
        assertThat(audit.last("other_tenant", key, enterprise)).isEmpty();

        Map<String, Object> row = jdbc.queryForMap(
                "select * from ebox_send_audit where idempotency_key = ? and outcome = 'PUBLISHED'", key);
        assertThat(row).containsEntry("recipient_ref", "0212345678").containsEntry("recipient_ref_kind", "BCE")
                .containsEntry("request_method", "POST").containsEntry("response_code", 201)
                .containsEntry("on_behalf_of", "0207654321").containsEntry("proof_signature_sha256", "b".repeat(64));
        assertThat(String.valueOf(row.get("documents"))).contains("\"sha256\": \"" + "a".repeat(64) + "\"");
    }

    @Test
    void aCitizenIsNeverWrittenInClear() {
        String key = "k-" + UUID.randomUUID();
        EboxRecipient citizen = new EboxRecipient.Citizen(SSIN);
        audit.append(entry(citizen, key, Outcome.FALLBACK, Instant.now()));

        Map<String, Object> row = jdbc.queryForMap("select * from ebox_send_audit where idempotency_key = ?", key);

        assertThat(row.values()).noneMatch(value -> String.valueOf(value).contains(SSIN));
        assertThat(row).containsEntry("recipient_ref_kind", "SSIN_HMAC_V1");
        assertThat((String) row.get("recipient_ref")).matches("[0-9a-f]{64}");
        assertThat(audit.last(TENANT, key, citizen).orElseThrow().sameRecipient()).isTrue();
        assertThat(audit.last(TENANT, key, new EboxRecipient.Citizen("85073003427")).orElseThrow().sameRecipient()).isFalse();
    }

    @Test
    void theDatabaseRefusesUpdateDeleteAndTruncate() {
        String key = "k-" + UUID.randomUUID();
        audit.append(entry(new EboxRecipient.Enterprise("0212345678"), key, Outcome.PUBLISHED, Instant.now()));

        assertThatThrownBy(() -> jdbc.update("update ebox_send_audit set outcome = 'FALLBACK' where idempotency_key = ?", key))
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from ebox_send_audit where idempotency_key = ?", key))
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("truncate ebox_send_audit")).rootCause().hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("select count(*) from ebox_send_audit where idempotency_key = ?", Integer.class, key))
                .isEqualTo(1);
    }

    private static EboxSendAuditEntry entry(EboxRecipient recipient, String key, Outcome outcome, Instant at) {
        EboxRoute route = recipient.route();
        EboxConsent consent = new EboxConsent(route == EboxRoute.CITIZEN ? EboxConsent.Level.NO_CONSENT
                : EboxConsent.Level.EXCLUSIVE_EBOX, at, "federation-1.4");
        EboxDelivery delivery = outcome == Outcome.FALLBACK ? EboxDelivery.FALLBACK : EboxDelivery.EBOX;
        EboxPublication publication = null;
        if (outcome != Outcome.FALLBACK) {
            EboxPublication.Trace trace = new EboxPublication.Trace("POST", "https://services.socialsecurity.be/REST/ebox/messages",
                    "client-1", "0207654321", "c".repeat(64));
            publication = switch (outcome) {
                case PUBLISHED -> new EboxPublication(EboxPublication.Status.PUBLISHED, "msg-1", 201, trace,
                        new EboxProofOfDispatch(key, "msg-1", "b".repeat(64), "{}"));
                case UNCERTAIN -> new EboxPublication(EboxPublication.Status.UNCERTAIN, null, EboxPublication.TIMEOUT, trace, null);
                default -> new EboxPublication(EboxPublication.Status.DUPLICATE, null, 409, trace, null);
            };
        }
        return new EboxSendAuditEntry(UUID.randomUUID(), TENANT, at, UUID.randomUUID(), route, recipient, key, UUID.randomUUID(),
                "URB-2026-0042", "myrmacta-commune", "decision", EboxLanguage.FR, false,
                List.of(new DocumentTrace(1234, "a".repeat(64), true)), consent, delivery, FallbackChannel.PAPER, outcome,
                publication);
    }
}
