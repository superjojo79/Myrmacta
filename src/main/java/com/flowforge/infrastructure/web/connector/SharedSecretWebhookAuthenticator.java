package com.flowforge.infrastructure.web.connector;

import com.flowforge.domain.connector.model.WebhookAuthType;
import com.flowforge.domain.connector.model.WebhookAuthenticationException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class SharedSecretWebhookAuthenticator implements WebhookAuthenticator {

    private static final String HEADER = "X-Webhook-Token";

    @Override
    public boolean supports(WebhookAuthType authType) {
        return authType == WebhookAuthType.SHARED_SECRET_TOKEN;
    }

    @Override
    public void authenticate(UUID instanceId, String webhookSecret, byte[] rawBody, HttpHeaders headers) {
        String provided = headers.getFirst(HEADER);
        if (webhookSecret == null
                || webhookSecret.isBlank()
                || provided == null
                || !HmacWebhookAuthenticator.constantTimeEquals(webhookSecret, provided)) {
            throw new WebhookAuthenticationException(instanceId);
        }
    }
}
