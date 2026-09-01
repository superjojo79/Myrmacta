package com.flowforge.infrastructure.connector.builtin;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ServiceConnector;
import com.flowforge.domain.notification.port.out.EmailPort;
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
        if (!to.contains("@")) {
            throw new IllegalArgumentException("to must be a valid email address");
        }
        String subject = requireString(inputParams, "subject");
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
}
