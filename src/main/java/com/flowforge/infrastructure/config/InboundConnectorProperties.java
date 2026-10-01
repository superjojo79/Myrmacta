package com.flowforge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * TODO §87 (D3c, D3d) — bornes des connecteurs d'entrée par interrogation (e-mail entrant, Azure Service Bus). Tout est borné :
 * un message ou une pièce jointe hors limite est écarté, jamais lu en entier.
 *
 * @param maxMessagesPerPoll messages traités par interrogation d'une instance
 * @param maxMessageBytes taille maximale d'un message e-mail (au-delà : refusé sans analyse)
 * @param maxBodyChars texte conservé du corps d'un message
 * @param maxAttachmentBytes taille maximale d'une pièce jointe
 * @param minPollSeconds intervalle minimal d'interrogation d'une instance
 * @param maxBusMessageBytes taille maximale d'un message de bus lu (au-delà : refusé, jamais bufferisé)
 */
@ConfigurationProperties(prefix = "app.inbound")
public record InboundConnectorProperties(
        @DefaultValue("20") int maxMessagesPerPoll,
        @DefaultValue("26214400") long maxMessageBytes,
        @DefaultValue("20000") int maxBodyChars,
        @DefaultValue("10485760") long maxAttachmentBytes,
        @DefaultValue("30") int minPollSeconds,
        @DefaultValue("1048576") long maxBusMessageBytes) {

    public InboundConnectorProperties {
        if (maxMessagesPerPoll < 1 || maxMessagesPerPoll > 500) {
            throw new IllegalArgumentException("app.inbound.max-messages-per-poll must be between 1 and 500");
        }
        if (maxMessageBytes < 1024 || maxAttachmentBytes < 1024 || maxBodyChars < 100 || maxBusMessageBytes < 1024) {
            throw new IllegalArgumentException("app.inbound limits are too small");
        }
        if (minPollSeconds < 5) {
            throw new IllegalArgumentException("app.inbound.min-poll-seconds must be at least 5");
        }
    }

    public static InboundConnectorProperties defaults() {
        return new InboundConnectorProperties(20, 26_214_400L, 20_000, 10_485_760L, 30, 1_048_576L);
    }
}
