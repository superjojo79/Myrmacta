package be.myrmacta.infrastructure.persistence.ebox;

import be.myrmacta.domain.ebox.model.EboxDelivery;
import be.myrmacta.domain.ebox.model.EboxPublication;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry;
import be.myrmacta.domain.ebox.model.FallbackChannel;
import be.myrmacta.domain.ebox.port.out.EboxSendAuditTrail;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * TODO §155 (D-EBOX-SEND-AUDIT) et §122 — journal d'audit des envois eBox, en JPA. Chaque ajout est validé dans sa propre
 * transaction ({@code REQUIRES_NEW}) : l'appel à l'eBox a eu lieu, sa trace reste même si la transaction du process recule.
 */
@Component
class JpaEboxSendAuditTrailAdapter implements EboxSendAuditTrail {

    private final SpringDataEboxSendAuditRepository repository;
    private final EboxRecipientReference references;

    JpaEboxSendAuditTrailAdapter(SpringDataEboxSendAuditRepository repository, EboxRecipientReference references) {
        this.repository = repository;
        this.references = references;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(EboxSendAuditEntry entry) {
        EboxRecipientReference.Reference recipient = references.of(entry.tenantId(), entry.recipient());
        EboxPublication publication = entry.publication();
        EboxPublication.Trace trace = publication == null ? null : publication.trace();
        repository.save(new EboxSendAuditJpaEntity(entry.id(), entry.tenantId(), entry.recordedAt().truncatedTo(ChronoUnit.MICROS),
                entry.senderInstanceId(), entry.route().name(), recipient.value(), recipient.kind(), entry.idempotencyKey(),
                entry.dossierId(), entry.correlationId(), entry.senderApplicationId(), entry.messageTypeId(), entry.language().code(),
                entry.registered(), documents(entry.documents()), entry.consent().level().name(),
                entry.consent().checkedAt().truncatedTo(ChronoUnit.MICROS), entry.consent().source(), entry.delivery().name(),
                entry.fallbackChannel().name(), entry.outcome().name(), trace == null ? null : trace.method(),
                trace == null ? null : trace.url(), publication == null ? null : publication.responseCode(),
                trace == null ? null : trace.clientId(), trace == null ? null : trace.onBehalfOf(),
                trace == null ? null : trace.tokenHash(), publication == null ? null : publication.providerMessageId(),
                publication == null || publication.proof() == null ? null : publication.proof().signatureSha256()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EboxSendAuditEntry.Summary> last(String tenantId, String idempotencyKey, EboxRecipient recipient) {
        String expected = references.of(tenantId, recipient).value();
        return repository.findByTenantIdAndIdempotencyKeyOrderByRecordedAtDesc(tenantId, idempotencyKey, Limit.of(1)).stream()
                .findFirst()
                .map(row -> new EboxSendAuditEntry.Summary(EboxSendAuditEntry.Outcome.valueOf(row.outcome()),
                        EboxDelivery.valueOf(row.delivery()), FallbackChannel.valueOf(row.fallbackChannel()), row.providerMessageId(),
                        expected.equals(row.recipientRef())));
    }

    private static List<Map<String, Object>> documents(List<EboxSendAuditEntry.DocumentTrace> documents) {
        return documents.stream().map(document -> {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("size", document.size());
            json.put("sha256", document.sha256());
            json.put("mainContent", document.mainContent());
            return json;
        }).toList();
    }
}
