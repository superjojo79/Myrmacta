package com.flowforge.infrastructure.config;

import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.notification.port.in.ManageNotificationWebhookUseCase;
import com.flowforge.domain.notification.port.in.NotificationInboxUseCase;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.notification.port.out.NotificationOutboxRepository;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.notification.port.out.UserNotificationRepository;
import com.flowforge.domain.notification.usecase.ManageNotificationWebhookUseCaseImpl;
import com.flowforge.domain.notification.usecase.NotificationInboxUseCaseImpl;
import com.flowforge.domain.notification.usecase.SendNotificationUseCaseImpl;
import com.flowforge.infrastructure.notification.OutboundUrlGuard;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    @Bean
    OutboundUrlGuard outboundUrlGuard(Environment environment) {
        // A13: test/local may use http://localhost only (not public http).
        boolean allowLocalHttp = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("test") || profile.equals("local"));
        return new OutboundUrlGuard(allowLocalHttp);
    }

    @Bean
    SendNotificationUseCase sendNotificationUseCase(
            NotificationOutboxRepository outboxRepository,
            NotificationWebhookRepository webhookRepository,
            DossierTypeRepository dossierTypeRepository,
            UserNotificationRepository userNotificationRepository) {
        return new SendNotificationUseCaseImpl(
                outboxRepository, webhookRepository, dossierTypeRepository, userNotificationRepository);
    }

    @Bean
    ManageNotificationWebhookUseCase manageNotificationWebhookUseCase(
            NotificationWebhookRepository webhookRepository) {
        return new ManageNotificationWebhookUseCaseImpl(webhookRepository);
    }

    @Bean
    NotificationInboxUseCase notificationInboxUseCase(UserNotificationRepository userNotificationRepository) {
        return new NotificationInboxUseCaseImpl(userNotificationRepository);
    }
}
