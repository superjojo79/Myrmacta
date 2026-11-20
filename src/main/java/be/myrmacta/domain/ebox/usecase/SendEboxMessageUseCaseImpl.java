package be.myrmacta.domain.ebox.usecase;

import be.myrmacta.domain.ebox.model.EboxConsent;
import be.myrmacta.domain.ebox.model.EboxDelivery;
import be.myrmacta.domain.ebox.model.EboxOutgoingMessage;
import be.myrmacta.domain.ebox.model.EboxPublication;
import be.myrmacta.domain.ebox.model.EboxRoute;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry.DocumentTrace;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry.Outcome;
import be.myrmacta.domain.ebox.model.EboxSendOutcome;
import be.myrmacta.domain.ebox.model.EboxSendUncertainException;
import be.myrmacta.domain.ebox.model.SendEboxCommand;
import be.myrmacta.domain.ebox.port.in.SendEboxMessageUseCase;
import be.myrmacta.domain.ebox.port.out.EboxChannel;
import be.myrmacta.domain.ebox.port.out.EboxSendAuditTrail;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * TODO §155 — envoi eBox (issue #13).
 *
 * <ol>
 *   <li>Une clé d'idempotence déjà traitée rend l'issue du journal sans rien rappeler, sauf une issue incertaine, rejouée avec
 *       la même clé (D-EBOX-SEND-IDEMPOTENCE). La même clé pour un autre destinataire est refusée.</li>
 *   <li>Le consentement (citoyen) ou la préférence eBox (entreprise) est constaté avant chaque envoi ; un service muet arrête
 *       l'envoi (D-EBOX-SEND-CONSENT).</li>
 *   <li>La voie retenue publie, publie avec une copie par la voie de secours, ou ne fait que désigner la voie de secours.</li>
 *   <li>Le journal d'audit reçoit une ligne pour une publication acceptée, un doublon, une issue incertaine ou une voie de secours
 *       seule ; un refus certain n'en laisse aucune (ATREQ04, D-EBOX-SEND-AUDIT).</li>
 * </ol>
 */
public final class SendEboxMessageUseCaseImpl implements SendEboxMessageUseCase {

    private final String tenantId;
    private final Map<EboxRoute, EboxChannel> channels = new EnumMap<>(EboxRoute.class);
    private final EboxSendAuditTrail audit;
    private final Clock clock;

    public SendEboxMessageUseCaseImpl(String tenantId, List<EboxChannel> channels, EboxSendAuditTrail audit, Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
        for (EboxChannel channel : channels) {
            if (this.channels.putIfAbsent(channel.route(), channel) != null) {
                throw new IllegalArgumentException("two eBox channels serve the route " + channel.route());
            }
        }
    }

    @Override
    public EboxSendOutcome send(SendEboxCommand command) {
        EboxOutgoingMessage message = command.message();
        EboxChannel channel = channels.get(message.route());
        if (channel == null) {
            throw new IllegalStateException("no eBox channel is available for the route " + message.route());
        }
        Optional<EboxSendAuditEntry.Summary> previous = audit.last(tenantId, command.idempotencyKey(), message.recipient());
        if (previous.isPresent()) {
            EboxSendAuditEntry.Summary summary = previous.get();
            if (!summary.sameRecipient()) {
                throw new IllegalArgumentException("this idempotency key was already used for another recipient");
            }
            if (summary.outcome() != Outcome.UNCERTAIN) {
                return new EboxSendOutcome(summary.outcome(), summary.delivery(), summary.fallbackChannel(),
                        summary.providerMessageId(), null, true);
            }
        }
        EboxConsent consent = channel.consent(tenantId, command.sender(), message.recipient());
        EboxDelivery delivery = previous.map(EboxSendAuditEntry.Summary::delivery)
                .orElseGet(() -> EboxDelivery.decide(message.route(), consent.level()));
        if (!delivery.publishes()) {
            audit.append(entry(command, consent, delivery, Outcome.FALLBACK, null));
            return new EboxSendOutcome(Outcome.FALLBACK, delivery, command.fallbackChannel(), null, null, false);
        }
        EboxPublication publication = channel.publish(tenantId, command.sender(), message, command.idempotencyKey());
        Outcome outcome = Outcome.valueOf(publication.status().name());
        audit.append(entry(command, consent, delivery, outcome, publication));
        if (outcome == Outcome.UNCERTAIN) {
            throw new EboxSendUncertainException(command.idempotencyKey());
        }
        return new EboxSendOutcome(outcome, delivery, command.fallbackChannel(), publication.providerMessageId(), publication.proof(),
                false);
    }

    private EboxSendAuditEntry entry(SendEboxCommand command, EboxConsent consent, EboxDelivery delivery, Outcome outcome,
            EboxPublication publication) {
        EboxOutgoingMessage message = command.message();
        return new EboxSendAuditEntry(UUID.randomUUID(), tenantId, clock.instant(), command.sender().connectorInstanceId(),
                message.route(), message.recipient(), command.idempotencyKey(), command.dossierId(), message.correlationId(),
                message.senderApplicationId(), message.messageTypeId(), message.language(), message.registered(),
                message.documents().stream().map(DocumentTrace::of).toList(), consent, delivery, command.fallbackChannel(), outcome,
                publication);
    }
}
