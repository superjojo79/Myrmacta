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
import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.notification.model.NotificationValidationException;
import be.myrmacta.domain.notification.port.out.NotificationOutboxRepository;
import be.myrmacta.domain.notification.template.port.in.ActivateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase.CreateCommand;
import be.myrmacta.support.OidcClientTestConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.job.api.Job;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Issue #19 (CS2 à CS5, TODO §123) — la brique « Notifier » avec un gabarit, sur le moteur réel, l'outbox réelle, le
 * relais planifié et un serveur SMTP en mémoire (GreenMail) : le message reçu est relu (objet, parties, en-tête anti-boucle).
 * Le port SMTP est libre (pas de collision avec un autre build de la machine).
 */
@SpringBootTest(properties = "flowable.async-executor-activate=false")
@Import(OidcClientTestConfiguration.class)
class NotifierTemplateEmailIT {

    private static final String TENANT = "tenant_dev";
    private static final int SMTP_PORT = freePort();

    @RegisterExtension
    static final GreenMailExtension MAIL = new GreenMailExtension(new ServerSetup(SMTP_PORT, "127.0.0.1", "smtp"));

    @DynamicPropertySource
    static void smtp(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP_PORT);
    }

    @Autowired private RepositoryService repositoryService;
    @Autowired private RuntimeService runtimeService;
    @Autowired private ManagementService managementService;
    @Autowired private DossierRepository dossiers;
    @Autowired private DossierTypeRepository dossierTypes;
    @Autowired private CreateNotificationTemplateUseCase createTemplate;
    @Autowired private ActivateNotificationTemplateUseCase activateTemplate;
    @Autowired private NotificationOutboxRepository outbox;
    @Autowired private JdbcTemplate jdbc;

    private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final String recipient = "dest-" + suffix + "@example.org";
    private final List<String> deployments = new ArrayList<>();
    private String typeCode;

    @BeforeEach
    void dossierType() {
        typeCode = "TPL" + suffix.toUpperCase();
        dossierTypes.save(DossierType.inherit(UUID.randomUUID(), TENANT, typeCode, "Gabarit", "p", true, false, List.of(
                new FieldDefinition("objet", "Objet", FieldType.STRING, false, null, null, null),
                new FieldDefinition("ssn", "Numéro national", FieldType.STRING, false, null, List.of(), null, true))));
    }

    @AfterEach
    void undeploy() {
        deployments.forEach(id -> repositoryService.deleteDeployment(id, true));
    }

    /** CS2 — objet et corps rendus avec {@code dossier.*} et {@code variables.*} ; texte et HTML en {@code multipart/alternative}. */
    @Test
    void aTemplateWithAnHtmlBodyIsSentAsTextAndHtmlWithTheAutoSubmittedHeader() throws Exception {
        String key = template(null, null, "Accusé : [[${dossier.reference}]]",
                "Dossier [[${dossier.reference}]] ([[${dossier.fields.objet}]]) - motif : [[${variables.motif}]]",
                "<p>Dossier <b th:text=\"${dossier.reference}\">r</b> - motif : <i th:text=\"${variables.motif}\">m</i></p>");
        Dossier dossier = dossier();
        start(process("sync", false, "flowable:notificationTemplate=\"" + key + "\""),
                Map.of("dossierId", dossier.id().toString(), "motif", "Pièce <manquante> & datée"));

        MimeMessage message = onlyMessage();
        assertThat(message.getSubject()).isEqualTo("Accusé : " + dossier.reference());
        assertThat(message.getHeader("Auto-Submitted")).containsExactly("auto-generated");
        List<BodyPart> parts = leafParts(message);
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).getContentType()).startsWith("text/plain");
        // [[${…}]] échappe en entités HTML même en mode TEXT : le renderer les décode (lot 1) ; le texte brut reste fidèle.
        assertThat(parts.get(0).getContent()).isEqualTo("Dossier " + dossier.reference() + " (Demande de test)"
                + " - motif : Pièce <manquante> & datée");
        assertThat(parts.get(1).getContentType()).startsWith("text/html");
        assertThat((String) parts.get(1).getContent())
                .contains("<b>" + dossier.reference() + "</b>")
                .contains("Pièce &lt;manquante&gt; &amp; datée");
    }

    /** CS2 — variante et langue : la cascade de résolution choisit la langue désignée par la variable de process. */
    @Test
    void theLocaleVariableSelectsTheLocalizedVersion() throws Exception {
        String key = template(null, null, "DEFAUT", "texte par défaut", null);
        template(key, "fr", "FRANCAIS", "texte français", null);
        Dossier dossier = dossier();
        start(process("locale", false, "flowable:notificationTemplate=\"" + key + "\" flowable:notificationLocale=\"variable:langue\""),
                Map.of("dossierId", dossier.id().toString(), "langue", "fr"));

        MimeMessage message = onlyMessage();
        assertThat(message.getSubject()).isEqualTo("FRANCAIS");
        assertThat(message.getContentType()).startsWith("text/plain"); // sans HTML : texte seul
        assertThat(message.getContent()).isEqualTo("texte français");
    }

    /** Non-régression — une brique sans gabarit envoie toujours son objet et son corps littéraux, en texte seul. */
    @Test
    void aBlockWithoutATemplateStillSendsItsLiteralSubjectAndBody() throws Exception {
        Dossier dossier = dossier();
        start(process("literal", false, "flowable:notificationSubject=\"Objet littéral\" flowable:notificationBody=\"Corps littéral\""),
                Map.of("dossierId", dossier.id().toString()));

        MimeMessage message = onlyMessage();
        assertThat(message.getSubject()).isEqualTo("Objet littéral");
        assertThat(message.getContentType()).startsWith("text/plain");
        assertThat(message.getContent()).isEqualTo("Corps littéral");
        assertThat(message.getHeader("Auto-Submitted")).containsExactly("auto-generated");
    }

    /** Non-régression — une ligne d'outbox écrite avant les gabarits (sans {@code htmlBody}) part toujours en texte seul. */
    @Test
    void anOutboxRowWithoutHtmlBodyIsStillSentAsPlainText() throws Exception {
        outbox.savePending(TENANT, NotificationChannel.EMAIL,
                "{\"to\":\"" + recipient + "\",\"subject\":\"Ancienne ligne\",\"body\":\"Texte seul\"}", Instant.now());

        MimeMessage message = onlyMessage();
        assertThat(message.getSubject()).isEqualTo("Ancienne ligne");
        assertThat(message.getContentType()).startsWith("text/plain");
        assertThat(message.getContent()).isEqualTo("Texte seul");
    }

    /** CS3 — un champ sensible n'est jamais rendu : l'expression échoue comme une propriété absente, rien n'est écrit. */
    @Test
    void aSensitiveFieldIsNeverRenderedAndNothingIsWrittenToTheOutbox() throws Exception {
        String key = template(null, null, "Sensible", "Numéro : [[${dossier.fields.ssn}]]", null);
        Dossier dossier = dossier();

        assertThatThrownBy(() -> start(process("sensitive", false, "flowable:notificationTemplate=\"" + key + "\""),
                Map.of("dossierId", dossier.id().toString())))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining(key).hasMessageNotContaining("SECRET-NN");
        assertNothingSent();
    }

    /** CS4 — variable absente, brique synchrone : l'étape échoue (refus 422 côté API) et l'outbox reste vide. */
    @Test
    void anAbsentVariableFailsASynchronousStepAndNothingIsWrittenToTheOutbox() throws Exception {
        String key = template(null, null, "Absente", "Motif : [[${variables.motif}]]", null);
        Dossier dossier = dossier();

        assertThatThrownBy(() -> start(process("absent", false, "flowable:notificationTemplate=\"" + key + "\""),
                Map.of("dossierId", dossier.id().toString())))
                .isInstanceOf(NotificationValidationException.class).hasMessageContaining(key);
        assertNothingSent();
    }

    /** CS4 — brique asynchrone : le job échoue (incident après ses reprises) et l'outbox reste vide. */
    @Test
    void anAbsentVariableFailsAnAsynchronousJobAndNothingIsWrittenToTheOutbox() throws Exception {
        String key = template(null, null, "Absente", "Motif : [[${variables.motif}]]", null);
        Dossier dossier = dossier();
        String processInstance = start(process("absentAsync", true, "flowable:notificationTemplate=\"" + key + "\""),
                Map.of("dossierId", dossier.id().toString()));

        Job job = managementService.createJobQuery().processInstanceId(processInstance).singleResult();
        assertThat(job).isNotNull();
        assertThatThrownBy(() -> managementService.executeJob(job.getId()))
                .hasStackTraceContaining(NotificationValidationException.class.getName());
        assertNothingSent();
    }

    /** TODO §123 — un gabarit sans version active échoue à l'exécution, pas au déploiement. */
    @Test
    void aMissingTemplateFailsAtRuntimeNamingTheKey() throws Exception {
        Dossier dossier = dossier();
        String key = "absent-" + suffix;

        assertThatThrownBy(() -> start(process("missing", false, "flowable:notificationTemplate=\"" + key + "\""),
                Map.of("dossierId", dossier.id().toString())))
                .isInstanceOf(NotificationValidationException.class).hasMessageContaining(key);
        assertNothingSent();
    }

    /** CS5 — une variable de process {@code notificationTemplate} est ignorée : l'objet et le corps littéraux partent. */
    @Test
    void aProcessVariableNamedNotificationTemplateIsIgnored() throws Exception {
        String key = template(null, null, "Gabarit détourné", "ne doit pas partir", null);
        Dossier dossier = dossier();
        start(process("ignored", false, "flowable:notificationSubject=\"Objet littéral\" flowable:notificationBody=\"Corps littéral\""),
                Map.of("dossierId", dossier.id().toString(), "notificationTemplate", key));

        MimeMessage message = onlyMessage();
        assertThat(message.getSubject()).isEqualTo("Objet littéral");
        assertThat(message.getContent()).isEqualTo("Corps littéral");
    }

    // --- aides -----------------------------------------------------------------------------------------------------

    private String template(String key, String locale, String subject, String text, String html) {
        String templateKey = key == null ? "it-" + suffix : key;
        var draft = createTemplate.create(new CreateCommand(templateKey, null, locale, "Gabarit " + templateKey, subject, text,
                html, "test"));
        activateTemplate.activate(draft.id(), "test");
        return templateKey;
    }

    private Dossier dossier() {
        return dossiers.save(new Dossier(UUID.randomUUID(), TENANT, "REF-" + suffix, typeCode, "Gabarit",
                DossierStatus.OUVERT, Map.of("objet", "Demande de test", "ssn", "SECRET-NN"), null, 0L, "test", Instant.now(),
                null, null, null, null, null));
    }

    /** Un process {@code start -> Notifier -> attente}, avec les attributs de la brique. */
    private String process(String name, boolean async, String attributes) {
        String key = "notifierTpl" + name + suffix;
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://myrmacta/test">
                  <process id="%s" isExecutable="true">
                    <startEvent id="s"/>
                    <sequenceFlow id="f1" sourceRef="s" targetRef="n"/>
                    <serviceTask id="n" flowable:delegateExpression="${notificationDispatchTask}"%s
                                 flowable:notificationChannels="EMAIL" flowable:notificationTo="%s" %s/>
                    <sequenceFlow id="f2" sourceRef="n" targetRef="w"/>
                    <userTask id="w" flowable:assignee="notifier-smoke"/>
                  </process>
                </definitions>
                """.formatted(key, async ? " flowable:async=\"true\"" : "", recipient, attributes);
        deployments.add(repositoryService.createDeployment().name(key).addString(key + ".bpmn20.xml", xml).deploy().getId());
        return key;
    }

    private String start(String processKey, Map<String, Object> variables) {
        return runtimeService.startProcessInstanceByKey(processKey, variables).getId();
    }

    /** Messages reçus pour ce test seulement : le relais planifié peut aussi vider des lignes d'autres tests. */
    private List<MimeMessage> mine() throws Exception {
        List<MimeMessage> mine = new ArrayList<>();
        for (MimeMessage message : MAIL.getReceivedMessages()) {
            if (message.getAllRecipients()[0].toString().equals(recipient)) {
                mine.add(message);
            }
        }
        return mine;
    }

    private MimeMessage onlyMessage() throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (mine().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        Thread.sleep(500); // laisse arriver un éventuel doublon
        assertThat(mine()).as("e-mail reçu").hasSize(1);
        return mine().getFirst();
    }

    private void assertNothingSent() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where payload_json::text like ?",
                Long.class, "%" + recipient + "%")).isZero();
        Thread.sleep(2_500); // plus d'un tour du relais (1 s)
        assertThat(mine()).as("aucun e-mail").isEmpty();
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
}
