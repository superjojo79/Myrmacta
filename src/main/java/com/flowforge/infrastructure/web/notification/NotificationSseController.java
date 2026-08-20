package com.flowforge.infrastructure.web.notification;

import com.flowforge.infrastructure.notification.SseNotificationAdapter;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.swagger.v3.oas.annotations.Hidden;

@RestController
@RequestMapping("/api/notifications")
public class NotificationSseController {

    private static final long SSE_TIMEOUT_MS = TimeUnit.HOURS.toMillis(1);

    private final SseNotificationAdapter sseNotificationAdapter;

    public NotificationSseController(SseNotificationAdapter sseNotificationAdapter) {
        this.sseNotificationAdapter = sseNotificationAdapter;
    }

    @Hidden
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal OidcUser user) {
        if (user == null || user.getSubject() == null || user.getSubject().isBlank()) {
            throw new IllegalArgumentException("Authenticated user subject is required");
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sseNotificationAdapter.register(user.getSubject(), emitter);
        return emitter;
    }
}
