package be.myrmacta.infrastructure.web.notification;

import be.myrmacta.domain.notification.model.NotificationWebhookConfig;
import be.myrmacta.domain.notification.port.in.ManageNotificationWebhookUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import be.myrmacta.infrastructure.web.ListSorts;
import be.myrmacta.infrastructure.web.PageQueryParser;
import be.myrmacta.infrastructure.web.PageResponse;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification-webhooks")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class NotificationWebhookAdminController {

    private final TenantProperties tenantProperties;
    private final ManageNotificationWebhookUseCase manageUseCase;
    private final OutboundUrlGuard outboundUrlGuard;

    public NotificationWebhookAdminController(
            TenantProperties tenantProperties,
            ManageNotificationWebhookUseCase manageUseCase,
            OutboundUrlGuard outboundUrlGuard) {
        this.tenantProperties = tenantProperties;
        this.manageUseCase = manageUseCase;
        this.outboundUrlGuard = outboundUrlGuard;
    }

    @PostMapping
    public ResponseEntity<NotificationWebhookResponse> create(
            @RequestBody CreateNotificationWebhookRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        outboundUrlGuard.assertSafe(request.targetUrl());
        NotificationWebhookConfig created = manageUseCase.create(
                new ManageNotificationWebhookUseCase.CreateCommand(
                        tenantProperties.id(),
                        request.name(),
                        request.targetUrl(),
                        request.secret(),
                        request.enabled() == null || request.enabled()));
        return ResponseEntity.created(URI.create("/api/notification-webhooks/" + created.id()))
                .body(NotificationWebhookResponse.from(created));
    }

    @GetMapping
    public PageResponse<NotificationWebhookResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return PageResponse.from(manageUseCase
                .list(
                        tenantProperties.id(),
                        PageQueryParser.parse(page, size, sort, ListSorts.NOTIFICATION_WEBHOOKS))
                .map(NotificationWebhookResponse::from));
    }

    @GetMapping("/{id}")
    public NotificationWebhookResponse get(@PathVariable UUID id) {
        return NotificationWebhookResponse.from(manageUseCase.get(id, tenantProperties.id()));
    }

    @PatchMapping("/{id}")
    public NotificationWebhookResponse update(
            @PathVariable UUID id, @RequestBody UpdateNotificationWebhookRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.targetUrl() != null && !request.targetUrl().isBlank()) {
            outboundUrlGuard.assertSafe(request.targetUrl());
        }
        NotificationWebhookConfig updated = manageUseCase.update(
                new ManageNotificationWebhookUseCase.UpdateCommand(
                        id,
                        tenantProperties.id(),
                        request.name(),
                        request.targetUrl(),
                        request.secret(),
                        request.enabled(),
                        Boolean.TRUE.equals(request.restore())));
        return NotificationWebhookResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable UUID id) {
        manageUseCase.softDelete(id, tenantProperties.id());
        return ResponseEntity.noContent().build();
    }
}
