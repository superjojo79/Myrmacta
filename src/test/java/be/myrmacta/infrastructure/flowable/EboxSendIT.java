package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry;
import be.myrmacta.domain.ebox.port.out.EboxSendAuditTrail;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * TODO §155 (lot 2) — chaîne complète de la brique « Envoyer en eBox » : délégué, connecteur {@code ebox-outbound}, cas d'usage,
 * voie citoyen simulée (sans consentement par défaut), journal d'audit PostgreSQL, passerelle vers la voie de secours, rejeu de
 * l'étape sans second envoi.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class EboxSendIT {

    private static final String TENANT = "tenant_dev";
    private static final String NISS = "85073003328";
    private static final byte[] PDF = "%PDF-1.7\n% décision\n".getBytes(StandardCharsets.UTF_8);

    @Autowired private RepositoryService repositoryService;
    @Autowired private RuntimeService runtimeService;
    @Autowired private TaskService taskService;
    @Autowired private DossierRepository dossiers;
    @Autowired private DocumentStoragePort storage;
    @Autowired private ConnectorInstanceRepository instances;
    @Autowired private EboxSendAuditTrail audit;

    private String deploymentId;

    @BeforeEach
    void deploy() throws IOException {
        UUID instanceId = UUID.randomUUID();
        instances.save(new ConnectorInstanceData(instanceId, TENANT, "eBox envoi " + instanceId, "ebox-outbound", true, Map.of()));
        String xml;
        try (InputStream in = getClass().getResourceAsStream("/palette/ebox-send.bpmn20.xml")) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("__INSTANCE__", instanceId.toString());
        }
        deploymentId = repositoryService.createDeployment().name("ebox-send").addString("ebox-send.bpmn20.xml", xml).deploy().getId();
    }

    @AfterEach
    void undeploy() {
        repositoryService.deleteDeployment(deploymentId, true);
    }

    @Test
    void aCitizenWithoutConsentSendsTheProcessToTheFallback_andAReplayNeverSendsTwice() {
        Dossier dossier = dossiers.save(new Dossier(UUID.randomUUID(), TENANT, "REF-" + System.nanoTime(), "DEMANDE", "Permis",
                DossierStatus.OUVERT, Map.of("niss", NISS, "langue", "fr"), null, 0L, "test", Instant.now(), null, null, null, null,
                null));
        String ref = storage.store(TENANT, "application/pdf", PDF);

        String first = run(dossier, ref);

        assertThat(taskService.createTaskQuery().processInstanceId(first).singleResult().getTaskDefinitionKey())
                .isEqualTo("esFallback");
        Map<String, Object> variables = runtimeService.getVariables(first);
        assertThat(variables).containsEntry("eboxIssue", "VOIE_SECOURS").containsEntry("eboxLivraison", "FALLBACK")
                .containsEntry("eboxSecoursRequis", true).containsEntry("eboxVoieSecours", "EMAIL")
                .containsEntry("eboxEnvoiMessageId", "").containsEntry("eboxEnvoiRejoue", false);
        String key = EboxSendDelegate.idempotencyKey(dossier.id(), "esSend", ref, null);
        EboxSendAuditEntry.Summary logged = audit.last(TENANT, key, new EboxRecipient.Citizen(NISS)).orElseThrow();
        assertThat(logged.outcome()).isEqualTo(EboxSendAuditEntry.Outcome.FALLBACK);

        String second = run(dossier, ref);

        assertThat(runtimeService.getVariables(second)).containsEntry("eboxIssue", "VOIE_SECOURS")
                .containsEntry("eboxEnvoiRejoue", true);
    }

    private String run(Dossier dossier, String ref) {
        String pi = runtimeService.startProcessInstanceByKey("eboxSend",
                Map.of("dossierId", dossier.id().toString(), "generatedDocumentId", ref)).getId();
        taskService.complete(taskService.createTaskQuery().processInstanceId(pi).taskDefinitionKey("esWait").singleResult().getId());
        return pi;
    }
}
