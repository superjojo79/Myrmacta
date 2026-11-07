package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.InboundAttachment;
import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.domain.connector.port.out.SftpClientPort;
import be.myrmacta.domain.dossier.port.out.AttachmentScanPort;
import be.myrmacta.domain.dossier.port.out.AttachmentScanResult;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.user.port.in.RecordUserLoginUseCase;
import be.myrmacta.infrastructure.deadline.DeadlineAlertJob;
import be.myrmacta.infrastructure.mcp.McpActorScope;
import be.myrmacta.infrastructure.mcp.tool.MailRoomMcpTools;
import be.myrmacta.support.OidcClientTestConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * TODO §113 (issue #217, lots 1 et 2b) — le paquet {@code exemples/digital-mail-room} sur l'application : un e-mail lu par
 * le connecteur IMAP de l'exemple devient un courrier numéroté (type {@code COURRIER}) avec ses pièces ; la table de routage
 * l'envoie vers un dossier métier lié dont la tâche va au service désigné, ou vers la file de tri du groupe COURRIER. Seule la boîte aux lettres est simulée. Placé dans ce
 * paquet parce que {@link ImapMailbox} n'est pas public.
 */
@SpringBootTest(properties = "flowable.async-executor-activate=false")
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class MailRoomExampleIT {

    private static final Path EXAMPLE = Path.of("exemples/digital-mail-room");
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String TENANT = "tenant_dev";
    private static final String OPERATOR = "operateur-courrier";
    private static final int SMTP_PORT = freePort();

    /** Issue #19 (CS8) : l'accusé de réception part par le vrai relais de l'outbox vers un serveur SMTP en mémoire. */
    @RegisterExtension
    static final GreenMailExtension MAIL = new GreenMailExtension(new ServerSetup(SMTP_PORT, "127.0.0.1", "smtp"));

    @DynamicPropertySource
    static void smtp(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP_PORT);
    }

    @MockitoBean private ImapMailbox mailbox;
    @MockitoBean private SftpClientPort sftp;
    @MockitoSpyBean private AttachmentScanPort scanPort;

    @Autowired private MockMvc mockMvc;
    @Autowired private ConnectorTriggerRuntime triggerRuntime;
    @Autowired private RuntimeService runtimeService;
    @Autowired private TaskService taskService;
    @Autowired private HistoryService historyService;
    @Autowired private ManagementService managementService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DeadlineAlertJob deadlineAlertJob;
    @Autowired private MailRoomMcpTools mailRoomTools;
    @Autowired private RecordUserLoginUseCase recordLogin;
    @Autowired private be.myrmacta.domain.promotion.port.out.ConfigurationBundleCodec bundleCodec;
    @Autowired private be.myrmacta.domain.mailroom.port.in.ManageReplyWorkflowUseCase replyWorkflows;
    @Autowired private be.myrmacta.domain.deadline.port.in.ManageDeadlineUseCase deadlines;

    private static boolean configured;

    @Test
    void anInvoiceFromAKnownSupplierIsRoutedToAccounting_inALinkedInvoiceDossier() throws Exception {
        configureTheExample();
        var invoice = new InboundAttachment("facture-2026-117.pdf", "application/pdf", "%PDF-1.4 facture".getBytes(StandardCharsets.US_ASCII));
        deliver(new ImapMailbox.Mail("<f117@fournisseur.example>", "compta@fournisseur.example", "Fournisseur SA",
                "Facture 2026-117", Instant.parse("2026-10-30T08:15:00Z"), "Veuillez trouver notre facture en annexe.", false,
                "mx.example.com; dmarc=pass header.from=fournisseur.example", List.of(invoice), 0));

        String mailId = awaitMailDossier("Facture 2026-117");
        JsonNode dossier = awaitRouted(mailId);
        assertThat(dossier.get("reference").asString()).contains("COURRIER");
        assertThat(dossier.get("fields").get("canal").asString()).isEqualTo("EMAIL");
        assertThat(dossier.get("fields").get("service").asString()).isEqualTo("COMPTABILITE");
        assertThat(dossier.get("fields").get("echeance").asString())
                .isEqualTo(LocalDate.now(ZoneOffset.UTC).plusDays(30).toString());
        assertThat(read(get("/api/dossiers/{id}/elements", mailId)).toString()).contains("facture-2026-117.pdf");
        assertThat(read(get("/api/dossiers/{id}/workflows", mailId)).get("content").get(0).get("status").asString())
                .isEqualTo("ROUTE");

        JsonNode relation = read(get("/api/dossiers/{id}/relations", mailId)).get(0);
        assertThat(relation.get("type").asString()).isEqualTo("LINKED");
        String invoiceId = relation.get("relatedDossier").get("id").asString();
        JsonNode business = read(get("/api/dossiers/{id}", invoiceId));
        assertThat(business.get("dossierTypeCode").asString()).isEqualTo("FACTURE");
        assertThat(business.get("label").asString()).isEqualTo("Facture 2026-117");
        assertThat(business.get("fields").get("expediteur").asString()).isEqualTo("compta@fournisseur.example");
        String invoiceProcess = runtimeService.createProcessInstanceQuery().processDefinitionKey("traiterFacture")
                .variableValueEquals("dossierId", invoiceId).singleResult().getId();
        Task task = taskService.createTaskQuery().processInstanceId(invoiceProcess).singleResult();
        assertThat(task.getAssignee()).isNull();
        assertThat(taskService.getIdentityLinksForTask(task.getId()))
                .anySatisfy(link -> assertThat(link.getGroupId()).isEqualTo("COMPTABILITE"));

        // TODO §115 (D-DEADLINE-FIELD) : le routage écrit l'échéance dans le champ désigné du type FACTURE.
        String deadline = LocalDate.now(ZoneOffset.UTC).plusDays(30).toString();
        assertThat(business.get("fields").get("echeance").asString()).isEqualTo(deadline);
        // D-DEADLINE-ESCALATE : rappel à J-5 (une fois), puis, le lendemain du terme, la tâche passe à la direction financière.
        Instant reminder = LocalDate.parse(deadline).minusDays(5).atTime(9, 0).toInstant(ZoneOffset.UTC);
        deadlineAlertJob.process(reminder);
        deadlineAlertJob.process(reminder.plusSeconds(3600));
        assertThat(jdbc.queryForObject("select count(*) from dossier_deadline_alert where dossier_id = ?::uuid and level = 'RAPPEL'",
                Integer.class, invoiceId)).isEqualTo(1);
        assertThat(taskService.getIdentityLinksForTask(task.getId()))
                .anySatisfy(link -> assertThat(link.getGroupId()).isEqualTo("COMPTABILITE"));
        deadlineAlertJob.process(LocalDate.parse(deadline).plusDays(1).atTime(9, 0).toInstant(ZoneOffset.UTC));
        assertThat(taskService.getIdentityLinksForTask(task.getId()))
                .anySatisfy(link -> assertThat(link.getGroupId()).isEqualTo("DIRECTION_FINANCIERE"))
                .noneSatisfy(link -> assertThat(link.getGroupId()).isEqualTo("COMPTABILITE"));
        assertThat(read(get("/api/dossiers/{id}/elements", invoiceId)).toString())
                .contains("Échéance du " + deadline + " dépassée : 1 tâche(s) réaffectée(s) au groupe DIRECTION_FINANCIERE");

        // Le registre du courrier : la liste des dossiers filtrée sur le type.
        assertThat(read(get("/api/dossiers").param("dossierTypeCode", "COURRIER")).get("content").toString())
                .contains(dossier.get("reference").asString());
    }

    @Test
    void anUnrecognisedMailGoesToTheSortingQueueOfTheMailRoomGroup() throws Exception {
        configureTheExample();
        deliver(new ImapMailbox.Mail("<x1@ailleurs.example>", "quelquun@ailleurs.example", "Inconnu", "Question diverse",
                Instant.parse("2026-10-30T09:00:00Z"), "Bonjour", false, "mx.example.com; dmarc=pass header.from=ailleurs.example",
                List.of(), 0, MailThread.of("<devis-12@fournisseur.example>", "<devis-12@fournisseur.example>", "")));

        String mailId = awaitMailDossier("Question diverse");
        ProcessInstance sorting = awaitSortingTask(mailId);
        Task task = taskService.createTaskQuery().processInstanceId(sorting.getId()).taskDefinitionKey("trierCourrier").singleResult();
        assertThat(task.getAssignee()).isNull();
        assertThat(taskService.getIdentityLinksForTask(task.getId()))
                .anySatisfy(link -> assertThat(link.getGroupId()).isEqualTo("COURRIER"));
        assertThat(read(get("/api/dossiers/{id}/workflows", mailId)).get("content").get(0).get("status").asString())
                .isEqualTo("A_TRIER");
        assertThat(read(get("/api/dossiers/{id}", mailId)).get("fields").get("service").asString()).isEqualTo("COURRIER");
        assertThat(runtimeService.getVariables(sorting.getId()))
                .containsEntry("routageResultat", "A_TRIER")
                .containsEntry("routageMotif", "aucune action de routage")
                .containsEntry("mailInReplyTo", "<devis-12@fournisseur.example>")
                .containsEntry("mailSenderAuthenticated", true);
        assertThat(read(get("/api/dossiers/{id}/relations", mailId))).isEmpty();
    }

    /** TODO §113 (lot 3a) — une réponse au fil d'une facture routée rejoint le dossier « facture » et réveille son traitement. */
    @Test
    void aReplyFromTheSupplierJoinsTheInvoiceDossierOfItsThread_aForeignDomainDoesNot() throws Exception {
        configureTheExample();
        deliver(new ImapMailbox.Mail("<f300@fournisseur.example>", "compta@fournisseur.example", "Fournisseur SA", "Facture 2026-300",
                Instant.parse("2026-10-30T08:15:00Z"), "Notre facture.", false, "mx.example.com; dmarc=pass header.from=fournisseur.example",
                List.of(), 0));
        String invoiceMail = awaitMailDossier("Facture 2026-300");
        awaitRouted(invoiceMail);
        String invoiceId = read(get("/api/dossiers/{id}/relations", invoiceMail)).get(0).get("relatedDossier").get("id").asString();

        var reminder = new InboundAttachment("rappel.pdf", "application/pdf", "%PDF-1.4 rappel".getBytes(StandardCharsets.US_ASCII));
        deliver(new ImapMailbox.Mail("<r300@fournisseur.example>", "compta@fournisseur.example", "Fournisseur SA",
                "RE: Facture 2026-300", Instant.parse("2026-10-31T08:15:00Z"), "Rappel en annexe.", false,
                "mx.example.com; dmarc=pass header.from=fournisseur.example", List.of(reminder), 0,
                MailThread.of("<f300@fournisseur.example>", "<f300@fournisseur.example>", "")));
        String reply = awaitMailDossier("RE: Facture 2026-300");
        awaitRouted(reply);

        assertThat(read(get("/api/dossiers/{id}/workflows", reply)).get("content").get(0).get("status").asString()).isEqualTo("RATTACHE");
        JsonNode link = read(get("/api/dossiers/{id}/relations", reply)).get(0);
        assertThat(link.get("type").asString()).isEqualTo("LINKED");
        assertThat(link.get("relatedDossier").get("id").asString()).isEqualTo(invoiceId);
        assertThat(read(get("/api/dossiers/{id}/elements", invoiceId)).toString()).contains("rappel.pdf");
        String invoiceProcess = runtimeService.createProcessInstanceQuery().processDefinitionKey("traiterFacture")
                .variableValueEquals("dossierId", invoiceId).singleResult().getId();
        Task readTask = taskService.createTaskQuery().processInstanceId(invoiceProcess).taskDefinitionKey("lireCourrier").singleResult();
        assertThat(readTask).isNotNull();
        assertThat(taskService.getIdentityLinksForTask(readTask.getId()))
                .anySatisfy(identity -> assertThat(identity.getGroupId()).isEqualTo("COMPTABILITE"));
        assertThat(taskService.createTaskQuery().processInstanceId(invoiceProcess).taskDefinitionKey("traiterFactureTache").count())
                .isEqualTo(1);
        // Mode 3 (D-MAILROOM-REPLY-WORKFLOW, DOSSIER_ENFANT) : un dossier « réponse » enfant de la facture, avec son workflow.
        JsonNode child = null;
        for (JsonNode relation : read(get("/api/dossiers/{id}/relations", invoiceId))) {
            if ("PARENT_CHILD".equals(relation.get("type").asString())) {
                child = relation.get("relatedDossier");
            }
        }
        assertThat(child).isNotNull();
        assertThat(child.get("dossierTypeCode").asString()).isEqualTo("REPONSE_FACTURE");
        var replyProcess = runtimeService.createProcessInstanceQuery().processDefinitionKey("traiterReponse")
                .variableValueEquals("dossierId", child.get("id").asString()).singleResult();
        assertThat(replyProcess).isNotNull();
        assertThat(runtimeService.getVariable(replyProcess.getId(), "courrierId")).isEqualTo(reply);
        // TODO §115 (D-DEADLINE-TIMER) : course « traitée ou échue » — le minuteur porte ${echeanceDepassee} (J+10, lendemain 00:00 UTC).
        String replyDeadline = LocalDate.now(ZoneOffset.UTC).plusDays(10).toString();
        assertThat(runtimeService.getVariable(replyProcess.getId(), "echeance")).isEqualTo(replyDeadline);
        var timer = managementService.createTimerJobQuery().processInstanceId(replyProcess.getId()).singleResult();
        assertThat(timer.getDuedate().toInstant())
                .isEqualTo(LocalDate.parse(replyDeadline).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        managementService.executeJob(managementService.moveTimerToExecutableJob(timer.getId()).getId());
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(replyProcess.getId()).count()).isZero();
        assertThat(historyService.createHistoricActivityInstanceQuery().processInstanceId(replyProcess.getId())
                .activityId("finEchue").count()).isEqualTo(1);
        assertThat(read(get("/api/dossiers/{id}", child.get("id").asString())).get("status").asString()).isEqualTo("CLOTURE");

        deliver(new ImapMailbox.Mail("<x300@autre.example>", "quelquun@autre.example", "Autre", "RE: Facture 2026-300 (autre)",
                Instant.parse("2026-10-31T09:00:00Z"), "Je m'immisce.", false, "mx.example.com; dmarc=pass header.from=autre.example",
                List.of(), 0, MailThread.of("<f300@fournisseur.example>", "", "")));
        String intruder = awaitMailDossier("RE: Facture 2026-300 (autre)");
        ProcessInstance sorting = awaitSortingTask(intruder);
        assertThat(runtimeService.getVariables(sorting.getId()))
                .containsEntry("filResultat", "NOUVEAU")
                .containsEntry("filMotif", "domaine de l'expéditeur différent de celui du fil");
        assertThat(read(get("/api/dossiers/{id}/relations", intruder))).isEmpty();
    }

    /** TODO §113 (D-MAILROOM-PDF-SFTP) — un PDF numérisé et son index JSON deviennent un courrier PAPIER, qui part au tri. */
    @Test
    void aScannedPdfWithItsIndexBecomesAPaperMail_sentToTheSortingQueue() throws Exception {
        configureTheExample();
        JsonNode example = JSON.readTree(EXAMPLE.resolve("config/connecteur-sftp-pdf.exemple.json").toFile());
        Map<String, Object> config = JSON.convertValue(example.get("config"), Map.class);
        ConnectorInstanceData scans = new ConnectorInstanceData(UUID.randomUUID(), TENANT, example.get("name").asString(),
                example.get("connectorType").asString(), true, config);
        byte[] pdf = "%PDF-1.4 lettre recommandée".getBytes(StandardCharsets.US_ASCII);
        byte[] index = "{\"expediteur\":\"greffe@tribunal.example\",\"objet\":\"Convocation 2026-77\"}".getBytes(StandardCharsets.UTF_8);
        when(sftp.listFiles(any(), eq("/scans/entree"), eq("*.{pdf,PDF}")))
                .thenReturn(List.of(new SftpClientPort.RemoteFile("/scans/entree/convocation.pdf", pdf.length)));
        when(sftp.listFiles(any(), eq("/scans/entree"), eq("*.{json,JSON}")))
                .thenReturn(List.of(new SftpClientPort.RemoteFile("/scans/entree/convocation.json", index.length)));
        when(sftp.download(any(), eq("/scans/entree/convocation.pdf"), anyLong())).thenReturn(pdf);
        when(sftp.download(any(), eq("/scans/entree/convocation.json"), anyLong())).thenReturn(index);

        triggerRuntime.onIncomingEvent(scans, Map.of());

        verify(sftp).archive(any(), eq("/scans/entree/convocation.pdf"), eq("/scans/traites"));
        verify(sftp).archive(any(), eq("/scans/entree/convocation.json"), eq("/scans/traites"));
        String mailId = awaitMailDossierByField("objet", "Convocation 2026-77");
        JsonNode dossier = read(get("/api/dossiers/{id}", mailId));
        assertThat(dossier.get("fields").get("canal").asString()).isEqualTo("PAPIER");
        assertThat(dossier.get("fields").get("expediteur").asString()).isEqualTo("greffe@tribunal.example");
        assertThat(read(get("/api/dossiers/{id}/elements", mailId)).toString()).contains("convocation.pdf");
        ProcessInstance sorting = awaitSortingTask(mailId);
        assertThat(runtimeService.getVariables(sorting.getId())).containsEntry("courrierFichier", "convocation.pdf")
                .containsEntry("filResultat", "NOUVEAU").containsEntry("routageResultat", "A_TRIER");
    }

    /**
     * Issue #277 — un PDF numérisé écarté par l'antivirus ne crée pas de courrier : la remise est annulée (ni dossier, ni process
     * en file) et le PDF part dans « refusés » avec son index.
     */
    @Test
    void aScannedPdfRejectedByTheAntivirusCreatesNoMail_andGoesToTheRejectedDirectory() throws Exception {
        configureTheExample();
        JsonNode example = JSON.readTree(EXAMPLE.resolve("config/connecteur-sftp-pdf.exemple.json").toFile());
        Map<String, Object> config = JSON.convertValue(example.get("config"), Map.class);
        ConnectorInstanceData scans = new ConnectorInstanceData(UUID.randomUUID(), TENANT, example.get("name").asString(),
                example.get("connectorType").asString(), true, config);
        byte[] pdf = "%PDF-1.4 piece infectee".getBytes(StandardCharsets.US_ASCII);
        byte[] index = "{\"expediteur\":\"pirate@malveillant.example\",\"objet\":\"Courrier infecte 2026-9\"}"
                .getBytes(StandardCharsets.UTF_8);
        when(sftp.listFiles(any(), eq("/scans/entree"), eq("*.{pdf,PDF}")))
                .thenReturn(List.of(new SftpClientPort.RemoteFile("/scans/entree/infecte.pdf", pdf.length)));
        when(sftp.listFiles(any(), eq("/scans/entree"), eq("*.{json,JSON}")))
                .thenReturn(List.of(new SftpClientPort.RemoteFile("/scans/entree/infecte.json", index.length)));
        when(sftp.download(any(), eq("/scans/entree/infecte.pdf"), anyLong())).thenReturn(pdf);
        when(sftp.download(any(), eq("/scans/entree/infecte.json"), anyLong())).thenReturn(index);
        doReturn(AttachmentScanResult.ofInfected("eicar-test-signature")).when(scanPort)
                .scan(argThat(content -> content != null && Arrays.equals(content, pdf)), any());
        Integer mailsBefore = jdbc.queryForObject("select count(*) from dossier", Integer.class);

        triggerRuntime.onIncomingEvent(scans, Map.of());

        verify(sftp).archive(any(), eq("/scans/entree/infecte.pdf"), eq("/scans/refuses"));
        verify(sftp).archive(any(), eq("/scans/entree/infecte.json"), eq("/scans/refuses"));
        verify(sftp, never()).archive(any(), eq("/scans/entree/infecte.pdf"), eq("/scans/traites"));
        assertThat(jdbc.queryForObject("select count(*) from dossier", Integer.class)).isEqualTo(mailsBefore);
        assertThat(read(get("/api/dossiers").param("dossierTypeCode", "COURRIER").param("f.objet", "Courrier infecte 2026-9"))
                .get("content")).isEmpty();
    }

    /** TODO §113 (D-MAILROOM-DEPOSIT) — un agent dépose deux fichiers : le PDF devient un courrier PAPIER, l'autre est refusé. */
    @Test
    void anAgentDepositsAPaperMail_fromTheDepositScreen() throws Exception {
        configureTheExample();
        ObjectNode point = (ObjectNode) JSON.readTree(EXAMPLE.resolve("config/connecteur-depot.exemple.json").toFile());
        point.remove("_commentaire");
        String pointId = JSON.readTree(mockMvc.perform(post("/api/connectors").with(user("admin-courrier", "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(point)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();

        RequestPostProcessor operator = user(OPERATOR, "DOSSIER_MANAGER");
        assertThat(read(get("/api/mail-deposit-points")).toString()).contains(pointId);
        assertThat(JSON.readTree(mockMvc.perform(get("/api/mail-deposit-points").with(user("passant", "DOSSIER_MANAGER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())).isEmpty();
        mockMvc.perform(multipart("/api/mail-deposit-points/{id}/deposits", pointId)
                        .file(new MockMultipartFile("files", "lettre.pdf", "application/pdf", "%PDF-1.4 x".getBytes(StandardCharsets.US_ASCII)))
                        .with(user("passant", "DOSSIER_MANAGER")).with(csrf()))
                .andExpect(status().isForbidden());

        JsonNode results = JSON.readTree(mockMvc.perform(multipart("/api/mail-deposit-points/{id}/deposits", pointId)
                        .file(new MockMultipartFile("files", "mise-en-demeure.pdf", "application/pdf",
                                "%PDF-1.4 mise en demeure".getBytes(StandardCharsets.US_ASCII)))
                        .file(new MockMultipartFile("files", "photo.pdf", "application/pdf", "JFIF pas un pdf".getBytes(StandardCharsets.US_ASCII)))
                        .param("expediteur", "avocat@cabinet.example").param("objet", "Mise en demeure 2026-12")
                        .with(operator).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("results");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).get("reference").asString()).contains("COURRIER");
        assertThat(results.get(1).get("refusal").asString()).isEqualTo("pas un PDF");
        String mailId = results.get(0).get("dossierId").asString();
        JsonNode dossier = read(get("/api/dossiers/{id}", mailId));
        assertThat(dossier.get("fields").get("canal").asString()).isEqualTo("PAPIER");
        assertThat(dossier.get("fields").get("objet").asString()).isEqualTo("Mise en demeure 2026-12");
        assertThat(read(get("/api/dossiers/{id}/elements", mailId)).toString()).contains("mise-en-demeure.pdf");
        awaitMailDossierByField("objet", "Mise en demeure 2026-12");
        ProcessInstance sorting = awaitSortingTask(mailId);
        assertThat(runtimeService.getVariables(sorting.getId())).containsEntry("courrierDeposePar", OPERATOR)
                .containsEntry("routageResultat", "A_TRIER");
    }

    /**
     * TODO §113 (D-MAILROOM-MCP) — un agent, par les outils MCP : lit le fil d'une facture, y rattache un courrier de la file de
     * tri (à blanc d'abord), réaffecte puis termine la tâche de tri. Chaque action laisse une note à son nom.
     */
    @Test
    void anAgentWorksTheSortingQueueThroughTheMcpTools() throws Exception {
        configureTheExample();
        deliver(new ImapMailbox.Mail("<f500@fournisseur.example>", "compta@fournisseur.example", "Fournisseur SA", "Facture 2026-500",
                Instant.parse("2026-10-30T08:15:00Z"), "Notre facture.", false, "mx.example.com; dmarc=pass header.from=fournisseur.example",
                List.of(), 0));
        String invoiceMail = awaitMailDossier("Facture 2026-500");
        awaitRouted(invoiceMail);
        String invoiceId = read(get("/api/dossiers/{id}/relations", invoiceMail)).get(0).get("relatedDossier").get("id").asString();
        deliver(new ImapMailbox.Mail("<q500@ailleurs.example>", "secretariat@ailleurs.example", "Secrétariat", "Courrier à classer 500",
                Instant.parse("2026-10-31T09:00:00Z"), "Concerne la facture 2026-500.", false,
                "mx.example.com; dmarc=pass header.from=ailleurs.example", List.of(), 0));
        String mailId = awaitMailDossier("Courrier à classer 500");
        ProcessInstance sorting = awaitSortingTask(mailId);
        Task sortingTask = taskService.createTaskQuery().processInstanceId(sorting.getId()).taskDefinitionKey("trierCourrier").singleResult();
        String linkId = String.valueOf(runtimeService.getVariable(sorting.getId(), "dossierWorkflowLinkId"));
        AccessContext.User agent = AccessContext.user(OPERATOR, Set.of("DOSSIER_MANAGER"));
        // complete_task ne termine qu'une tâche de la file d'un de mes groupes (SR-4) : l'agent est membre de COURRIER.
        recordLogin.record(OPERATOR, "Opérateur courrier", null, Set.of("DOSSIER_MANAGER"));
        mockMvc.perform(put("/api/user-groups/{code}/members/{userId}", "COURRIER", OPERATOR).with(user("admin-courrier", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().is2xxSuccessful());

        var thread = McpActorScope.call(agent, () -> mailRoomTools.getMailThread(invoiceId));
        assertThat(thread.linkedDossiers()).extracting(MailRoomMcpTools.LinkedDossier::dossierId)
                .containsExactly(java.util.UUID.fromString(invoiceMail));

        assertThat(McpActorScope.call(agent, () -> mailRoomTools.attachMailToDossier(mailId, invoiceId, true)))
                .containsEntry("dryRun", true).containsEntry("done", false);
        assertThat(read(get("/api/dossiers/{id}/relations", mailId))).isEmpty();
        McpActorScope.call(agent, () -> mailRoomTools.attachMailToDossier(mailId, invoiceId, false));
        assertThat(read(get("/api/dossiers/{id}/relations", mailId)).get(0).get("relatedDossier").get("id").asString()).isEqualTo(invoiceId);
        // SR-1 : un nouvel essai de l'agent ne refait rien (pas de seconde tâche « Prendre connaissance », pas de seconde note).
        assertThat(McpActorScope.call(agent, () -> mailRoomTools.attachMailToDossier(mailId, invoiceId, false)))
                .containsEntry("done", false).containsEntry("alreadyAttached", true);
        String invoiceProcess = runtimeService.createProcessInstanceQuery().processDefinitionKey("traiterFacture")
                .variableValueEquals("dossierId", invoiceId).singleResult().getId();
        assertThat(taskService.createTaskQuery().processInstanceId(invoiceProcess).taskDefinitionKey("lireCourrier").count()).isEqualTo(1);
        assertThat(read(get("/api/dossiers/{id}/elements", invoiceId)).toString()).contains("rattaché par " + OPERATOR);

        assertThat(McpActorScope.call(agent, () -> mailRoomTools.completeTask(linkId, sortingTask.getId(), true))).containsEntry("done", false);
        assertThat(taskService.createTaskQuery().taskId(sortingTask.getId()).count()).isEqualTo(1);
        McpActorScope.call(agent, () -> mailRoomTools.reassignTaskToGroup(linkId, sortingTask.getId(), "JURIDIQUE", false));
        assertThat(taskService.getIdentityLinksForTask(sortingTask.getId()))
                .anySatisfy(identity -> assertThat(identity.getGroupId()).isEqualTo("JURIDIQUE"));
        // Réaffectée à JURIDIQUE, la tâche n'est plus dans la file de l'agent : il ne peut plus la terminer (SR-4) ; on la remet.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> McpActorScope.call(agent,
                () -> mailRoomTools.completeTask(linkId, sortingTask.getId(), false))).hasMessageContaining("queue of one of your user groups");
        McpActorScope.call(agent, () -> mailRoomTools.reassignTaskToGroup(linkId, sortingTask.getId(), "COURRIER", false));

        McpActorScope.call(agent, () -> mailRoomTools.completeTask(linkId, sortingTask.getId(), false));
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(sorting.getId()).count()).isZero();
        assertThat(read(get("/api/dossiers/{id}", mailId)).get("status").asString()).isEqualTo("CLOTURE");
        assertThat(read(get("/api/dossiers/{id}/elements", mailId)).toString()).contains("terminée par " + OPERATOR);
    }

    /**
     * Issue #19 (CS8) — un e-mail dont l'expéditeur est attesté reçoit un accusé de réception rendu depuis le gabarit (objet,
     * référence du courrier, date), en texte et en HTML, marqué comme message automatique. Une réponse qui rejoint un fil
     * n'en reçoit pas un second.
     */
    @Test
    void anEmailFromAnAttestedSenderIsAcknowledgedOnce_aReplyInItsThreadIsNot() throws Exception {
        configureTheExample();
        String sender = "ack1@expediteur.example";
        deliver(new ImapMailbox.Mail("<ack1@expediteur.example>", sender, "Expéditeur", "Demande de renseignements 1",
                Instant.parse("2026-10-30T09:00:00Z"), "Bonjour", false,
                "mx.example.com; dmarc=pass header.from=expediteur.example", List.of(), 0));
        String mailId = awaitMailDossier("Demande de renseignements 1");
        awaitSortingTask(mailId);
        String reference = read(get("/api/dossiers/{id}", mailId)).get("reference").asString();

        MimeMessage ack = onlyAcknowledgement(sender);
        assertThat(ack.getSubject()).isEqualTo("Accusé de réception : Demande de renseignements 1");
        assertThat(ack.getHeader("Auto-Submitted")).containsExactly("auto-generated");
        List<BodyPart> parts = leafParts(ack);
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).getContentType()).startsWith("text/plain");
        assertThat((String) parts.get(0).getContent())
                .contains("« Demande de renseignements 1 »", "référence " + reference, "le " + LocalDate.now(ZoneOffset.UTC));
        assertThat(parts.get(1).getContentType()).startsWith("text/html");
        assertThat((String) parts.get(1).getContent()).contains("<b>" + reference + "</b>");

        // Une réponse du même expéditeur, dans le fil d'un courrier connu, rejoint ce fil : pas de second accusé.
        deliver(new ImapMailbox.Mail("<ack1-re@expediteur.example>", sender, "Expéditeur", "RE: Demande de renseignements 1",
                Instant.parse("2026-10-31T09:00:00Z"), "Précision", false,
                "mx.example.com; dmarc=pass header.from=expediteur.example", List.of(), 0,
                MailThread.of("<ack1@expediteur.example>", "<ack1@expediteur.example>", "")));
        String reply = awaitMailDossier("RE: Demande de renseignements 1");
        awaitRouted(reply);
        assertThat(read(get("/api/dossiers/{id}/workflows", reply)).get("content").get(0).get("status").asString())
                .isEqualTo("RATTACHE");
        assertAcknowledgements(sender, 1);
    }

    /**
     * Issue #19 — l'accusé vit dans une branche parallèle : sans gabarit actif, son job échoue (incident, aucun e-mail), mais
     * le courrier est quand même routé, et le tri se poursuit.
     */
    @Test
    void aFailingAcknowledgementNeverHoldsBackTheRouting() throws Exception {
        configureTheExample();
        RequestPostProcessor admin = user("admin-courrier", "TENANT_ADMIN");
        String templateId = JSON.readTree(mockMvc.perform(get("/api/notification-templates/resolve")
                        .param("templateKey", "accuse-reception-courrier").with(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asString();
        mockMvc.perform(post("/api/notification-templates/{id}/archive", templateId).with(admin).with(csrf()))
                .andExpect(status().isOk());
        String sender = "sansgabarit@expediteur.example";
        try {
            deliver(new ImapMailbox.Mail("<sansgabarit@expediteur.example>", sender, "Expéditeur", "Demande sans gabarit",
                    Instant.parse("2026-10-30T10:00:00Z"), "Bonjour", false,
                    "mx.example.com; dmarc=pass header.from=expediteur.example", List.of(), 0));
            String mailId = awaitMailDossier("Demande sans gabarit");
            ProcessInstance sorting = awaitSortingTask(mailId);
            // Le courrier est routé (ici à la file de tri) malgré l'échec de l'accusé.
            assertThat(runtimeService.getVariables(sorting.getId())).containsEntry("routageResultat", "A_TRIER")
                    .containsEntry("accuseReception", true);
            assertThat(read(get("/api/dossiers/{id}/workflows", mailId)).get("content").get(0).get("status").asString())
                    .isEqualTo("A_TRIER");
            // L'accusé est en incident sur sa branche, et rien n'est parti.
            Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
            long incidents = 0;
            while (incidents == 0 && Instant.now().isBefore(deadline)) {
                runJobs();
                incidents = managementService.createDeadLetterJobQuery().processInstanceId(sorting.getId()).count()
                        + managementService.createJobQuery().processInstanceId(sorting.getId()).withException().count()
                        // après un échec, le moteur replanifie le job (nouvelle tentative) en « job minuté » avec son exception
                        + managementService.createTimerJobQuery().processInstanceId(sorting.getId()).withException().count();
                Thread.sleep(200);
            }
            assertThat(incidents).as("incident de l'accusé").isPositive();
            assertAcknowledgements(sender, 0);
        } finally {
            // Les autres tests de la classe partagent le gabarit : nouvelle version, activée.
            String draftId = JSON.readTree(mockMvc.perform(post("/api/notification-templates/{id}/new-version", templateId)
                            .with(admin).with(csrf())).andExpect(status().isCreated()).andReturn().getResponse()
                    .getContentAsString()).get("id").asString();
            mockMvc.perform(post("/api/notification-templates/{id}/activate", draftId).with(admin).with(csrf()))
                    .andExpect(status().isOk());
        }
    }

    /** Issue #19 (CS8) — un courrier papier n'a pas d'adresse à qui répondre : aucun accusé. */
    @Test
    void aPaperMailIsNotAcknowledged() throws Exception {
        configureTheExample();
        JsonNode example = JSON.readTree(EXAMPLE.resolve("config/connecteur-sftp-pdf.exemple.json").toFile());
        Map<String, Object> config = JSON.convertValue(example.get("config"), Map.class);
        ConnectorInstanceData scans = new ConnectorInstanceData(UUID.randomUUID(), TENANT, example.get("name").asString(),
                example.get("connectorType").asString(), true, config);
        String sender = "papier@cabinet.example";
        byte[] pdf = "%PDF-1.4 lettre papier".getBytes(StandardCharsets.US_ASCII);
        byte[] index = ("{\"expediteur\":\"" + sender + "\",\"objet\":\"Lettre papier sans accuse\"}")
                .getBytes(StandardCharsets.UTF_8);
        when(sftp.listFiles(any(), eq("/scans/entree"), eq("*.{pdf,PDF}")))
                .thenReturn(List.of(new SftpClientPort.RemoteFile("/scans/entree/sans-accuse.pdf", pdf.length)));
        when(sftp.listFiles(any(), eq("/scans/entree"), eq("*.{json,JSON}")))
                .thenReturn(List.of(new SftpClientPort.RemoteFile("/scans/entree/sans-accuse.json", index.length)));
        when(sftp.download(any(), eq("/scans/entree/sans-accuse.pdf"), anyLong())).thenReturn(pdf);
        when(sftp.download(any(), eq("/scans/entree/sans-accuse.json"), anyLong())).thenReturn(index);

        triggerRuntime.onIncomingEvent(scans, Map.of());

        String mailId = awaitMailDossierByField("objet", "Lettre papier sans accuse");
        ProcessInstance sorting = awaitSortingTask(mailId);
        assertThat(runtimeService.getVariables(sorting.getId())).containsEntry("accuseReception", false);
        assertAcknowledgements(sender, 0);
    }

    /** Issue #19 (CS8, backscatter) — un expéditeur que NOTRE serveur de réception n'atteste pas n'est jamais accusé. */
    @Test
    void anEmailFromAnUnattestedSenderIsNotAcknowledged() throws Exception {
        configureTheExample();
        String sender = "usurpe@expediteur.example";
        deliver(new ImapMailbox.Mail("<usurpe@expediteur.example>", sender, "Expéditeur", "Demande non attestee",
                Instant.parse("2026-10-30T09:30:00Z"), "Bonjour", false,
                "mx.example.com; dmarc=fail header.from=expediteur.example", List.of(), 0));
        String mailId = awaitMailDossier("Demande non attestee");
        ProcessInstance sorting = awaitSortingTask(mailId);
        assertThat(runtimeService.getVariables(sorting.getId()))
                .containsEntry("mailSenderAuthenticated", false)
                .containsEntry("accuseReception", false);
        assertAcknowledgements(sender, 0);
    }

    /** Les messages reçus par cette adresse : le relais planifié peut aussi vider les lignes d'autres tests. */
    private List<MimeMessage> acknowledgements(String address) throws Exception {
        List<MimeMessage> mine = new ArrayList<>();
        for (MimeMessage message : MAIL.getReceivedMessages()) {
            if (message.getAllRecipients()[0].toString().equals(address)) {
                mine.add(message);
            }
        }
        return mine;
    }

    private MimeMessage onlyAcknowledgement(String address) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (acknowledgements(address).isEmpty() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        Thread.sleep(1_500); // laisse arriver un éventuel doublon
        assertThat(acknowledgements(address)).as("accusé de réception à " + address).hasSize(1);
        return acknowledgements(address).getFirst();
    }

    /** Plus d'un tour du relais (1 s) après le tri : autant de lignes d'outbox et de messages reçus que prévu pour cette adresse. */
    private void assertAcknowledgements(String address, int expected) throws Exception {
        Thread.sleep(2_500);
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where payload_json::text like ?",
                Long.class, "%" + address + "%")).isEqualTo((long) expected);
        assertThat(acknowledgements(address)).as("accusés de réception à " + address).hasSize(expected);
    }

    private static List<BodyPart> leafParts(MimeMessage message) throws Exception {
        List<BodyPart> leaves = new ArrayList<>();
        collect((Multipart) message.getContent(), leaves);
        return leaves;
    }

    private static void collect(Multipart multipart, List<BodyPart> leaves) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.getContent() instanceof Multipart nested) {
                collect(nested, leaves);
            } else {
                leaves.add(part);
            }
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Le courrier dont un champ vaut cette valeur, une fois le relais de l'outbox terminé (voir {@link #awaitMailDossier}). */
    private String awaitMailDossierByField(String field, String value) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            Integer pending = jdbc.queryForObject("select count(*) from outbox_event where status = 'PENDING'", Integer.class);
            JsonNode found = read(get("/api/dossiers").param("dossierTypeCode", "COURRIER").param("f." + field, value)).get("content");
            if (pending != null && pending == 0 && found.size() == 1) {
                return found.get(0).get("id").asString();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("no mail dossier with " + field + " = " + value);
    }

    private void deliver(ImapMailbox.Mail mail) throws Exception {
        doAnswer(call -> {
            ImapMailbox.Handler handler = call.getArgument(3);
            assertThat(handler.handle(mail)).isEqualTo(ImapMailbox.Outcome.PROCESSED);
            return null;
        }).when(mailbox).process(any(), anyInt(), any(), any());
        triggerRuntime.onIncomingEvent(exampleConnector(), Map.of());
    }

    /** Groupes, types, configurations, table DMN et BPMN, tels que livrés dans l'exemple (une fois par contexte). */
    private void configureTheExample() throws Exception {
        if (configured) {
            return;
        }
        RequestPostProcessor admin = user("admin-courrier", "TENANT_ADMIN");
        for (JsonNode group : JSON.readTree(EXAMPLE.resolve("config/groupes.json").toFile())) {
            mockMvc.perform(post("/api/user-groups").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(group)))
                    .andExpect(status().isCreated());
        }
        for (String type : List.of("config/type-dossier.json", "config/type-facture.json", "config/type-reponse-facture.json")) {
            JsonNode body = JSON.readTree(EXAMPLE.resolve(type).toFile());
            mockMvc.perform(post("/api/dossier-types").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(body)))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/dossier-types/{code}/acl", body.get("code").asString()).with(admin).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subjectUserId\":\"" + OPERATOR + "\",\"permission\":\"EDIT\"}"))
                    .andExpect(status().isNoContent());
        }
        for (String workflow : List.of("config/workflow-config.json", "config/workflow-config-facture.json",
                "config/workflow-config-reponse.json")) {
            // Forme MCP (processDefinitionKey) ; l'API REST attend workflowKey.
            ObjectNode config = (ObjectNode) JSON.readTree(EXAMPLE.resolve(workflow).toFile());
            config.set("workflowKey", config.remove("processDefinitionKey"));
            mockMvc.perform(post("/api/workflow-type-configs").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(config)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(multipart("/api/dmn-decision-deployments").file(new MockMultipartFile("file", "routage-courrier.dmn",
                                "application/xml", Files.readAllBytes(EXAMPLE.resolve("routage-courrier.dmn"))))
                        .with(admin).with(csrf()))
                .andExpect(status().isCreated());
        // Issue #19 (D-NOTIF-TPL-ACK) : le gabarit de l'accusé de réception, activé (geste humain) avant le premier courrier.
        String templateId = JSON.readTree(mockMvc.perform(post("/api/notification-templates").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Files.readString(EXAMPLE.resolve("config/gabarit-accuse-reception.json"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        mockMvc.perform(post("/api/notification-templates/{id}/activate", templateId).with(admin).with(csrf()))
                .andExpect(status().isOk());
        for (String bpmn : List.of("traiter-facture.bpmn20.xml", "traiter-reponse.bpmn20.xml", "tri-courrier.bpmn20.xml")) {
            mockMvc.perform(multipart("/api/workflow-bpmn-deployments").file(new MockMultipartFile("file", bpmn,
                                    "application/xml", Files.readAllBytes(EXAMPLE.resolve(bpmn))))
                            .with(admin).with(csrf()))
                    .andExpect(status().isCreated());
        }
        ObjectNode reply = (ObjectNode) JSON.readTree(EXAMPLE.resolve("config/reponse-facture.json").toFile());
        reply.remove("_commentaire");
        mockMvc.perform(put("/api/dossier-types/{code}/reply-workflow", "FACTURE").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(reply)))
                .andExpect(status().isOk());
        ObjectNode deadline = (ObjectNode) JSON.readTree(EXAMPLE.resolve("config/echeance-facture.json").toFile());
        deadline.remove("_commentaire");
        mockMvc.perform(put("/api/dossier-types/{code}/deadline", "FACTURE").with(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(deadline)))
                .andExpect(status().isOk());
        configured = true;
    }

    /** Le connecteur de l'exemple, sur une adresse publique littérale (pas de résolution DNS dans le test). */
    @SuppressWarnings("unchecked")
    private static ConnectorInstanceData exampleConnector() throws Exception {
        JsonNode example = JSON.readTree(EXAMPLE.resolve("config/connecteur-imap.exemple.json").toFile());
        Map<String, Object> config = JSON.convertValue(example.get("config"), Map.class);
        config.put("host", "93.184.216.34");
        return new ConnectorInstanceData(UUID.randomUUID(), TENANT, example.get("name").asString(),
                example.get("connectorType").asString(), true, config);
    }

    /**
     * Le courrier créé pour cet objet : le relais de l'outbox démarre le tri en arrière-plan, qui peut déjà être fini. On attend
     * aussi que le relais ait fini : il démarre le process (le job devient visible) PUIS confirme le lien dans une seconde
     * transaction qui modifie le dossier. Exécuter le job entre les deux ferait échouer l'un des deux sur le verrou optimiste ;
     * en production, l'exécuteur asynchrone réessaie le job, ici coupé.
     */
    private String awaitMailDossier(String subject) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            var started = historyService.createHistoricProcessInstanceQuery().processDefinitionKey("triCourrier")
                    .variableValueEquals("mailSubject", subject).singleResult();
            Integer pending = jdbc.queryForObject("select count(*) from outbox_event where status = 'PENDING'", Integer.class);
            if (started != null && pending != null && pending == 0) {
                return (String) historyService.createHistoricVariableInstanceQuery().processInstanceId(started.getId())
                        .variableName("dossierId").singleResult().getValue();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("no mail dossier for " + subject + "; outbox: "
                + jdbc.queryForList("select status, attempts, last_error from outbox_event"));
    }

    /** Exécuteur asynchrone coupé : les jobs (première étape du tri) sont exécutés ici, dans l'ordre. */
    private void runJobs() {
        for (var job : managementService.createJobQuery().list()) {
            try {
                managementService.executeJob(job.getId());
            } catch (RuntimeException failed) {
                // Un job en échec (ex. l'accusé de réception sans gabarit) ne doit pas empêcher d'exécuter les autres : il reste
                // en incident, ce que les tests concernés vérifient.
            }
        }
    }

    private JsonNode awaitRouted(String mailId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            runJobs();
            JsonNode dossier = read(get("/api/dossiers/{id}", mailId));
            if ("CLOTURE".equals(dossier.get("status").asString())) {
                return dossier;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("the mail was not routed; outbox: "
                + jdbc.queryForList("select status, attempts, last_error from outbox_event") + "; dead jobs: "
                + jdbc.queryForList("select exception_msg_ from act_ru_deadletter_job") + "; jobs: "
                + jdbc.queryForList("select retries_, exception_msg_, lock_owner_, lock_exp_time_ from act_ru_job") + "; executions: "
                + jdbc.queryForList("select act_id_, is_active_ from act_ru_execution") + "; engine jobs: "
                + managementService.createJobQuery().count() + "; suspended: "
                + jdbc.queryForList("select exception_msg_ from act_ru_suspended_job") + "; status: "
                + read(get("/api/dossiers/{id}", mailId)).get("status").asString() + "; workflows: "
                + read(get("/api/dossiers/{id}/workflows", mailId)).get("content"));
    }

    private ProcessInstance awaitSortingTask(String mailId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            runJobs();
            ProcessInstance found = runtimeService.createProcessInstanceQuery().processDefinitionKey("triCourrier")
                    .variableValueEquals("dossierId", mailId).singleResult();
            // L'accusé de réception est une branche parallèle asynchrone : on attend aussi qu'elle ait fini (ou échoué pour de bon).
            if (found != null && taskService.createTaskQuery().processInstanceId(found.getId()).count() == 1
                    && managementService.createJobQuery().processInstanceId(found.getId()).count() == 0) {
                return found;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("the sorting task was not created");
    }

    private JsonNode read(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return JSON.readTree(mockMvc.perform(request.with(user(OPERATOR, "DOSSIER_MANAGER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static RequestPostProcessor user(String subject, String role) {
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /**
     * TODO §117 (issue #279) — l'exemple entier voyage dans un bundle : groupes (sans membres), types, configurations, table de
     * routage, processus, workflow « réponse » et échéance. Relu sur la même instance, tout est inchangé et aucun groupe n'est
     * signalé orphelin ; un réglage absent de la cible (autre environnement) est recréé par l'import.
     */
    @Test
    void theWholeExampleTravelsInABundle_andMissingTypeSettingsAreRestoredByTheImport() throws Exception {
        configureTheExample();
        RequestPostProcessor admin = user("admin-courrier", "TENANT_ADMIN");
        var bundle = bundleCodec.read(mockMvc.perform(get("/api/admin/configuration/export").with(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());

        assertThat(bundle.formatVersion()).isEqualTo(2);
        for (JsonNode group : JSON.readTree(EXAMPLE.resolve("config/groupes.json").toFile())) {
            var entry = bundle.find(be.myrmacta.domain.promotion.model.ArtifactType.USER_GROUP, group.get("code").asString());
            assertThat(entry).as(group.get("code").asString()).isPresent();
            assertThat(entry.get().text()).doesNotContain("member").doesNotContain(OPERATOR);
        }
        assertThat(bundle.find(be.myrmacta.domain.promotion.model.ArtifactType.REPLY_WORKFLOW, "FACTURE")).isPresent();
        assertThat(bundle.find(be.myrmacta.domain.promotion.model.ArtifactType.DEADLINE, "FACTURE")).isPresent();
        assertThat(bundle.find(be.myrmacta.domain.promotion.model.ArtifactType.DMN, "routageCourrier")).isPresent();
        // Issue #19 (H1) : le gabarit de l'accusé de réception voyage aussi (version active), et se rejoue sans changement.
        assertThat(bundle.find(be.myrmacta.domain.promotion.model.ArtifactType.NOTIFICATION_TEMPLATE,
                be.myrmacta.domain.promotion.artifact.DocumentTemplateArtifacts.entryKey("accuse-reception-courrier", null, null)))
                .isPresent();

        JsonNode same = promotion("plan", bundle, admin);
        for (JsonNode e : same.get("entries")) {
            assertThat(e.get("action").asString()).as(e.get("type").asString() + " " + e.get("key").asString()).isEqualTo("UNCHANGED");
            if (List.of("DMN", "BPMN").contains(e.get("type").asString())) {
                assertThat(e.get("reason").asString()).as("no orphan group in the example").doesNotContain("WARNING");
            }
        }

        // « autre environnement » : les réglages du type n'y existent pas encore
        replyWorkflows.clear("FACTURE");
        deadlines.clear("FACTURE");
        try {
            JsonNode plan = promotion("plan", bundle, admin);
            assertThat(action(plan, "REPLY_WORKFLOW", "FACTURE")).isEqualTo("CREATE");
            assertThat(action(plan, "DEADLINE", "FACTURE")).isEqualTo("CREATE");
            JsonNode report = promotion("apply", bundle, admin);
            assertThat(outcome(report, "REPLY_WORKFLOW", "FACTURE")).isEqualTo("APPLIED");
            assertThat(outcome(report, "DEADLINE", "FACTURE")).isEqualTo("APPLIED");
        } finally {
            // les autres tests de la classe partagent l'exemple : il est remis en état même si l'import a échoué
            JsonNode reply = JSON.readTree(EXAMPLE.resolve("config/reponse-facture.json").toFile());
            if (replyWorkflows.get("FACTURE").isEmpty()) {
                replyWorkflows.set("FACTURE", be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting.Mode.valueOf(
                        reply.get("mode").asString()), reply.get("workflowKey").asString(), reply.path("childTypeCode").asString(null));
            }
            JsonNode deadline = JSON.readTree(EXAMPLE.resolve("config/echeance-facture.json").toFile());
            if (deadlines.get("FACTURE").isEmpty()) {
                deadlines.set("FACTURE", deadline.get("fieldName").asString(), deadline.get("reminderDays").asInt(),
                        deadline.path("escalationGroup").asString(null));
            }
        }
        assertThat(replyWorkflows.get("FACTURE")).hasValueSatisfying(r -> assertThat(r.workflowKey()).isEqualTo("traiterReponse"));
        assertThat(deadlines.get("FACTURE")).isPresent();
        for (JsonNode e : promotion("plan", bundle, admin).get("entries")) {
            assertThat(e.get("action").asString()).as(e.get("type").asString() + " " + e.get("key").asString()).isEqualTo("UNCHANGED");
        }
    }

    private JsonNode promotion(String step, be.myrmacta.domain.promotion.model.ConfigurationBundle bundle, RequestPostProcessor admin)
            throws Exception {
        var request = multipart("/api/admin/configuration/" + step)
                .file(new MockMultipartFile("file", "bundle.zip", "application/zip", bundleCodec.write(bundle)))
                .with(admin).with(csrf());
        if (step.equals("apply")) {
            request.param("expectedBundleSha256", bundle.sha256());
        }
        return JSON.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String action(JsonNode plan, String type, String key) {
        return field(plan, type, key, "action");
    }

    private static String outcome(JsonNode report, String type, String key) {
        return field(report, type, key, "outcome");
    }

    private static String field(JsonNode result, String type, String key, String name) {
        for (JsonNode e : result.get("entries")) {
            if (e.get("type").asString().equals(type) && e.get("key").asString().equals(key)) {
                return e.get(name).asString();
            }
        }
        throw new AssertionError(type + " " + key + " not found in " + result);
    }
}
