package com.flowforge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Issue #183 (D-NOTIF-ATTACH-MAX) — plafond de la pièce jointe d'un e-mail « Notifier », vérifié à l'émission et revérifié à
 * l'envoi. Défaut 7 Mio : environ 9,8 Mo une fois encodé en base64 (+33 %), sous la limite par défaut de Postfix
 * ({@code message_size_limit} = 10 240 000 octets ; revue adversariale L1). Une valeur nulle ou négative est une erreur de
 * configuration, refusée au démarrage plutôt que remplacée en silence (revue I4).
 */
@ConfigurationProperties(prefix = "app.notification.email")
public record NotificationEmailProperties(Long attachmentMaxBytes) {

    public static final long DEFAULT_ATTACHMENT_MAX_BYTES = 7L * 1024 * 1024;

    public NotificationEmailProperties {
        if (attachmentMaxBytes == null) {
            attachmentMaxBytes = DEFAULT_ATTACHMENT_MAX_BYTES;
        } else if (attachmentMaxBytes <= 0) {
            throw new IllegalArgumentException("app.notification.email.attachment-max-bytes must be positive");
        }
    }
}
