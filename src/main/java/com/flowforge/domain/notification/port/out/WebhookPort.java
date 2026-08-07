package com.flowforge.domain.notification.port.out;

import com.flowforge.domain.notification.model.WebhookDeliveryCommand;

public interface WebhookPort {

    void send(WebhookDeliveryCommand command);
}
