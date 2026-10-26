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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * TODO §102 (issue #184, lot B) — brique « Rechercher des dossiers » sur le vrai moteur et une vraie base : nombre et
 * référence du plus ancien dossier ouvert du même type (R3, R4), source variable, champ non cherchable en incident (R5).
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DossierSearchTaskIT {

    private static final String KEY = "dossierSearchSmoke";

    @Autowired private RepositoryService repositoryService;
    @Autowired private RuntimeService runtimeService;
    @Autowired private TenantProperties tenantProperties;
    @Autowired private BpmnParserPort parser;
    @Autowired private DossierRepository dossiers;
    @Autowired private DossierTypeRepository dossierTypes;

    private String type;

    @BeforeEach
    void typeExists() {
        type = "SRCH" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        dossierTypes.save(DossierType.inherit(UUID.randomUUID(), "tenant_dev", type, "Recherche", "wf", true, false, List.of(
                new FieldDefinition("email", "E-mail", FieldType.STRING, false, null, null, null, false, null, null, null,
                        null, null, null, true, null, null, false),
                new FieldDefinition("libre", "Libre", FieldType.STRING, false, null, null, null))));
    }

    private String deploy(String criteria) {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow sourceRef="start" targetRef="search"/>
                    <serviceTask id="search" flowable:delegateExpression="${dossierSearchTask}" flowable:searchCriteria="%s"
                                 flowable:searchCountResult="nbVoisins" flowable:searchFirstReferenceResult="premierVoisin"/>
                    <sequenceFlow sourceRef="search" targetRef="wait"/>
                    <userTask id="wait" name="Attente"/>
                    <sequenceFlow sourceRef="wait" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(KEY, criteria);
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        parser.extractProcessDefinitionKey(bytes); // la même validation que le déploiement réel
        return repositoryService.createDeployment().tenantId(tenantProperties.id())
                .addBytes(KEY + ".bpmn20.xml", bytes).deploy().getId();
    }

    private Dossier dossier(DossierStatus status, Instant createdAt, Map<String, Object> fields) {
        return dossiers.save(new Dossier(UUID.randomUUID(), "tenant_dev", type + "-" + System.nanoTime(), type, "D", status,
                fields, null, 0L, "test", createdAt, null, null, null, null, null));
    }

    private ProcessInstance run(Dossier dossier, Map<String, Object> extra) {
        Map<String, Object> variables = new HashMap<>(extra);
        variables.put("dossierId", dossier.id().toString());
        return runtimeService.startProcessInstanceByKeyAndTenantId(KEY, "s-" + UUID.randomUUID(), variables,
                tenantProperties.id());
    }

    @Test
    void countsTheOpenDossiersOfTheSameTypeWithTheSameValueAndNamesTheOldest() {
        String deployment = deploy("email = dossierField:email");
        try {
            Instant t0 = Instant.parse("2026-02-01T00:00:00Z");
            Dossier oldest = dossier(DossierStatus.EN_COURS, t0, Map.of("email", "a@x.fr"));
            dossier(DossierStatus.DRAFT, t0.plusSeconds(60), Map.of("email", "a@x.fr"));
            dossier(DossierStatus.CLOTURE, t0.minusSeconds(60), Map.of("email", "a@x.fr"));
            Dossier current = dossier(DossierStatus.OUVERT, t0.plusSeconds(120), Map.of("email", "a@x.fr"));

            ProcessInstance instance = run(current, Map.of());

            assertThat(runtimeService.getVariable(instance.getId(), "nbVoisins")).isEqualTo(2L);
            assertThat(runtimeService.getVariable(instance.getId(), "premierVoisin")).isEqualTo(oldest.reference());

            Dossier alone = dossier(DossierStatus.OUVERT, t0, Map.of("email", "seul@x.fr"));
            ProcessInstance none = run(alone, Map.of());
            assertThat(runtimeService.getVariable(none.getId(), "nbVoisins")).isEqualTo(0L);
            assertThat(runtimeService.getVariable(none.getId(), "premierVoisin")).isNull();
        } finally {
            repositoryService.deleteDeployment(deployment, true);
        }
    }

    @Test
    void theSearchedValueCanComeFromAProcessVariable() {
        String deployment = deploy("email = variable:emailSaisi");
        try {
            Dossier other = dossier(DossierStatus.EN_COURS, Instant.now(), Map.of("email", "v@x.fr"));
            Dossier current = dossier(DossierStatus.EN_COURS, Instant.now(), Map.of());

            ProcessInstance instance = run(current, Map.of("emailSaisi", "v@x.fr"));

            assertThat(runtimeService.getVariable(instance.getId(), "nbVoisins")).isEqualTo(1L);
            assertThat(runtimeService.getVariable(instance.getId(), "premierVoisin")).isEqualTo(other.reference());
        } finally {
            repositoryService.deleteDeployment(deployment, true);
        }
    }

    @Test
    void aFieldThatIsNotSearchableFailsTheActivity() {
        String deployment = deploy("libre = dossierField:libre");
        try {
            Dossier current = dossier(DossierStatus.EN_COURS, Instant.now(), Map.of("libre", "x"));
            assertThatThrownBy(() -> run(current, Map.of())).hasMessageContaining("not searchable");
        } finally {
            repositoryService.deleteDeployment(deployment, true);
        }
    }

    @Test
    void aMalformedBlockIsRefusedAtDeployment() {
        assertThatThrownBy(() -> deploy("email = &quot;a@x.fr&quot;")).hasMessageContaining("dossierField:");
    }
}
