package be.myrmacta.infrastructure.ebox;

import be.myrmacta.domain.ebox.model.EboxConsent;
import be.myrmacta.domain.ebox.model.EboxOutgoingMessage;
import be.myrmacta.domain.ebox.model.EboxPublication;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxRoute;
import be.myrmacta.domain.ebox.model.EboxSenderAccount;
import be.myrmacta.domain.ebox.port.out.EboxChannel;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * TODO §155 (D-EBOX-SEND-CITIZEN-STUB) — voie citoyen en attente de l'API de l'eBox citoyen (BOSA, spec sous NDA, OAuth sans
 * certificat X.509) : une implémentation générique et configurable de {@link EboxChannel}, à remplacer par le vrai adaptateur.
 *
 * <p>Sûre par défaut : l'état rendu est {@code NO_CONSENT} ({@code app.ebox.citizen-stub.consent}), donc rien n'est jamais
 * « publié » et le process suit la voie de secours. Réglé à {@code CONSENTED} (démo, tests), une publication est simulée : la
 * trace porte la méthode {@code STUB} et l'URL {@code stub://ebox-citizen}, le journal d'audit ne la confond pas avec un envoi réel.
 */
@Component
public class StubEboxCitizenChannel implements EboxChannel {

    static final String SOURCE = "citizen-stub";
    static final String STUB_URL = "stub://ebox-citizen/messages";
    private static final Logger log = LoggerFactory.getLogger(StubEboxCitizenChannel.class);

    private final EboxConsent.Level level;
    private final Clock clock;

    @Autowired
    public StubEboxCitizenChannel(@Value("${app.ebox.citizen-stub.consent:NO_CONSENT}") String level) {
        this(level, Clock.systemUTC());
    }

    StubEboxCitizenChannel(String level, Clock clock) {
        EboxConsent.Level parsed = EboxConsent.Level.valueOf(level.strip().toUpperCase(Locale.ROOT));
        if (!parsed.appliesTo(EboxRoute.CITIZEN)) {
            throw new IllegalArgumentException("app.ebox.citizen-stub.consent must be CONSENTED, NO_CONSENT or NO_EBOX");
        }
        this.level = parsed;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public EboxRoute route() {
        return EboxRoute.CITIZEN;
    }

    @Override
    public EboxConsent consent(String tenantId, EboxSenderAccount sender, EboxRecipient recipient) {
        citizen(recipient);
        return new EboxConsent(level, clock.instant(), SOURCE);
    }

    @Override
    public EboxPublication publish(String tenantId, EboxSenderAccount sender, EboxOutgoingMessage message, String idempotencyKey) {
        citizen(message.recipient());
        log.warn("eBox citizen channel is a stub: message {} was not sent to any real eBox", idempotencyKey);
        return new EboxPublication(EboxPublication.Status.PUBLISHED, "stub-" + UUID.randomUUID(), 201,
                new EboxPublication.Trace("STUB", STUB_URL, null, null, null), null);
    }

    /** Le stub ne dépose aucun recommandé : il n'a aucune preuve à rendre. */
    @Override
    public Optional<EboxPublication> registeredProof(String tenantId, EboxSenderAccount sender, String idempotencyKey,
            String providerMessageId) {
        return Optional.empty();
    }

    private static void citizen(EboxRecipient recipient) {
        if (!(recipient instanceof EboxRecipient.Citizen)) {
            throw new IllegalArgumentException("the citizen eBox channel only serves citizen recipients");
        }
    }
}
