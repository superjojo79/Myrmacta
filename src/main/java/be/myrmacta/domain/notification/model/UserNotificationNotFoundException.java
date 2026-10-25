package be.myrmacta.domain.notification.model;

import java.util.UUID;

public class UserNotificationNotFoundException extends RuntimeException {

    public UserNotificationNotFoundException(UUID id) {
        super("User notification not found: " + id);
    }
}
