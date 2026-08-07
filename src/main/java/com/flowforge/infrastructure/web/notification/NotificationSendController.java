package com.flowforge.infrastructure.web.notification;

import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationSendController {

    private final TenantProperties tenantProperties;
    private final SendNotificationUseCase sendNotificationUseCase;

    public NotificationSendController(
            TenantProperties tenantProperties, SendNotificationUseCase sendNotificationUseCase) {
        this.tenantProperties = tenantProperties;
        this.sendNotificationUseCase = sendNotificationUseCase;
    }

    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
    public ResponseEntity<SendNotificationResponse> send(@RequestBody SendNotificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        List<UUID> outboxIds = sendNotificationUseCase.send(request.toEvent(), tenantProperties.id());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new SendNotificationResponse(outboxIds));
    }
}
