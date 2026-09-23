package com.flowforge.infrastructure.web.notification;

import com.flowforge.domain.notification.port.in.NotificationInboxUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.web.ListSorts;
import com.flowforge.infrastructure.web.PageQueryParser;
import com.flowforge.infrastructure.web.PageResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Self-scoped: {@code recipient} is always the authenticated principal's subject, never a client
 * parameter — every query is naturally isolated per user, no role restriction (every authenticated
 * role has its own inbox). Distinct from {@link NotificationSendController}, which shares the
 * {@code /api/notifications} prefix but is admin-only and unrelated (manual send, not history).
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationInboxController {

    private final TenantProperties tenantProperties;
    private final NotificationInboxUseCase inboxUseCase;
    private final ObjectMapper objectMapper;

    public NotificationInboxController(
            TenantProperties tenantProperties, NotificationInboxUseCase inboxUseCase, ObjectMapper objectMapper) {
        this.tenantProperties = tenantProperties;
        this.inboxUseCase = inboxUseCase;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public PageResponse<UserNotificationResponse> list(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return PageResponse.from(inboxUseCase
                .list(
                        tenantProperties.id(),
                        user.getSubject(),
                        PageQueryParser.parse(page, size, null, ListSorts.USER_NOTIFICATIONS))
                .map(record -> UserNotificationResponse.from(record, objectMapper)));
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal OidcUser user) {
        return new UnreadCountResponse(inboxUseCase.unreadCount(tenantProperties.id(), user.getSubject()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        inboxUseCase.markRead(id, tenantProperties.id(), user.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal OidcUser user) {
        inboxUseCase.markAllRead(tenantProperties.id(), user.getSubject());
        return ResponseEntity.noContent().build();
    }
}
