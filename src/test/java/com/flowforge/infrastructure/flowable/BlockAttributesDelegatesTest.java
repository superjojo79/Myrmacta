package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.port.in.ExecuteConnectorUseCase;
import com.flowforge.domain.document.port.in.GenerateDocumentUseCase;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.RelationType;
import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.model.NotificationValidationException;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.workflow.port.in.UpdateWorkflowLinkStatusUseCase;
import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import com.flowforge.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import com.flowforge.application.workflow.ManualStartOrchestrator;
import com.flowforge.infrastructure.dossier.ChildDossierRelationConfirmer;
import com.flowforge.infrastructure.config.TenantProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * S-RT-1..S-RT-4 pour les six delegates concernés (TODO §73, D-PALETTE-PARAMS / D-PALETTE-PRECEDENCE) : variable
 * seule = comportement d'avant ; attribut littéral seul = valeur ; les deux = l'attribut gagne (I1) ; aucun = même erreur.
 */
class BlockAttributesDelegatesTest {

    private static DelegateExecution execution(Map<String, Object> variables, Map<String, String> attributes) {
        DelegateExecution execution = mock(DelegateExecution.class);
        variables.forEach((k, v) -> when(execution.getVariable(k)).thenReturn(v));
        when(execution.getProcessInstanceId()).thenReturn("pi-1");
        when(execution.getCurrentActivityId()).thenReturn("act");
        if (!attributes.isEmpty()) {
            ServiceTask task = new ServiceTask();
            task.setId("act");
            attributes.forEach((k, v) -> DeployedActivityParametersTest.taskWith(k, v)
                    .getAttributes().forEach((name, list) -> list.forEach(task::addAttribute)));
            when(execution.getCurrentFlowElement()).thenReturn(task);
        }
        return execution;
    }

    // --- updateDossierStatusTask -------------------------------------------------------------------------------

    @Test
    void dossierStatus_attributeOnly_attributeWins_andMissingKeepsTheError() {
        UpdateDossierStatusUseCase useCase = mock(UpdateDossierStatusUseCase.class);
        UpdateDossierStatusDelegate delegate = new UpdateDossierStatusDelegate(useCase);
        UUID dossier = UUID.randomUUID();
        Map<String, Object> dossierVar = Map.of("dossierId", dossier.toString());

        delegate.execute(execution(dossierVar, Map.of("targetDossierStatus", "CLOTURE")));
        Map<String, Object> both = new HashMap<>(dossierVar);
        both.put("targetDossierStatus", "SUSPENDU");
        delegate.execute(execution(both, Map.of("targetDossierStatus", "CLOTURE")));
        delegate.execute(execution(both, Map.of()));

        ArgumentCaptor<UpdateDossierStatusUseCase.UpdateDossierStatusCommand> captor =
                ArgumentCaptor.forClass(UpdateDossierStatusUseCase.UpdateDossierStatusCommand.class);
        verify(useCase, org.mockito.Mockito.times(3)).update(captor.capture(), any());
        assertThat(captor.getAllValues()).extracting(c -> c.status())
                .containsExactly(DossierStatus.CLOTURE, DossierStatus.CLOTURE, DossierStatus.SUSPENDU);
        assertThatThrownBy(() -> delegate.execute(execution(dossierVar, Map.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("targetDossierStatus");
    }

    // --- updateWorkflowLinkStatusTask --------------------------------------------------------------------------

    @Test
    void workflowStatus_attributeOnly_attributeWins_andMissingKeepsTheError() {
        UpdateWorkflowLinkStatusUseCase useCase = mock(UpdateWorkflowLinkStatusUseCase.class);
        UpdateWorkflowLinkStatusDelegate delegate = new UpdateWorkflowLinkStatusDelegate(useCase);
        UUID link = UUID.randomUUID();
        Map<String, Object> linkVar = Map.of("dossierWorkflowLinkId", link.toString());

        delegate.execute(execution(linkVar, Map.of("targetWorkflowStatus", "EN_INSTRUCTION")));
        Map<String, Object> both = new HashMap<>(linkVar);
        both.put("targetWorkflowStatus", "VALIDE");
        delegate.execute(execution(both, Map.of("targetWorkflowStatus", "EN_INSTRUCTION")));

        verify(useCase, org.mockito.Mockito.times(2)).update(link, "EN_INSTRUCTION", "act");
        verify(useCase, never()).update(link, "VALIDE", "act");
        assertThatThrownBy(() -> delegate.execute(execution(linkVar, Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- connectorServiceTask ----------------------------------------------------------------------------------

    @Test
    void connector_attributeOnly_attributeWins_andMissingKeepsTheError() {
        ExecuteConnectorUseCase useCase = mock(ExecuteConnectorUseCase.class);
        ConnectorServiceTask delegate = new ConnectorServiceTask(useCase, new TenantProperties("tenant_dev"));
        UUID fromAttribute = UUID.randomUUID();
        UUID fromVariable = UUID.randomUUID();

        delegate.execute(execution(Map.of(), Map.of("connectorInstanceId", fromAttribute.toString())));
        delegate.execute(execution(Map.of("connectorInstanceId", fromVariable.toString()),
                Map.of("connectorInstanceId", fromAttribute.toString())));

        delegate.execute(execution(Map.of("connectorInstanceId", fromVariable.toString()), Map.of()));

        verify(useCase, org.mockito.Mockito.times(2)).execute(eq(fromAttribute), eq("tenant_dev"), any(), any());
        verify(useCase).execute(eq(fromVariable), eq("tenant_dev"), any(), any());
        assertThatThrownBy(() -> delegate.execute(execution(Map.of(), Map.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("connectorInstanceId");
    }

    // --- notificationDispatchTask ------------------------------------------------------------------------------

    /** TODO §93 (D-NOTIF-SOURCE-LITERAL) — destinataire lu dans le dossier ou dans une variable désignée par le BPMN. */
    @Test
    void notification_recipientSourceInTheLiteralAttributeIsResolved() {
        SendNotificationUseCase useCase = mock(SendNotificationUseCase.class);
        PortalRecipientResolverPort resolver = mock(PortalRecipientResolverPort.class);
        NotificationDispatchTask delegate = new NotificationDispatchTask(new TenantProperties("tenant_dev"), useCase, resolver,
                mock(com.flowforge.domain.notification.port.in.ResolveEmailAttachmentUseCase.class));
        UUID dossier = UUID.randomUUID();
        when(resolver.resolve(new PortalRecipientSource(PortalRecipientSource.Kind.DOSSIER_FIELD, "email"), dossier, "pi-1"))
                .thenReturn(java.util.Optional.of("camille@example.org"));
        // variable: est lue dans l'exécution (valeur posée plus tôt dans la même transaction comprise), sans le résolveur.
        Map<String, Object> vars = Map.of("dossierId", dossier.toString(), "adresse", " b@example.org ");

        delegate.execute(execution(vars, Map.of("notificationChannels", "EMAIL", "notificationTo", "dossierField:email")));
        delegate.execute(execution(vars, Map.of("notificationChannels", "EMAIL", "notificationTo", "variable:adresse")));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(useCase, org.mockito.Mockito.times(2)).send(captor.capture(), eq("tenant_dev"));
        assertThat(captor.getAllValues()).extracting(NotificationEvent::to)
                .containsExactly("camille@example.org", "b@example.org");
    }

    @Test
    void notification_emptyResolvedRecipientIsAnIncidentNamingTheSourceNeverAValue() {
        SendNotificationUseCase useCase = mock(SendNotificationUseCase.class);
        PortalRecipientResolverPort resolver = mock(PortalRecipientResolverPort.class);
        NotificationDispatchTask delegate = new NotificationDispatchTask(new TenantProperties("tenant_dev"), useCase, resolver,
                mock(com.flowforge.domain.notification.port.in.ResolveEmailAttachmentUseCase.class));
        UUID dossier = UUID.randomUUID();
        PortalRecipientSource email = new PortalRecipientSource(PortalRecipientSource.Kind.DOSSIER_FIELD, "email");
        Map<String, Object> vars = Map.of("dossierId", dossier.toString());
        Map<String, String> attrs = Map.of("notificationChannels", "EMAIL", "notificationTo", "dossierField:email");

        for (java.util.Optional<Object> resolved : List.of(java.util.Optional.<Object>empty(),
                java.util.Optional.<Object>of("  "), java.util.Optional.<Object>of(List.of("x@example.org")))) {
            when(resolver.resolve(email, dossier, "pi-1")).thenReturn(resolved);
            assertThatThrownBy(() -> delegate.execute(execution(vars, attrs)))
                    .isInstanceOf(NotificationValidationException.class)
                    .hasMessageContaining("dossierField:email")
                    .hasMessageNotContaining("x@example.org");
        }
        verify(useCase, never()).send(any(), any());
    }

    @Test
    void notification_aResolvedRecipientMustBeASingleMailbox() {
        // MimeMessageHelper.setTo analyse une LISTE : un champ « a@x.org, b@y.org » enverrait à plusieurs destinataires.
        SendNotificationUseCase useCase = mock(SendNotificationUseCase.class);
        PortalRecipientResolverPort resolver = mock(PortalRecipientResolverPort.class);
        NotificationDispatchTask delegate = new NotificationDispatchTask(new TenantProperties("tenant_dev"), useCase, resolver,
                mock(com.flowforge.domain.notification.port.in.ResolveEmailAttachmentUseCase.class));
        UUID dossier = UUID.randomUUID();
        PortalRecipientSource email = new PortalRecipientSource(PortalRecipientSource.Kind.DOSSIER_FIELD, "email");
        Map<String, Object> vars = Map.of("dossierId", dossier.toString());
        Map<String, String> attrs = Map.of("notificationChannels", "EMAIL", "notificationTo", "dossierField:email");

        String crlf = new String(new char[] {13, 10});
        for (String bad : List.of("a@x.org, b@y.org", "a@x.org;b@y.org", "Camille <a@x.org>", "a@x.org" + crlf + "Bcc: b@y.org",
                "a b@x.org", "pas-une-adresse", "@x.org", "a@")) {
            when(resolver.resolve(email, dossier, "pi-1")).thenReturn(java.util.Optional.of(bad));
            assertThatThrownBy(() -> delegate.execute(execution(vars, attrs)))
                    .as(bad)
                    .isInstanceOf(NotificationValidationException.class)
                    .hasMessageContaining("dossierField:email")
                    .hasMessageNotContaining("x.org");
        }
        verify(useCase, never()).send(any(), any());
    }

    @Test
    void notification_aSourceGivenThroughTheVariableIsNeverResolved() {
        // Une variable est contrôlée par un utilisateur (démarrage, complétion de tâche) : elle ne désigne pas un autre champ.
        SendNotificationUseCase useCase = mock(SendNotificationUseCase.class);
        PortalRecipientResolverPort resolver = mock(PortalRecipientResolverPort.class);
        NotificationDispatchTask delegate = new NotificationDispatchTask(new TenantProperties("tenant_dev"), useCase, resolver,
                mock(com.flowforge.domain.notification.port.in.ResolveEmailAttachmentUseCase.class));

        // Elle reste une adresse littérale… qui n'en est pas une : refusée (revue L3), jamais résolue.
        assertThatThrownBy(() -> delegate.execute(
                execution(Map.of("notificationTo", "dossierField:email"), Map.of("notificationChannels", "EMAIL"))))
                .isInstanceOf(NotificationValidationException.class);
        org.mockito.Mockito.verifyNoInteractions(resolver, useCase);
    }

    /**
     * Revue adversariale TODO §93 (L3) : la variable notificationTo (démarrage, complétion de tâche : saisie utilisateur) est
     * soumise au même contrôle « une seule adresse » qu'une source ; l'attribut littéral écrit par le concepteur reste libre.
     */
    @Test
    void notification_theUserControlledVariableMustAlsoBeASingleMailbox() {
        SendNotificationUseCase useCase = mock(SendNotificationUseCase.class);
        NotificationDispatchTask delegate = new NotificationDispatchTask(
                new TenantProperties("tenant_dev"), useCase, mock(PortalRecipientResolverPort.class),
                mock(com.flowforge.domain.notification.port.in.ResolveEmailAttachmentUseCase.class));

        for (String bad : List.of("Camille <x@evil.org>", "g: a@x.org, b@y.org;", "a@x.org, b@y.org")) {
            assertThatThrownBy(() -> delegate.execute(
                    execution(Map.of("notificationTo", bad), Map.of("notificationChannels", "EMAIL"))))
                    .as(bad)
                    .isInstanceOf(NotificationValidationException.class)
                    .hasMessageNotContaining("x.org");
        }
        delegate.execute(execution(Map.of("notificationTo", " a@example.org "), Map.of("notificationChannels", "EMAIL")));
        delegate.execute(execution(Map.of(), Map.of("notificationChannels", "EMAIL", "notificationTo", "Service <s@example.org>")));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(useCase, org.mockito.Mockito.times(2)).send(captor.capture(), eq("tenant_dev"));
        assertThat(captor.getAllValues()).extracting(NotificationEvent::to)
                .containsExactly("a@example.org", "Service <s@example.org>");
    }

    @Test
    void notification_aMalformedDossierIdIsAValidationErrorWithoutTheValue() {
        NotificationDispatchTask delegate = new NotificationDispatchTask(new TenantProperties("tenant_dev"),
                mock(SendNotificationUseCase.class), mock(PortalRecipientResolverPort.class),
                mock(com.flowforge.domain.notification.port.in.ResolveEmailAttachmentUseCase.class));

        assertThatThrownBy(() -> delegate.execute(execution(Map.of("dossierId", "pas-un-uuid"),
                Map.of("notificationChannels", "EMAIL", "notificationTo", "dossierField:email"))))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageNotContaining("pas-un-uuid");
    }

    @Test
    void notification_aLiteralAddressIsUnchangedAndNeverResolved() {
        SendNotificationUseCase useCase = mock(SendNotificationUseCase.class);
        PortalRecipientResolverPort resolver = mock(PortalRecipientResolverPort.class);
        NotificationDispatchTask delegate = new NotificationDispatchTask(new TenantProperties("tenant_dev"), useCase, resolver,
                mock(com.flowforge.domain.notification.port.in.ResolveEmailAttachmentUseCase.class));

        delegate.execute(execution(Map.of(), Map.of("notificationChannels", "EMAIL", "notificationTo", "contact@example.org")));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(useCase).send(captor.capture(), eq("tenant_dev"));
        assertThat(captor.getValue().to()).isEqualTo("contact@example.org");
        org.mockito.Mockito.verifyNoInteractions(resolver);
    }

    @Test
    void notification_attributesOnly_attributeWins_andMissingChannelsKeepsTheError() {
        SendNotificationUseCase useCase = mock(SendNotificationUseCase.class);
        NotificationDispatchTask delegate = new NotificationDispatchTask(
                new TenantProperties("tenant_dev"), useCase, mock(PortalRecipientResolverPort.class),
                mock(com.flowforge.domain.notification.port.in.ResolveEmailAttachmentUseCase.class));
        UUID webhook = UUID.randomUUID();

        delegate.execute(execution(Map.of(), Map.of(
                "notificationChannels", "EMAIL, BROWSER",
                "notificationTo", "a@example.org",
                "notificationSubject", "Objet",
                "notificationBody", "Corps",
                "notificationUserId", "u1",
                "notificationWebhookConfigId", webhook.toString())));
        delegate.execute(execution(Map.of("notificationSubject", "Variable"),
                Map.of("notificationChannels", "EMAIL", "notificationSubject", "Attribut")));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(useCase, org.mockito.Mockito.times(2)).send(captor.capture(), eq("tenant_dev"));
        NotificationEvent first = captor.getAllValues().get(0);
        assertThat(first.channels()).containsExactly(NotificationChannel.EMAIL, NotificationChannel.BROWSER);
        assertThat(first.to()).isEqualTo("a@example.org");
        assertThat(first.subject()).isEqualTo("Objet");
        assertThat(first.body()).isEqualTo("Corps");
        assertThat(first.userId()).isEqualTo("u1");
        assertThat(first.webhookConfigId()).isEqualTo(webhook);
        assertThat(captor.getAllValues().get(1).subject()).isEqualTo("Attribut");
        assertThatThrownBy(() -> delegate.execute(execution(Map.of(), Map.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("notificationChannels");
    }

    // --- documentGenerationTask --------------------------------------------------------------------------------

    @Test
    void documentGeneration_attributesOnly_attributeWins_andMissingKeepsTheError() {
        GenerateDocumentUseCase generate = mock(GenerateDocumentUseCase.class);
        DocumentStoragePort storage = mock(DocumentStoragePort.class);
        when(generate.generate(any())).thenReturn("%PDF".getBytes());
        when(storage.store(any(), any(), any())).thenReturn("ref");
        DocumentGenerationTask delegate =
                new DocumentGenerationTask(new TenantProperties("tenant_dev"), generate, storage);

        delegate.execute(execution(Map.of(), Map.of(
                "documentType", "facture", "documentVariant", "premium", "documentLocale", "fr")));
        delegate.execute(execution(Map.of("documentType", "devis"), Map.of("documentType", "facture")));

        ArgumentCaptor<GenerateDocumentUseCase.GenerateDocumentCommand> captor =
                ArgumentCaptor.forClass(GenerateDocumentUseCase.GenerateDocumentCommand.class);
        verify(generate, org.mockito.Mockito.times(2)).generate(captor.capture());
        assertThat(captor.getAllValues().get(0).templateKey()).isEqualTo("facture");
        assertThat(captor.getAllValues().get(0).variantKey()).isEqualTo("premium");
        assertThat(captor.getAllValues().get(0).locale()).isEqualTo("fr");
        assertThat(captor.getAllValues().get(1).templateKey()).isEqualTo("facture");
        assertThatThrownBy(() -> delegate.execute(execution(Map.of(), Map.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("documentType");
    }

    // --- createChildDossierTask --------------------------------------------------------------------------------

    @Test
    void createChild_attributesOnly_attributeWins_andMissingKeepsTheError() {
        ChildDossierRelationConfirmer confirmer = mock(ChildDossierRelationConfirmer.class);
        ManualStartOrchestrator orchestrator = mock(ManualStartOrchestrator.class);
        DossierRepository dossiers = mock(DossierRepository.class);
        CreateChildDossierDelegate delegate = new CreateChildDossierDelegate(
                confirmer, orchestrator, dossiers, mock(UpdateDossierStatusUseCase.class),
                new TenantProperties("tenant_dev"));
        UUID source = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        when(confirmer.confirm(any(), any(), any(), any(), any(), any(), any())).thenReturn(child);
        Map<String, Object> dossierVar = Map.of("dossierId", source.toString());

        delegate.execute(execution(dossierVar, Map.of(
                "childDossierTypeCode", "ENFANT", "childDossierLabel", "Suite", "workflowProcessKey", "suiteProcess",
                "relationType", "SUBSEQUENT")));
        Map<String, Object> withVariable = new HashMap<>(dossierVar);
        withVariable.put("childDossierLabel", "Depuis variable");
        delegate.execute(execution(withVariable, Map.of(
                "childDossierTypeCode", "ENFANT", "childDossierLabel", "Suite", "workflowProcessKey", "suiteProcess",
                "relationType", "PARENT_CHILD")));

        verify(confirmer).confirm(eq("tenant_dev"), eq(source), eq("ENFANT"), eq("Suite"), any(),
                eq(RelationType.SUBSEQUENT), eq("pi-1:act"));
        verify(confirmer).confirm(eq("tenant_dev"), eq(source), eq("ENFANT"), eq("Suite"), any(),
                eq(RelationType.PARENT_CHILD), eq("pi-1:act"));
        verify(orchestrator, org.mockito.Mockito.times(2)).start(eq(child), eq("suiteProcess"), any(), any(), any());
        assertThatThrownBy(() -> delegate.execute(execution(dossierVar, Map.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("childDossierTypeCode");
        verify(confirmer, never()).confirm(any(), any(), eq("MISSING"), any(), any(), any(), any());
    }
}
