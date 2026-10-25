package be.myrmacta.domain.notification.port.out;

import be.myrmacta.domain.notification.model.WebhookDeliveryCommand;

public interface WebhookPort {

    void send(WebhookDeliveryCommand command);
}
