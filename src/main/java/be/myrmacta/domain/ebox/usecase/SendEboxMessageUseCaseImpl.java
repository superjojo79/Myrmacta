package be.myrmacta.domain.ebox.usecase;

import be.myrmacta.domain.ebox.model.EboxConsent;
import be.myrmacta.domain.ebox.model.EboxDelivery;
import be.myrmacta.domain.ebox.model.EboxOutgoingMessage;
import be.myrmacta.domain.ebox.model.EboxProofOfDispatch;
import be.myrmacta.domain.ebox.model.EboxPublication;
import be.myrmacta.domain.ebox.model.EboxRegisteredMailUnresolvedException;
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
import be.myrmacta.domain.ebox.port.out.EboxSendReservations;
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
 *   <li>Une clé d'idempotence déjà traitée rend l'issue du journal sans republier (la preuve d'un recommandé est relue), sauf
 *       une issue incertaine, rejouée avec la même clé (D-EBOX-SEND-IDEMPOTENCE). La même clé pour un autre destinataire est
 *       refusée.</li>
 *   <li>Un recommandé réserve sa clé avant l'appel (D-EBOX-SEND-REGISTERED).</li>
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
    private final EboxSendReservations reservations;
    private final Clock clock;

    public SendEboxMessageUseCaseImpl(String tenantId, List<EboxChannel> channels, EboxSendAuditTrail audit,
            EboxSendReservations reservations, Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
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
        String key = command.idempotencyKey();
        Optional<EboxSendAuditEntry.Summary> previous = audit.last(tenantId, key, message.recipient());
        if (previous.isPresent()) {
            EboxSendAuditEntry.Summary summary = previous.get();
            if (!summary.sameRecipient()) {
                throw new IllegalArgumentException("this idempotency key was already used for another recipient");
            }
            if (summary.outcome() != Outcome.UNCERTAIN) {
                return replay(command, channel, summary);
            }
        }
        // Constaté à chaque tentative, rejeu compris : un consentement retiré depuis une issue incertaine mène à la voie de secours.
        EboxConsent consent = channel.consent(tenantId, command.sender(), message.recipient());
        EboxDelivery delivery = EboxDelivery.decide(message.route(), consent.level());
        if (!delivery.publishes()) {
            audit.append(entry(command, consent, delivery, Outcome.FALLBACK, null));
            return new EboxSendOutcome(Outcome.FALLBACK, delivery, command.fallbackChannel(), null, null, false);
        }
        EboxPublication publication = message.registered()
                ? publishRegistered(command, channel)
                : channel.publish(tenantId, command.sender(), message, key);
        Outcome outcome = Outcome.valueOf(publication.status().name());
        audit.append(entry(command, consent, delivery, outcome, publication));
        if (outcome == Outcome.UNCERTAIN) {
            throw new EboxSendUncertainException(key);
        }
        return new EboxSendOutcome(outcome, delivery, command.fallbackChannel(), publication.providerMessageId(), publication.proof(),
                false);
    }

    /** Rejeu d'une clé déjà traitée : rien n'est republié ; la preuve d'un recommandé est relue (le journal n'en garde que l'empreinte). */
    private EboxSendOutcome replay(SendEboxCommand command, EboxChannel channel, EboxSendAuditEntry.Summary summary) {
        EboxProofOfDispatch proof = null;
        if (command.message().registered() && summary.providerMessageId() != null) {
            proof = channel.registeredProof(tenantId, command.sender(), command.idempotencyKey(), summary.providerMessageId())
                    .map(EboxPublication::proof)
                    .orElseThrow(() -> new IllegalStateException("the proof of dispatch of registered mail "
                            + summary.providerMessageId() + " could not be read back"));
        }
        return new EboxSendOutcome(summary.outcome(), summary.delivery(), summary.fallbackChannel(), summary.providerMessageId(),
                proof, true);
    }

    /**
     * TODO §155 (D-EBOX-SEND-REGISTERED) — une seule tentative par clé dépose : les autres (reprise après une issue incertaine,
     * tentative concurrente) retrouvent la preuve de dépôt ou échouent sans rien renvoyer. Une exception de la voie signifie que
     * rien n'a été déposé : la clé est libérée.
     */
    private EboxPublication publishRegistered(SendEboxCommand command, EboxChannel channel) {
        String key = command.idempotencyKey();
        if (!reservations.reserve(tenantId, key, clock.instant())) {
            return channel.registeredProof(tenantId, command.sender(), key, null)
                    .orElseThrow(() -> new EboxRegisteredMailUnresolvedException(key));
        }
        try {
            return channel.publish(tenantId, command.sender(), command.message(), key);
        } catch (RuntimeException notPublished) {
            reservations.release(tenantId, key);
            throw notPublished;
        }
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
