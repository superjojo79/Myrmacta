package be.myrmacta.domain.notification.template.model;

import java.util.Objects;

/**
 * Gabarit ou champ refusé. Nomme la partie ({@code templateKey}, {@code subject}, {@code textBody}, {@code htmlBody}, …)
 * et la règle violée ; ne contient jamais une valeur de variable.
 */
public class NotificationTemplateValidationException extends RuntimeException {

    private final String part;

    public NotificationTemplateValidationException(String part, String rule) {
        super(Objects.requireNonNull(part, "part") + ": " + Objects.requireNonNull(rule, "rule"));
        this.part = part;
    }

    public NotificationTemplateValidationException(String part, String rule, Throwable cause) {
        super(Objects.requireNonNull(part, "part") + ": " + Objects.requireNonNull(rule, "rule"), cause);
        this.part = part;
    }

    public String part() {
        return part;
    }
}
