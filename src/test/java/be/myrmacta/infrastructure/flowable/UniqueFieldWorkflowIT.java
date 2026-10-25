package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * TODO §102 (issue #184) — une écriture par workflow (brique « Enregistrer des valeurs dans le dossier ») qui créerait un
 * doublon échoue comme une erreur de validation : l'activité échoue, le message nomme le champ et le dossier détenteur, rien
 * n'est écrit.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class UniqueFieldWorkflowIT {

    private static final String KEY = "uniqueFieldMapping";

    @Autowired private RepositoryService repositoryService;
    @Autowired private RuntimeService runtimeService;
    @Autowired private TenantProperties tenantProperties;
    @Autowired private BpmnParserPort parser;
    @Autowired private DossierRepository dossiers;
    @Autowired private DossierTypeRepository dossierTypes;

    @Test
    void aFieldMappingThatWouldCreateADuplicateFailsAndWritesNothing() {
        String type = "UNQWF" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        dossierTypes.save(DossierType.inherit(UUID.randomUUID(), "tenant_dev", type, "Unique wf", "wf", true, false, List.of(
                new FieldDefinition("plaque", "Plaque", FieldType.STRING, false, null, null, null, false, null, null, null,
                        null, null, null, false, null, null, true),
                new FieldDefinition("ville", "Ville", FieldType.STRING, false, null, null, null))));
        Dossier holder = save(type, Map.of("plaque", "BE-1"));
        Dossier target = save(type, Map.of("plaque", "BE-2", "ville", "Paris"));

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://flowforge/test">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow sourceRef="start" targetRef="map"/>
                    <serviceTask id="map" flowable:delegateExpression="${fieldMappingTask}"
                                 flowable:fieldMapping="ville = &quot;Lyon&quot;&#10;plaque = &quot;BE-1&quot;"/>
                    <sequenceFlow sourceRef="map" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(KEY);
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        parser.extractProcessDefinitionKey(bytes);
        String deployment = repositoryService.createDeployment().tenantId(tenantProperties.id())
                .addBytes(KEY + ".bpmn20.xml", bytes).deploy().getId();
        try {
            assertThatThrownBy(() -> runtimeService.startProcessInstanceByKeyAndTenantId(KEY, "u-" + UUID.randomUUID(),
                    Map.of("dossierId", target.id().toString()), tenantProperties.id()))
                    .hasMessageContaining("plaque (UNIQUE")
                    .hasMessageContaining(holder.reference())
                    .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("BE-1"));

            assertThat(dossiers.findById(target.id()).orElseThrow().fields())
                    .as("tout ou rien").containsEntry("ville", "Paris").containsEntry("plaque", "BE-2");
        } finally {
            repositoryService.deleteDeployment(deployment, true);
        }
    }

    private Dossier save(String type, Map<String, Object> fields) {
        return dossiers.save(new Dossier(UUID.randomUUID(), "tenant_dev", type + "-" + System.nanoTime(), type, "D",
                DossierStatus.OUVERT, fields, null, 0L, "test", Instant.now(), null, null, null, null, null));
    }
}
