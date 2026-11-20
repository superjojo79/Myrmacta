package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry;
import be.myrmacta.domain.ebox.port.out.EboxSendAuditTrail;
import be.myrmacta.domain.workflow.model.UnknownBlockReferenceException;
import be.myrmacta.domain.workflow.port.in.DeployBpmnProcessUseCase;
import be.myrmacta.infrastructure.connector.builtin.FakeEboxSenderServer;
import be.myrmacta.support.OidcClientTestConfiguration;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.job.api.Job;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * TODO §155 (lot 2, revue H2 et B2) — brique « Envoyer en eBox » sur le moteur réel, voie entreprise contre le faux serveur
 * eBox : boucle sur plusieurs destinataires (sous-processus multi-instance, passerelle à l'intérieur) et rejeu d'un job qui
 * échoue après la publication.
 */
@SpringBootTest(properties = "flowable.async-executor-activate=false")
@Import(OidcClientTestConfiguration.class)
class EboxSendLoopAndReplayIT {

    private static final String TENANT = "tenant_dev";
    private static final String WITH_EBOX = "0212345678";
    private static final String WITHOUT_EBOX = "0312345678";
    private static final byte[] PDF = "%PDF-1.7\n% décision\n".getBytes(StandardCharsets.UTF_8);

    private static final String SEND_ATTRIBUTES = """
                 flowable:delegateExpression="${eboxSendTask}"
                 flowable:connectorInstanceId="__INSTANCE__"
                 flowable:eboxEnvoiTypeDestinataire="ENTREPRISE"
                 flowable:eboxEnvoiDestinataire="variable:bce"
                 flowable:eboxEnvoiDocument="variable:generatedDocumentId"
                 flowable:eboxEnvoiObjet="Votre permis"
                 flowable:eboxEnvoiApplication="myrmacta-commune"
                 flowable:eboxEnvoiTypeMessage="decision"
                 flowable:eboxEnvoiLangue="fr"
                 flowable:eboxEnvoiSecours="PAPER\"""";

    private static final String LOOP = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://myrmacta/test">
              <process id="eboxSendLoop" name="Envoi eBox en boucle" isExecutable="true">
                <startEvent id="lStart"/>
                <sequenceFlow id="lF1" sourceRef="lStart" targetRef="lWait"/>
                <userTask id="lWait" flowable:assignee="ebox-smoke"/>
                <sequenceFlow id="lF2" sourceRef="lWait" targetRef="lAttach"/>
                <serviceTask id="lAttach" flowable:delegateExpression="${attachGeneratedDocumentTask}"/>
                <sequenceFlow id="lF3" sourceRef="lAttach" targetRef="lEach"/>
                <subProcess id="lEach">
                  <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="${bces}"
                                                    flowable:elementVariable="bce"/>
                  <startEvent id="lEachStart"/>
                  <sequenceFlow id="lE1" sourceRef="lEachStart" targetRef="lSend"/>
                  <serviceTask id="lSend" name="Envoyer en eBox" flowable:async="true"
            SEND/>
                  <sequenceFlow id="lE2" sourceRef="lSend" targetRef="lGateway"/>
                  <exclusiveGateway id="lGateway" default="lEOk"/>
                  <sequenceFlow id="lEFallback" sourceRef="lGateway" targetRef="lFallback">
                    <conditionExpression>${eboxSecoursRequis == true}</conditionExpression>
                  </sequenceFlow>
                  <sequenceFlow id="lEOk" sourceRef="lGateway" targetRef="lEachEnd"/>
                  <userTask id="lFallback" name="Envoyer par la voie de secours" flowable:assignee="ebox-smoke"/>
                  <sequenceFlow id="lE3" sourceRef="lFallback" targetRef="lEachEnd"/>
                  <endEvent id="lEachEnd"/>
                </subProcess>
                <sequenceFlow id="lF4" sourceRef="lEach" targetRef="lDone"/>
                <userTask id="lDone" flowable:assignee="ebox-smoke"/>
              </process>
            </definitions>
            """.replace("SEND", SEND_ATTRIBUTES);

    /** La passerelle sans sortie par défaut (la seconde ne s'ouvre jamais) n'accepte qu'un rejeu : la première tentative échoue après la publication. */
    private static final String REPLAY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://myrmacta/test">
              <process id="eboxSendReplay" name="Envoi eBox rejoué" isExecutable="true">
                <startEvent id="rStart"/>
                <sequenceFlow id="rF1" sourceRef="rStart" targetRef="rWait"/>
                <userTask id="rWait" flowable:assignee="ebox-smoke"/>
                <sequenceFlow id="rF2" sourceRef="rWait" targetRef="rAttach"/>
                <serviceTask id="rAttach" flowable:delegateExpression="${attachGeneratedDocumentTask}"/>
                <sequenceFlow id="rF3" sourceRef="rAttach" targetRef="rSend"/>
                <serviceTask id="rSend" name="Envoyer en eBox" flowable:async="true" flowable:eboxEnvoiRecommande="OUI"
            SEND/>
                <sequenceFlow id="rF4" sourceRef="rSend" targetRef="rCheck"/>
                <exclusiveGateway id="rCheck"/>
                <sequenceFlow id="rF5" sourceRef="rCheck" targetRef="rDone">
                  <conditionExpression>${eboxEnvoiRejoue == true}</conditionExpression>
                </sequenceFlow>
                <sequenceFlow id="rF6" sourceRef="rCheck" targetRef="rNever">
                  <conditionExpression>${eboxIssue == 'JAMAIS'}</conditionExpression>
                </sequenceFlow>
                <userTask id="rDone" flowable:assignee="ebox-smoke"/>
                <userTask id="rNever" flowable:assignee="ebox-smoke"/>
              </process>
            </definitions>
            """.replace("SEND", SEND_ATTRIBUTES);

    @Autowired private RepositoryService repositoryService;
    @Autowired private RuntimeService runtimeService;
    @Autowired private TaskService taskService;
    @Autowired private ManagementService managementService;
    @Autowired private DossierRepository dossiers;
    @Autowired private DossierElementRepository elements;
    @Autowired private DocumentStoragePort storage;
    @Autowired private ConnectorInstanceRepository instances;
    @Autowired private EboxSendAuditTrail audit;
    @Autowired private EntityManager entityManager;

    @Autowired private DeployBpmnProcessUseCase deployUseCase;

    private FakeEboxSenderServer ebox;
    private UUID instanceId;
    private final List<String> deployments = new ArrayList<>();

    @BeforeEach
    void start() throws Exception {
        ebox = new FakeEboxSenderServer();
        ebox.preferences.put(WITH_EBOX, true);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        Map<String, Object> config = new HashMap<>();
        config.put("publicationUrl", ebox.publicationUrl());
        config.put("federationUrl", ebox.federationUrl());
        config.put("registeredMailUrl", ebox.registeredMailUrl());
        config.put("oauthTokenUrl", ebox.tokenUrl());
        config.put("oauthClientId", "myrmacta-commune");
        config.put("oauthPrivateKey", "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        config.put("oauthCertThumbprint", "a".repeat(40));
        instanceId = UUID.randomUUID();
        instances.save(new ConnectorInstanceData(instanceId, TENANT, "eBox envoi " + instanceId, "ebox-outbound", true, config));
        for (String xml : List.of(LOOP, REPLAY)) {
            deployments.add(repositoryService.createDeployment().name("ebox-send-loop")
                    .addString("ebox-send-" + deployments.size() + ".bpmn20.xml",
                            xml.replace("__INSTANCE__", instanceId.toString()))
                    .deploy().getId());
        }
    }

    @AfterEach
    void stop() {
        deployments.forEach(id -> repositoryService.deleteDeployment(id, true));
        ebox.close();
    }

    /** Revue H2 : chaque tour a sa clé et son issue ; la passerelle du tour lit la sienne. */
    @Test
    void aMultiInstanceSubProcessSendsToEachRecipientAndTakesTheFallbackForTheRightOne() {
        Dossier dossier = dossier();
        String ref = storage.store(TENANT, "application/pdf", PDF);
        String pi = runtimeService.startProcessInstanceByKey("eboxSendLoop", Map.of("dossierId", dossier.id().toString(),
                "generatedDocumentId", ref, "bces", new ArrayList<>(List.of(WITH_EBOX, WITHOUT_EBOX)))).getId();
        complete(pi, "lWait");

        List<Job> jobs = managementService.createJobQuery().processInstanceId(pi).list();
        assertThat(jobs).hasSize(2);
        jobs.forEach(job -> managementService.executeJob(job.getId()));

        String published = EboxSendDelegate.idempotencyKey(dossier.id(), "lSend", ref, 0, WITH_EBOX);
        String fallback = EboxSendDelegate.idempotencyKey(dossier.id(), "lSend", ref, 1, WITHOUT_EBOX);
        assertThat(published).isNotEqualTo(fallback);
        assertThat(audit.last(TENANT, published, new EboxRecipient.Enterprise(WITH_EBOX)).orElseThrow().outcome())
                .isEqualTo(EboxSendAuditEntry.Outcome.PUBLISHED);
        assertThat(audit.last(TENANT, fallback, new EboxRecipient.Enterprise(WITHOUT_EBOX)).orElseThrow().outcome())
                .isEqualTo(EboxSendAuditEntry.Outcome.FALLBACK);
        assertThat(ebox.requestsTo("/ebox/messages")).singleElement()
                .satisfies(request -> assertThat(request.headers()).containsEntry("idempotency-key", published));

        Task task = taskService.createTaskQuery().processInstanceId(pi).taskDefinitionKey("lFallback").singleResult();
        assertThat(runtimeService.getVariable(task.getExecutionId(), "bce")).isEqualTo(WITHOUT_EBOX);
        assertThat(runtimeService.getVariable(task.getExecutionId(), "eboxIssue")).isEqualTo("VOIE_SECOURS");
        assertThat(runtimeService.getVariableLocal(pi, "eboxIssue")).as("the outcome stays in its turn").isNull();
        taskService.complete(task.getId());
        assertThat(taskService.createTaskQuery().processInstanceId(pi).singleResult().getTaskDefinitionKey()).isEqualTo("lDone");
    }

    /** Revue B2 et B1 : le job échoue après la publication ; la nouvelle tentative ne republie pas et verse la preuve une fois. */
    @Test
    void aJobThatFailsAfterThePublicationIsRetriedWithoutASecondPublication() {
        Dossier dossier = dossier();
        String ref = storage.store(TENANT, "application/pdf", PDF);
        String pi = runtimeService.startProcessInstanceByKey("eboxSendReplay", Map.of("dossierId", dossier.id().toString(),
                "generatedDocumentId", ref, "bce", WITH_EBOX)).getId();
        complete(pi, "rWait");

        String jobId = managementService.createJobQuery().processInstanceId(pi).singleResult().getId();
        assertThatThrownBy(() -> managementService.executeJob(jobId)).isInstanceOf(FlowableException.class);
        assertThat(ebox.requestsTo("/registeredMails")).hasSize(1);
        assertThat(proofs(dossier)).as("the proof rolled back with the failed job").isEmpty();

        Job retry = managementService.createTimerJobQuery().processInstanceId(pi).singleResult();
        managementService.moveTimerToExecutableJob(retry.getId());
        managementService.executeJob(retry.getId());

        String key = EboxSendDelegate.idempotencyKey(dossier.id(), "rSend", ref, null, WITH_EBOX);
        assertThat(ebox.requestsTo("/registeredMails")).as("published once").hasSize(1);
        assertThat(entityManager.createQuery("select count(e) from EboxSendAuditJpaEntity e where e.tenantId = :tenant "
                        + "and e.idempotencyKey = :key and e.outcome = 'PUBLISHED'", Long.class)
                .setParameter("tenant", TENANT).setParameter("key", key).getSingleResult()).isEqualTo(1L);
        assertThat(runtimeService.getVariable(pi, "eboxEnvoiRejoue")).isEqualTo(true);
        assertThat(runtimeService.getVariable(pi, "eboxIssue")).isEqualTo("ENVOYE");
        assertThat(proofs(dossier)).singleElement()
                .satisfies(proof -> assertThat(proof.metadata()).containsEntry("eboxIdempotencyKey", key));
        assertThat(taskService.createTaskQuery().processInstanceId(pi).singleResult().getTaskDefinitionKey()).isEqualTo("rDone");
    }

    /**
     * Revue H1 : « Appeler un connecteur » avec une requête littérale ne vise jamais un compte expéditeur eBox (refus au
     * déploiement) ; la brique « Envoyer en eBox », elle, le choisit.
     */
    @Test
    void aConnectorCallStepCannotTargetAnEboxSenderButTheEboxSendBlockCan() {
        String call = single("<serviceTask id=\"call\" flowable:async=\"true\" flowable:delegateExpression=\"${connectorServiceTask}\" "
                + "flowable:connectorInstanceId=\"" + instanceId + "\" flowable:connectorRequest=\"{'path': '/x'}\"/>");
        assertThatThrownBy(() -> deployUseCase.validate(call.getBytes(StandardCharsets.UTF_8), Optional.empty()))
                .isInstanceOf(UnknownBlockReferenceException.class).hasMessageContaining("connectorInstanceId");

        String send = single("<serviceTask id=\"send\" flowable:async=\"true\" "
                + SEND_ATTRIBUTES.replace("__INSTANCE__", instanceId.toString()) + "/>");
        deployUseCase.validate(send.getBytes(StandardCharsets.UTF_8), Optional.empty());
    }

    private static String single(String task) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="eboxSendRefusal" isExecutable="true">
                    <startEvent id="s"/>
                    <sequenceFlow id="f1" sourceRef="s" targetRef="TASK_ID"/>
                    TASK
                    <sequenceFlow id="f2" sourceRef="TASK_ID" targetRef="e"/>
                    <endEvent id="e"/>
                  </process>
                </definitions>
                """.replace("TASK_ID", task.contains("id=\"call\"") ? "call" : "send").replace("TASK", task);
    }

    private List<DossierElement> proofs(Dossier dossier) {
        return elements.findByDossierIdOrderByAddedAtAsc(dossier.id()).stream()
                .filter(e -> e.type() == ElementType.ATTACHMENT && e.label().startsWith("Preuve de dépôt eBox")).toList();
    }

    private Dossier dossier() {
        return dossiers.save(new Dossier(UUID.randomUUID(), TENANT, "REF-" + System.nanoTime(), "DEMANDE", "Permis",
                DossierStatus.OUVERT, Map.of(), null, 0L, "test", Instant.now(), null, null, null, null, null));
    }

    private void complete(String pi, String taskKey) {
        taskService.complete(taskService.createTaskQuery().processInstanceId(pi).taskDefinitionKey(taskKey).singleResult().getId());
    }
}
