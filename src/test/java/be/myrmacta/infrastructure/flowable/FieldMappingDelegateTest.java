package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierFieldsUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.expression.model.ExpressionLimits;
import be.myrmacta.domain.expression.port.out.ExpressionEnginePort;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.expression.JsonataExpressionEngine;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

/** TODO §78 (D3b) — le delegate : évaluation, patch unique par le chemin métier, échec fermé, secret jamais lu. */
class FieldMappingDelegateTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final UUID DOSSIER_ID = UUID.randomUUID();

    private final ExpressionEnginePort engine = new JsonataExpressionEngine(ExpressionLimits.DEFAULTS, Clock.systemUTC());
    private final GetDossierUseCase getDossier = mock(GetDossierUseCase.class);
    private final DossierTypeRepository types = mock(DossierTypeRepository.class);
    private final UpdateDossierFieldsUseCase update = mock(UpdateDossierFieldsUseCase.class);
    private final TenantProperties tenant = new TenantProperties("tenant_dev");

    private FieldMappingDelegate delegate() {
        return new FieldMappingDelegate(engine, getDossier, types, update, tenant, new ObjectMapper());
    }

    private static DelegateExecution execution(String mapping, Map<String, Object> variables) {
        ServiceTask task = new ServiceTask();
        task.setId("map");
        if (mapping != null) {
            ExtensionAttribute attribute = new ExtensionAttribute("fieldMapping");
            attribute.setNamespace(DeployedActivityParameters.FLOWABLE_NAMESPACE);
            attribute.setValue(mapping);
            task.addAttribute(attribute);
        }
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getCurrentFlowElement()).thenReturn(task);
        when(execution.getCurrentActivityId()).thenReturn("map");
        when(execution.getVariables()).thenReturn(variables);
        variables.forEach((k, v) -> when(execution.getVariable(k)).thenReturn(v));
        return execution;
    }

    private static Map<String, Object> variables(Object connectorResult) {
        Map<String, Object> v = new HashMap<>();
        v.put("dossierId", DOSSIER_ID.toString());
        if (connectorResult != null) {
            v.put("connectorResult", connectorResult);
        }
        return v;
    }

    private void givenDossier() {
        Dossier dossier = new Dossier(DOSSIER_ID, "tenant_dev", "DOS-1", "SINISTRE", "Sinistre", DossierStatus.OUVERT,
                Map.of("montant", 6000, "iban", "FR7630001007941234567890185"), null, 1L, "u", NOW, null, NOW, null, null,
                null);
        when(getDossier.get(any(UUID.class), any(AccessContext.class))).thenReturn(dossier);
        DossierType type = mock(DossierType.class);
        when(type.fieldDefinitions()).thenReturn(List.of(
                new FieldDefinition("montant", "Montant", FieldType.NUMBER, false, null, null, null, false),
                new FieldDefinition("iban", "IBAN", FieldType.STRING, false, null, null, null, true),
                new FieldDefinition("score", "Score", FieldType.NUMBER, false, null, null, null, false),
                new FieldDefinition("ville", "Ville", FieldType.STRING, false, null, null, null, false),
                new FieldDefinition("ratio", "Ratio", FieldType.NUMBER, false, null, null, null, false),
                new FieldDefinition("texte", "Texte", FieldType.STRING, false, null, null, null, false),
                new FieldDefinition("statut", "Statut", FieldType.STRING, false, null, null, null, false),
                new FieldDefinition("copie", "Copie", FieldType.STRING, false, null, null, null, false),
                new FieldDefinition("a", "A", FieldType.NUMBER, false, null, null, null, false),
                new FieldDefinition("b", "B", FieldType.NUMBER, false, null, null, null, false)));
        when(types.findByCode("tenant_dev", "SINISTRE")).thenReturn(Optional.of(type));
    }

    private UpdateDossierFieldsUseCase.UpdateDossierFieldsCommand writtenCommand() {
        ArgumentCaptor<UpdateDossierFieldsUseCase.UpdateDossierFieldsCommand> captor =
                ArgumentCaptor.forClass(UpdateDossierFieldsUseCase.UpdateDossierFieldsCommand.class);
        verify(update).update(captor.capture(), any(AccessContext.class));
        assertThat(captor.getValue().dossierId()).isEqualTo(DOSSIER_ID);
        assertThat(captor.getValue().workflowMapping()).isTrue();
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> writtenPatch() {
        return writtenCommand().fieldsPatch();
    }

    @Test
    void mapsTheParsedResponseBodyAndTheDossierIntoASingleBusinessPatch() {
        givenDossier();
        Map<String, Object> result = Map.of("status", 200, "body", "{\"score\": 87, \"details\": {\"city\": \"Lyon\"}}");

        delegate().execute(execution(
                "score = connectorResult.json.score\nville = connectorResult.json.details.city\nratio = connectorResult.json.score / dossier.fields.montant",
                variables(result)));

        Map<String, Object> patch = writtenPatch();
        assertThat(patch).containsEntry("score", 87L).containsEntry("ville", "Lyon").containsKey("ratio");
    }

    @Test
    void anExpressionWithoutAValueFailsNamingTheFieldAndWritesNothing() {
        givenDossier();
        DelegateExecution execution = execution("score = connectorResult.json.absent",
                variables(Map.of("status", 200, "body", "{\"score\": 1}")));

        assertThatThrownBy(() -> delegate().execute(execution))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("'score'").hasMessageContaining("no value");
        verify(update, never()).update(any(), any());
    }

    @Test
    void anExplicitNullFailsToo() {
        givenDossier();
        assertThatThrownBy(() -> delegate().execute(execution("score = connectorResult.json.score",
                variables(Map.of("status", 200, "body", "{\"score\": null}")))))
                .hasMessageContaining("'score'");
        verify(update, never()).update(any(), any());
    }

    @Test
    void allOrNothingWhenALaterFieldFails() {
        givenDossier();
        assertThatThrownBy(() -> delegate().execute(execution("a = 1\nb = connectorResult.json.absent",
                variables(Map.of("status", 200, "body", "{}")))))
                .hasMessageContaining("'b'");
        verify(update, never()).update(any(), any());
    }

    @Test
    void aSensitiveFieldIsNeverReadableByAMappingExpression() {
        givenDossier();
        assertThatThrownBy(() -> delegate().execute(execution("copie = dossier.fields.iban", variables(null))))
                .hasMessageContaining("'copie'").hasMessageContaining("no value");
        verify(update, never()).update(any(), any());
    }

    @Test
    void aNonJsonBodyLeavesTheRawTextAndNoJsonKey() {
        givenDossier();
        assertThatThrownBy(() -> delegate().execute(execution("score = connectorResult.json.score",
                variables(Map.of("status", 200, "body", "<html>nope</html>")))))
                .hasMessageContaining("no value");
        delegate().execute(execution("texte = connectorResult.body", variables(Map.of("status", 200, "body", "<html>nope</html>"))));
        assertThat(writtenPatch()).containsEntry("texte", "<html>nope</html>");
    }

    @Test
    void theMappingIsReadFromTheLiteralAttributeOnlyNeverFromAProcessVariable() {
        givenDossier();
        Map<String, Object> vars = variables(null);
        vars.put("fieldMapping", "score = 1");
        assertThatThrownBy(() -> delegate().execute(execution(null, vars))).hasMessageContaining("literal attribute");
        verify(update, never()).update(any(), any());
    }

    @Test
    void theMappingIsRevalidatedAtExecutionNotOnlyAtDeployment() {
        givenDossier();
        assertThatThrownBy(() -> delegate().execute(execution("pas de signe egal", variables(null))))
                .isInstanceOf(InvalidBpmnException.class);
        verify(update, never()).update(any(), any());
    }

    @Test
    void aMissingDossierFailsExplicitly() {
        Map<String, Object> vars = new HashMap<>();
        assertThatThrownBy(() -> delegate().execute(execution("a = 1", vars)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("dossier");
    }

    @Test
    void anUnknownDossierTypeFailsClosed() {
        Dossier dossier = new Dossier(DOSSIER_ID, "tenant_dev", "DOS-1", "SINISTRE", "S", DossierStatus.OUVERT, Map.of(),
                null, 1L, "u", NOW, null, NOW, null, null, null);
        when(getDossier.get(any(UUID.class), any(AccessContext.class))).thenReturn(dossier);
        when(types.findByCode("tenant_dev", "SINISTRE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> delegate().execute(execution("a = 1", variables(null))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SINISTRE");
        verify(update, never()).update(any(), any());
    }

    @Test
    void anOversizedBodyIsNotParsedButDoesNotBreakTheMapping() {
        givenDossier();
        String huge = "{\"a\":\"" + "x".repeat(BusinessExpressionContext.MAX_PARSED_BODY_CHARS) + "\"}";
        delegate().execute(execution("statut = $string(connectorResult.status)", variables(Map.of("status", 200, "body", huge))));
        assertThat(writtenPatch()).containsEntry("statut", "200");
    }

    @Test
    void aTargetThatTheTypeDoesNotDeclareIsRefusedEvenIfUnknownFieldsAreAllowed() {
        givenDossier();
        DelegateExecution execution = execution("dump = $\nscore = 1", variables(null));
        assertThatThrownBy(() -> delegate().execute(execution))
                .hasMessageContaining("'dump'").hasMessageContaining("not declared");
        verify(update, never()).update(any(), any());
    }

    @Test
    void anEvaluationErrorNamesTheFieldAndTheActivity() {
        givenDossier();
        assertThatThrownBy(() -> delegate().execute(execution("score = 1\nville = 'x' + 1", variables(null))))
                .hasMessageContaining("'ville'").hasMessageContaining("map");
        verify(update, never()).update(any(), any());
    }

    @Test
    void theWriteIsAttributedToTheProcessAndTheActivity() {
        givenDossier();
        DelegateExecution execution = execution("score = 1", variables(null));
        when(execution.getProcessInstanceId()).thenReturn("pi-42");

        delegate().execute(execution);

        ArgumentCaptor<UpdateDossierFieldsUseCase.UpdateDossierFieldsCommand> captor =
                ArgumentCaptor.forClass(UpdateDossierFieldsUseCase.UpdateDossierFieldsCommand.class);
        verify(update).update(captor.capture(), any(AccessContext.class));
        assertThat(captor.getValue().updatedBy()).isEqualTo("workflow:pi-42/map");
    }

    @Test
    void aSensitiveFieldWrittenByTheMappingIsAudited() {
        givenDossier();
        when(update.update(any(), any())).thenReturn(
                new UpdateDossierFieldsUseCase.UpdateDossierFieldsResult(null, List.of("iban")));
        DelegateExecution execution = execution("iban = \"FR7630001007941234567890185\"", variables(null));
        when(execution.getProcessInstanceId()).thenReturn("pi-7");
        ch.qos.logback.classic.Logger audit =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("SECURITY_AUDIT");
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        audit.addAppender(appender);
        try {
            delegate().execute(execution);
        } finally {
            audit.detachAppender(appender);
        }
        assertThat(appender.list).extracting(e -> e.getFormattedMessage())
                .anySatisfy(m -> assertThat(m).contains("sensitive_field_changed", "source=workflow", "pi-7", "map", "field=iban")
                        .doesNotContain("FR7630001007941234567890185"));
    }
}
