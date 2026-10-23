package com.flowforge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Issue #183 (D-NOTIF-ATTACH-MAX) — plafond de la pièce jointe d'un e-mail « Notifier », vérifié à l'émission et revérifié à
 * l'envoi. Défaut 10 Mio : sous la limite courante des relais SMTP une fois le contenu encodé en base64 (+33 %).
 */
@ConfigurationProperties(prefix = "app.notification.email")
public record NotificationEmailProperties(long attachmentMaxBytes) {

    public static final long DEFAULT_ATTACHMENT_MAX_BYTES = 10L * 1024 * 1024;

    public NotificationEmailProperties {
        if (attachmentMaxBytes <= 0) {
            attachmentMaxBytes = DEFAULT_ATTACHMENT_MAX_BYTES;
        }
    }
}
