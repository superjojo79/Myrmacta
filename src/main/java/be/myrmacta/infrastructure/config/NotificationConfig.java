package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.notification.port.in.ManageNotificationWebhookUseCase;
import be.myrmacta.domain.notification.port.in.NotificationInboxUseCase;
import be.myrmacta.domain.notification.port.in.ResolveEmailAttachmentUseCase;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.notification.port.out.NotificationOutboxRepository;
import be.myrmacta.domain.notification.port.out.NotificationWebhookRepository;
import be.myrmacta.domain.notification.port.out.UserNotificationRepository;
import be.myrmacta.domain.notification.usecase.ManageNotificationWebhookUseCaseImpl;
import be.myrmacta.domain.notification.usecase.NotificationInboxUseCaseImpl;
import be.myrmacta.domain.notification.usecase.ResolveEmailAttachmentUseCaseImpl;
import be.myrmacta.domain.notification.usecase.SendNotificationUseCaseImpl;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties({NotificationProperties.class, NotificationEmailProperties.class})
public class NotificationConfig {

    @Bean
    OutboundUrlGuard outboundUrlGuard(Environment environment) {
        // TODO §132 (D-LOOPBACK-LOCAL-ENV) : http://localhost seulement en environnement local, pas dès le profil test.
        boolean allowLocalHttp = OutboundUrlGuard.allowsLocalLoopback(
                environment.getProperty("app.environment", "production"), environment.getActiveProfiles());
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

    /** Issue #183 — contrôles de la pièce jointe d'un e-mail « Notifier » (D-NOTIF-ATTACH-PRODUCED-DOC). */
    @Bean
    ResolveEmailAttachmentUseCase resolveEmailAttachmentUseCase(
            TenantProperties tenantProperties,
            DossierElementRepository elementRepository,
            DocumentStoragePort documentStoragePort,
            NotificationEmailProperties emailProperties) {
        return new ResolveEmailAttachmentUseCaseImpl(
                tenantProperties.id(), elementRepository, documentStoragePort, emailProperties.attachmentMaxBytes());
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
