package be.myrmacta.domain.notification.port.out;

import java.util.Map;

public interface BrowserPushPort {

    /**
     * Pushes an event to all SSE emitters registered for {@code userId}.
     *
     * @return {@code true} if delivered to at least one emitter
     */
    boolean push(String userId, Map<String, Object> event);
}
