package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.notification.template.port.in.ActivateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.ArchiveNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.GetNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.ListNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.NewNotificationTemplateVersionUseCase;
import be.myrmacta.domain.notification.template.port.in.PreviewNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.RenderNotificationUseCase;
import be.myrmacta.domain.notification.template.port.in.ResolveNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.UpdateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateChecker;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRenderer;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import be.myrmacta.domain.notification.template.usecase.ActivateNotificationTemplateUseCaseImpl;
import be.myrmacta.domain.notification.template.usecase.ArchiveNotificationTemplateUseCaseImpl;
import be.myrmacta.domain.notification.template.usecase.CreateNotificationTemplateUseCaseImpl;
import be.myrmacta.domain.notification.template.usecase.GetNotificationTemplateUseCaseImpl;
import be.myrmacta.domain.notification.template.usecase.ListNotificationTemplateUseCaseImpl;
import be.myrmacta.domain.notification.template.usecase.NewNotificationTemplateVersionUseCaseImpl;
import be.myrmacta.domain.notification.template.usecase.PreviewNotificationTemplateUseCaseImpl;
import be.myrmacta.domain.notification.template.usecase.RenderNotificationUseCaseImpl;
import be.myrmacta.domain.notification.template.usecase.ResolveNotificationTemplateUseCaseImpl;
import be.myrmacta.domain.notification.template.usecase.UpdateNotificationTemplateUseCaseImpl;
import be.myrmacta.infrastructure.document.ThymeleafTemplateSyntaxChecker;
import be.myrmacta.infrastructure.notification.template.ThymeleafNotificationTemplateChecker;
import be.myrmacta.infrastructure.notification.template.ThymeleafNotificationTemplateRenderer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Câblage des gabarits de notification (TODO §123), calqué sur {@link DocumentConfig}. */
@Configuration
@EnableConfigurationProperties(NotificationTemplateProperties.class)
public class NotificationTemplateConfig {

    @Bean
    NotificationTemplateRenderer notificationTemplateRenderer(NotificationTemplateProperties properties) {
        return new ThymeleafNotificationTemplateRenderer(properties);
    }

    @Bean
    NotificationTemplateChecker notificationTemplateChecker(ThymeleafTemplateSyntaxChecker syntaxChecker) {
        return new ThymeleafNotificationTemplateChecker(syntaxChecker);
    }

    @Bean
    CreateNotificationTemplateUseCase createNotificationTemplateUseCase(
            TenantProperties tenantProperties,
            NotificationTemplateRepository repository,
            NotificationTemplateProperties properties) {
        return new CreateNotificationTemplateUseCaseImpl(tenantProperties.id(), repository, properties.limits());
    }

    @Bean
    UpdateNotificationTemplateUseCase updateNotificationTemplateUseCase(
            TenantProperties tenantProperties,
            NotificationTemplateRepository repository,
            NotificationTemplateProperties properties) {
        return new UpdateNotificationTemplateUseCaseImpl(tenantProperties.id(), repository, properties.limits());
    }

    /** Une seule transaction : archive la version active précédente, puis active le brouillon. */
    @Bean
    ActivateNotificationTemplateUseCase activateNotificationTemplateUseCase(
            TenantProperties tenantProperties,
            NotificationTemplateRepository repository,
            NotificationTemplateChecker checker,
            PlatformTransactionManager transactionManager) {
        ActivateNotificationTemplateUseCaseImpl impl =
                new ActivateNotificationTemplateUseCaseImpl(tenantProperties.id(), repository, checker);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return (id, updatedBy) -> tx.execute(status -> impl.activate(id, updatedBy));
    }

    @Bean
    ArchiveNotificationTemplateUseCase archiveNotificationTemplateUseCase(
            TenantProperties tenantProperties, NotificationTemplateRepository repository) {
        return new ArchiveNotificationTemplateUseCaseImpl(tenantProperties.id(), repository);
    }

    @Bean
    NewNotificationTemplateVersionUseCase newNotificationTemplateVersionUseCase(
            TenantProperties tenantProperties, NotificationTemplateRepository repository) {
        return new NewNotificationTemplateVersionUseCaseImpl(tenantProperties.id(), repository);
    }

    @Bean
    GetNotificationTemplateUseCase getNotificationTemplateUseCase(
            TenantProperties tenantProperties, NotificationTemplateRepository repository) {
        return new GetNotificationTemplateUseCaseImpl(tenantProperties.id(), repository);
    }

    @Bean
    ListNotificationTemplateUseCase listNotificationTemplateUseCase(
            TenantProperties tenantProperties, NotificationTemplateRepository repository) {
        return new ListNotificationTemplateUseCaseImpl(tenantProperties.id(), repository);
    }

    @Bean
    ResolveNotificationTemplateUseCase resolveNotificationTemplateUseCase(
            TenantProperties tenantProperties, NotificationTemplateRepository repository) {
        return new ResolveNotificationTemplateUseCaseImpl(tenantProperties.id(), repository);
    }

    @Bean
    RenderNotificationUseCase renderNotificationUseCase(
            ResolveNotificationTemplateUseCase resolveUseCase, NotificationTemplateRenderer renderer) {
        return new RenderNotificationUseCaseImpl(resolveUseCase, renderer);
    }

    @Bean
    PreviewNotificationTemplateUseCase previewNotificationTemplateUseCase(
            TenantProperties tenantProperties,
            NotificationTemplateRepository repository,
            NotificationTemplateChecker checker,
            NotificationTemplateRenderer renderer,
            NotificationTemplateProperties properties) {
        return new PreviewNotificationTemplateUseCaseImpl(
                tenantProperties.id(), repository, checker, renderer, properties.limits());
    }
}
