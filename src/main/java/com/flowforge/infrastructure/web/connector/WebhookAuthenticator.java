package com.flowforge.infrastructure.web.connector;

import com.flowforge.domain.connector.model.WebhookAuthType;
import java.util.UUID;
import org.springframework.http.HttpHeaders;

public interface WebhookAuthenticator {

    boolean supports(WebhookAuthType authType);

    void authenticate(UUID instanceId, String webhookSecret, byte[] rawBody, HttpHeaders headers);
}
