package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.notification.port.out.EmailPort;
import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.signature.SignatureAccessResolver;
import be.myrmacta.domain.signature.port.in.CancelSignatureRequestsForProcessUseCase;
import be.myrmacta.domain.signature.port.in.HandleSignatureCallbackUseCase;
import be.myrmacta.domain.signature.usecase.HandleSignatureCallbackUseCaseImpl;
import be.myrmacta.infrastructure.connector.builtin.SignatureCallbackConnector;
import be.myrmacta.domain.signature.port.in.ExpireSignatureRequestsUseCase;
import be.myrmacta.domain.signature.port.in.ManageSignatureUseCase;
import be.myrmacta.domain.signature.port.in.MySignaturesUseCase;
import be.myrmacta.domain.signature.usecase.CancelSignatureRequestsForProcessUseCaseImpl;
import be.myrmacta.domain.signature.usecase.ExpireSignatureRequestsUseCaseImpl;
import be.myrmacta.domain.signature.usecase.ManageSignatureUseCaseImpl;
import be.myrmacta.domain.signature.usecase.MySignaturesUseCaseImpl;
import be.myrmacta.infrastructure.flowable.SignatureProcessEventListener;
import be.myrmacta.infrastructure.signature.SignatureExpiryJob;
import org.flowable.engine.RuntimeService;
import be.myrmacta.domain.signature.SignatureNotifier;
import be.myrmacta.domain.signature.SignatureOutcomeHandler;
import be.myrmacta.domain.signature.port.in.DecideSignatureUseCase;
import be.myrmacta.domain.signature.usecase.DecideSignatureUseCaseImpl;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.signature.port.in.DispatchSignatureOutcomesUseCase;
import be.myrmacta.domain.signature.port.in.GetSignatureUseCase;
import be.myrmacta.domain.signature.port.out.InternalSignerEmails;
import be.myrmacta.domain.signature.port.out.SignatureMetricsPort;
import be.myrmacta.domain.signature.port.out.SignatureOutcomeListener;
import be.myrmacta.domain.signature.usecase.DispatchSignatureOutcomesUseCaseImpl;
import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.port.out.KnownUserRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.infrastructure.signature.AfterCommitSignatureOutcomeListener;
import be.myrmacta.infrastructure.signature.MicrometerSignatureMetrics;
import be.myrmacta.infrastructure.signature.SignatureOutcomeDispatcherJob;
import io.micrometer.core.instrument.MeterRegistry;
import be.myrmacta.domain.signature.usecase.GetSignatureUseCaseImpl;
import be.myrmacta.domain.signature.port.in.RequestSignatureUseCase;
import be.myrmacta.domain.signature.port.in.SendSignatureInvitesUseCase;
import be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort;
import be.myrmacta.domain.signature.port.out.SignatureInviteSenderPort;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.domain.signature.usecase.RequestSignatureUseCaseImpl;
import be.myrmacta.domain.signature.usecase.SendSignatureInvitesUseCaseImpl;
import be.myrmacta.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import be.myrmacta.domain.workflow.portal.port.out.PortalTokenPort;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.infrastructure.flowable.SignatureRequestTask;
import be.myrmacta.infrastructure.signature.EmailSignatureInviteSender;
import be.myrmacta.infrastructure.signature.SignatureInviteSenderJob;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * TODO §72 — câblage des use cases signature. Présents seulement si un fournisseur est actif
 * ({@code app.signature.provider} autre que {@code none}) : sans fournisseur, aucun delegate, aucun
 * job, aucune route (D-SIGNATURE-ACTIVATION).
 */
@Configuration
@ConditionalOnExpression("'${app.signature.provider:none}' != 'none'")
public class SignatureUseCaseConfig {

    private static final Clock CLOCK = Clock.systemUTC();

    @Bean
    SignatureNotifier signatureNotifier(
            TenantProperties tenantProperties,
            DossierWorkflowLinkRepository workflowLinkRepository,
            @Lazy WorkflowParticipantPort participantPort,
            SendNotificationUseCase sendNotificationUseCase) {
        return new SignatureNotifier(
                tenantProperties.id(), workflowLinkRepository, participantPort, sendNotificationUseCase);
    }

    @Bean
    RequestSignatureUseCase requestSignatureUseCase(
            TenantProperties tenantProperties,
            SignatureRequestRepository repository,
            SignaturePort port,
            SignatureAvailabilityPort availability,
            PortalRecipientResolverPort resolver,
            DossierElementRepository elementRepository,
            DocumentStoragePort storage,
            SignatureNotifier notifier,
            SignatureMetricsPort metrics,
            SignatureOutcomeListener outcomeListener,
            KnownUserRepository knownUsers) {
        InternalSignerEmails emails = userId -> knownUsers.findById(tenantProperties.id(), userId).map(KnownUser::email);
        return new RequestSignatureUseCaseImpl(tenantProperties.id(), repository, port, availability, resolver,
                elementRepository, storage, notifier, metrics, outcomeListener, emails, CLOCK);
    }

    @Bean
    SignatureMetricsPort signatureMetricsPort(MeterRegistry meterRegistry) {
        return new MicrometerSignatureMetrics(meterRegistry);
    }

    @Bean
    DispatchSignatureOutcomesUseCase dispatchSignatureOutcomesUseCase(
            SignatureRequestRepository repository,
            @Lazy WorkflowEnginePort workflowEnginePort,
            SignatureMetricsPort metrics,
            SignatureProperties properties) {
        return new DispatchSignatureOutcomesUseCaseImpl(repository, workflowEnginePort, metrics, CLOCK,
                properties.dispatch().maxAttempts(), properties.dispatch().batchSize());
    }

    @Bean
    SignatureOutcomeListener signatureOutcomeListener(
            DispatchSignatureOutcomesUseCase dispatcher, PlatformTransactionManager transactionManager) {
        return new AfterCommitSignatureOutcomeListener(dispatcher, transactionManager);
    }

    @Bean
    SignatureOutcomeDispatcherJob signatureOutcomeDispatcherJob(DispatchSignatureOutcomesUseCase dispatcher) {
        return new SignatureOutcomeDispatcherJob(dispatcher);
    }

    @Bean(name = "signatureRequestTask")
    SignatureRequestTask signatureRequestTask(RequestSignatureUseCase useCase) {
        return new SignatureRequestTask(useCase);
    }

    @Bean
    SignatureAccessResolver signatureAccessResolver(
            SignatureRequestRepository repository, PortalTokenPort tokenPort, DossierRepository dossierRepository) {
        return new SignatureAccessResolver(repository, tokenPort, dossierRepository, CLOCK);
    }

    @Bean
    SignatureOutcomeHandler signatureOutcomeHandler(
            TenantProperties tenantProperties,
            SignatureRequestRepository repository,
            SignaturePort port,
            DocumentStoragePort storage,
            AddDossierElementUseCase addElementUseCase,
            SignatureNotifier notifier,
            SignatureMetricsPort metrics,
            SignatureOutcomeListener listener) {
        return new SignatureOutcomeHandler(tenantProperties.id(), repository, port, storage, addElementUseCase,
                notifier, metrics, listener, CLOCK);
    }

    @Bean
    DecideSignatureUseCase decideSignatureUseCase(
            SignatureRequestRepository repository,
            SignatureAccessResolver accessResolver,
            SignatureOutcomeHandler outcomeHandler) {
        return new DecideSignatureUseCaseImpl(repository, accessResolver, outcomeHandler, CLOCK);
    }

    @Bean
    ManageSignatureUseCase manageSignatureUseCase(
            TenantProperties tenantProperties,
            SignatureRequestRepository repository,
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy,
            SignatureOutcomeHandler outcomeHandler,
            SignatureProperties properties) {
        return new ManageSignatureUseCaseImpl(tenantProperties.id(), repository, dossierRepository, accessPolicy,
                outcomeHandler, CLOCK, properties.send().resendMinInterval());
    }

    @Bean
    MySignaturesUseCase mySignaturesUseCase(
            TenantProperties tenantProperties,
            SignatureRequestRepository repository,
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy,
            DossierElementRepository elementRepository,
            DocumentStoragePort storage,
            DecideSignatureUseCase decideUseCase) {
        return new MySignaturesUseCaseImpl(tenantProperties.id(), repository, dossierRepository, accessPolicy,
                elementRepository, storage, decideUseCase, CLOCK);
    }

    @Bean
    ExpireSignatureRequestsUseCase expireSignatureRequestsUseCase(
            SignatureRequestRepository repository, SignatureOutcomeHandler outcomeHandler, SignatureProperties properties) {
        return new ExpireSignatureRequestsUseCaseImpl(repository, outcomeHandler, CLOCK, properties.expiry().batchSize());
    }

    @Bean
    SignatureExpiryJob signatureExpiryJob(ExpireSignatureRequestsUseCase useCase) {
        return new SignatureExpiryJob(useCase);
    }

    @Bean
    CancelSignatureRequestsForProcessUseCase cancelSignatureRequestsForProcessUseCase(
            SignatureRequestRepository repository, SignatureOutcomeHandler outcomeHandler) {
        return new CancelSignatureRequestsForProcessUseCaseImpl(repository, outcomeHandler, CLOCK);
    }

    @Bean
    SignatureProcessEventListener signatureProcessEventListener(
            RuntimeService runtimeService, CancelSignatureRequestsForProcessUseCase useCase) {
        return new SignatureProcessEventListener(runtimeService, useCase);
    }

    @Bean
    HandleSignatureCallbackUseCase handleSignatureCallbackUseCase(
            SignatureRequestRepository repository,
            SignatureOutcomeHandler outcomeHandler,
            SignatureMetricsPort metrics,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        return new be.myrmacta.infrastructure.signature.TransactionalHandleSignatureCallbackUseCase(
                new HandleSignatureCallbackUseCaseImpl(repository, outcomeHandler, metrics, CLOCK), transactionManager);
    }

    /** Entrée de catalogue : configuration, secrets et retour du fournisseur actif (D-SIGNATURE-ABSTRACTION). */
    @Bean
    SignatureCallbackConnector signatureCallbackConnector(SignaturePort port, HandleSignatureCallbackUseCase useCase) {
        return new SignatureCallbackConnector(port, useCase);
    }

    @Bean
    GetSignatureUseCase getSignatureUseCase(
            TenantProperties tenantProperties,
            SignatureAccessResolver accessResolver,
            DossierElementRepository elementRepository,
            DocumentStoragePort storage) {
        return new GetSignatureUseCaseImpl(tenantProperties.id(), accessResolver, elementRepository, storage);
    }

    @Bean
    SignatureInviteSenderPort signatureInviteSenderPort(
            EmailPort emailPort, PortalProperties portalProperties, I18nProperties i18n) {
        return new EmailSignatureInviteSender(emailPort, portalProperties, i18n.locale());
    }

    @Bean
    SendSignatureInvitesUseCase sendSignatureInvitesUseCase(
            SignatureRequestRepository repository,
            PortalTokenPort tokenPort,
            SignatureInviteSenderPort sender,
            DossierRepository dossierRepository,
            SignatureNotifier notifier,
            SignatureProperties properties) {
        return new SendSignatureInvitesUseCaseImpl(repository, tokenPort, sender, dossierRepository, notifier, CLOCK,
                properties.send().maxAttempts(), properties.send().batchSize());
    }

    @Bean
    SignatureInviteSenderJob signatureInviteSenderJob(SendSignatureInvitesUseCase useCase) {
        return new SignatureInviteSenderJob(useCase);
    }
}
