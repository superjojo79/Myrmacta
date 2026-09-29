package com.flowforge.infrastructure.config;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.notification.port.out.EmailPort;
import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.signature.SignatureAccessResolver;
import com.flowforge.domain.signature.port.in.CancelSignatureRequestsForProcessUseCase;
import com.flowforge.domain.signature.port.in.HandleSignatureCallbackUseCase;
import com.flowforge.domain.signature.usecase.HandleSignatureCallbackUseCaseImpl;
import com.flowforge.infrastructure.connector.builtin.SignatureCallbackConnector;
import com.flowforge.domain.signature.port.in.ExpireSignatureRequestsUseCase;
import com.flowforge.domain.signature.port.in.ManageSignatureUseCase;
import com.flowforge.domain.signature.port.in.MySignaturesUseCase;
import com.flowforge.domain.signature.usecase.CancelSignatureRequestsForProcessUseCaseImpl;
import com.flowforge.domain.signature.usecase.ExpireSignatureRequestsUseCaseImpl;
import com.flowforge.domain.signature.usecase.ManageSignatureUseCaseImpl;
import com.flowforge.domain.signature.usecase.MySignaturesUseCaseImpl;
import com.flowforge.infrastructure.flowable.SignatureProcessEventListener;
import com.flowforge.infrastructure.signature.SignatureExpiryJob;
import org.flowable.engine.RuntimeService;
import com.flowforge.domain.signature.SignatureNotifier;
import com.flowforge.domain.signature.SignatureOutcomeHandler;
import com.flowforge.domain.signature.port.in.DecideSignatureUseCase;
import com.flowforge.domain.signature.usecase.DecideSignatureUseCaseImpl;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.signature.port.in.DispatchSignatureOutcomesUseCase;
import com.flowforge.domain.signature.port.in.GetSignatureUseCase;
import com.flowforge.domain.signature.port.out.SignatureMetricsPort;
import com.flowforge.domain.signature.port.out.SignatureOutcomeListener;
import com.flowforge.domain.signature.usecase.DispatchSignatureOutcomesUseCaseImpl;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.infrastructure.signature.AfterCommitSignatureOutcomeListener;
import com.flowforge.infrastructure.signature.MicrometerSignatureMetrics;
import com.flowforge.infrastructure.signature.SignatureOutcomeDispatcherJob;
import io.micrometer.core.instrument.MeterRegistry;
import com.flowforge.domain.signature.usecase.GetSignatureUseCaseImpl;
import com.flowforge.domain.signature.port.in.RequestSignatureUseCase;
import com.flowforge.domain.signature.port.in.SendSignatureInvitesUseCase;
import com.flowforge.domain.signature.port.out.SignatureAvailabilityPort;
import com.flowforge.domain.signature.port.out.SignatureInviteSenderPort;
import com.flowforge.domain.signature.port.out.SignaturePort;
import com.flowforge.domain.signature.port.out.SignatureRequestRepository;
import com.flowforge.domain.signature.usecase.RequestSignatureUseCaseImpl;
import com.flowforge.domain.signature.usecase.SendSignatureInvitesUseCaseImpl;
import com.flowforge.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import com.flowforge.domain.workflow.portal.port.out.PortalTokenPort;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.infrastructure.flowable.SignatureRequestTask;
import com.flowforge.infrastructure.signature.EmailSignatureInviteSender;
import com.flowforge.infrastructure.signature.SignatureInviteSenderJob;
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
            SignatureOutcomeListener outcomeListener) {
        return new RequestSignatureUseCaseImpl(tenantProperties.id(), repository, port, availability, resolver,
                elementRepository, storage, notifier, metrics, outcomeListener, CLOCK);
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
        return new com.flowforge.infrastructure.signature.TransactionalHandleSignatureCallbackUseCase(
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
    SignatureInviteSenderPort signatureInviteSenderPort(EmailPort emailPort, PortalProperties portalProperties) {
        return new EmailSignatureInviteSender(emailPort, portalProperties);
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
