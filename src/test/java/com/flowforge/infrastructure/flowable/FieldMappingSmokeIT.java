package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * D3b (TODO §78) — la brique « Enregistrer des valeurs dans le dossier » sur le vrai moteur, le vrai use case de mise à jour
 * des champs (validation, normalisation, version) et un vrai dossier : un résultat de connecteur enrichit le dossier.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class FieldMappingSmokeIT {

    private static final String TYPE = "MAPSMOKE";
    private static final String KEY = "fieldMappingSmoke";

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired
    private BpmnParserPort parser;

    @Autowired
    private DossierRepository dossiers;

    @Autowired
    private DossierTypeRepository dossierTypes;

    @BeforeEach
    void typeExists() {
        if (dossierTypes.findByCode("tenant_dev", TYPE).isEmpty()) {
            dossierTypes.save(new DossierType(UUID.randomUUID(), "tenant_dev", TYPE, "Smoke mapping", "wf", true, false,
                    List.of(new FieldDefinition("score", "Score", FieldType.NUMBER, false, null, null, null, false),
                            new FieldDefinition("total", "Total", FieldType.MONEY, false, null, List.of("EUR"), null, false),
                            new FieldDefinition("ville", "Ville", FieldType.STRING, false, null, null, null, false),
                            new FieldDefinition("iban", "IBAN", FieldType.STRING, false, null, null, null, true)),
                    null, false, null, "test", "test"));
        }
    }

    private String deploy(String mapping) {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://flowforge/test">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow sourceRef="start" targetRef="map"/>
                    <serviceTask id="map" flowable:delegateExpression="${fieldMappingTask}" flowable:fieldMapping="%s"/>
                    <sequenceFlow sourceRef="map" targetRef="wait"/>
                    <userTask id="wait" name="Attente"/>
                    <sequenceFlow sourceRef="wait" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(KEY, mapping.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\n", "&#10;"));
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        parser.extractProcessDefinitionKey(bytes); // la même validation que le déploiement réel
        return repositoryService.createDeployment().tenantId(tenantProperties.id())
                .addBytes(KEY + ".bpmn20.xml", bytes).deploy().getId();
    }

    private Dossier dossier() {
        return dossiers.save(new Dossier(UUID.randomUUID(), "tenant_dev", "MAP-" + System.nanoTime(), TYPE, "Dossier",
                DossierStatus.OUVERT, Map.of("ville", "Paris"), null, 0L, "test", Instant.now(), null, null, null, null, null));
    }

    private void run(Dossier dossier, Object connectorResult) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("dossierId", dossier.id().toString());
        variables.put("connectorResult", connectorResult);
        runtimeService.startProcessInstanceByKeyAndTenantId(KEY, "map-" + UUID.randomUUID(), variables, tenantProperties.id());
    }

    @Test
    void aConnectorResultEnrichesTheDossierThroughTheBusinessPathInASingleVersionStep() {
        String deployment = deploy("score = connectorResult.json.score\n"
                + "total = {\"amount\": $string(connectorResult.json.total), \"currency\": \"EUR\"}\n"
                + "ville = $uppercase(connectorResult.json.city)");
        try {
            Dossier dossier = dossier();
            run(dossier, Map.of("status", 200, "body", "{\"score\": 87, \"total\": 1234.5, \"city\": \"lyon\"}"));

            Dossier after = dossiers.findById(dossier.id()).orElseThrow();
            assertThat(after.fields()).containsEntry("ville", "LYON");
            assertThat(((Number) after.fields().get("score")).intValue()).isEqualTo(87);
            assertThat(after.fields().get("total")).isEqualTo(Map.of("amount", "1234.50", "currency", "EUR"));
            assertThat(after.version()).as("un seul patch : une seule étape de version").isEqualTo(dossier.version() + 1);
        } finally {
            repositoryService.deleteDeployment(deployment, true);
        }
    }

    @Test
    void anInvalidValueFailsTheActivityAndNothingIsWritten() {
        String deployment = deploy("ville = \"Marseille\"\nscore = connectorResult.json.score");
        try {
            Dossier dossier = dossier();
            assertThatThrownBy(() -> run(dossier, Map.of("status", 200, "body", "{\"score\": \"pas un nombre\"}")))
                    .hasMessageContaining("score");

            assertThat(dossiers.findById(dossier.id()).orElseThrow().fields())
                    .as("tout ou rien : ville n'est pas écrite").containsEntry("ville", "Paris").doesNotContainKey("score");
        } finally {
            repositoryService.deleteDeployment(deployment, true);
        }
    }

    @Test
    void aMoneyAmountBeyondTheCurrencyScaleIsRefusedNotRounded() {
        String deployment = deploy("total = {\"amount\": \"10.123\", \"currency\": \"EUR\"}");
        try {
            Dossier dossier = dossier();
            assertThatThrownBy(() -> run(dossier, Map.of("status", 200, "body", "{}"))).hasMessageContaining("total");
            assertThat(dossiers.findById(dossier.id()).orElseThrow().fields()).doesNotContainKey("total");
        } finally {
            repositoryService.deleteDeployment(deployment, true);
        }
    }

    @Test
    void aSensitiveFieldCanBeATargetButNeverASource() {
        String deployment = deploy("iban = connectorResult.json.iban");
        try {
            Dossier dossier = dossier();
            run(dossier, Map.of("status", 200, "body", "{\"iban\": \"FR7630001007941234567890185\"}"));
            assertThat(dossiers.findById(dossier.id()).orElseThrow().fields()).containsEntry("iban", "FR7630001007941234567890185");
        } finally {
            repositoryService.deleteDeployment(deployment, true);
        }
        String leak = deploy("ville = dossier.fields.iban");
        try {
            Dossier dossier = dossier();
            assertThatThrownBy(() -> run(dossier, Map.of("status", 200, "body", "{}"))).hasMessageContaining("no value");
        } finally {
            repositoryService.deleteDeployment(leak, true);
        }
    }
}
