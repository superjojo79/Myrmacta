package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ServiceConnector;
import be.myrmacta.domain.notification.port.out.EmailPort;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Ad hoc email send from a {@code serviceTask}, distinct from the structured/templated
 * notification pipeline — content is computed directly in the BPMN process. Reuses the
 * already-configured {@link EmailPort} (one SMTP config per tenant instance), no per-instance
 * secret.
 */
@Component
public class SmtpConnector implements ConnectorDefinition, ServiceConnector {

    private final EmailPort emailPort;

    public SmtpConnector(EmailPort emailPort) {
        this.emailPort = Objects.requireNonNull(emailPort, "emailPort");
    }

    @Override
    public String getType() {
        return "smtp";
    }

    @Override
    public String getLabel() {
        return "SMTP (ad hoc email send)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.SERVICE;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of();
    }

    @Override
    public void validate(Map<String, Object> config) {
        // No per-instance config — reuses the application-wide EmailPort/JavaMailSender config.
    }

    @Override
    public Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> inputParams) {
        Objects.requireNonNull(inputParams, "inputParams");
        String to = requireString(inputParams, "to");
        if (!isSingleEmailAddress(to)) {
            throw new IllegalArgumentException("to must be a single valid email address");
        }
        String subject = requireString(inputParams, "subject");
        if (containsHeaderBreak(subject) || containsHeaderBreak(to)) {
            throw new IllegalArgumentException("to/subject must not contain control characters");
        }
        String body = requireString(inputParams, "body");

        emailPort.send(to, subject, body);
        return Map.of("sent", true);
    }

    private static String requireString(Map<String, Object> inputParams, String key) {
        Object value = inputParams.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return text;
    }

    private static boolean isSingleEmailAddress(String to) {
        if (to.contains(",") || to.contains(";") || containsHeaderBreak(to) || !to.contains("@")) {
            return false;
        }
        try {
            InternetAddress parsed = new InternetAddress(to, true);
            return parsed.getAddress() != null && !parsed.getAddress().isBlank();
        } catch (AddressException ex) {
            return false;
        }
    }

    private static boolean containsHeaderBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
