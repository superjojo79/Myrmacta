package be.myrmacta.infrastructure.demo;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.InboundAttachment;
import be.myrmacta.domain.connector.port.in.ManageConnectorInstanceUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.user.port.in.RecordUserLoginUseCase;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §128 (D-VIDEO-MAIL, D-VIDEO-AGENT, D-VIDEO-DEADLINE) — paquet de la capsule vidéo « Digital Mail Room » (#56), profil
 * {@code demo-video-mailroom}, après la mail room (installée sans ses courriers d'exemple) :
 * <ul>
 *   <li>la boîte IMAP de la démo, relevée sur le GreenMail local (D-IMAP-LOCAL-LOOPBACK) : les e-mails que le tournage
 *       envoie arrivent par le vrai connecteur ;</li>
 *   <li>l'agent IA de la capsule (sujet du JWT M2M) connu de l'annuaire et membre de la file de tri ;</li>
 *   <li>deux factures arrivées « il y a 25 jours », dont l'échéance tombe l'une dans 3 jours, l'autre avant-hier : le vrai
 *       job des échéances envoie le rappel et fait l'escalade.</li>
 * </ul>
 */
@Component
@Profile(VideoMailRoomDemoPack.PROFILE)
public class VideoMailRoomDemoPack implements DemoPack {

    static final String PROFILE = "demo-video-mailroom";
    static final int ORDER = 30;
    static final String TRUSTED_AUTHSERV_ID = "mx.demo.example";
    private static final Duration ROUTING_TIMEOUT = Duration.ofSeconds(120);
    private static final String ACTOR = "demo-video";
    private static final AccessContext.User ADMIN = AccessContext.user(ACTOR, Set.of("TENANT_ADMIN", "WORKFLOW_ADMIN"));
    private static final Logger log = LoggerFactory.getLogger(VideoMailRoomDemoPack.class);

    /** Les factures du chapitre « échéance » (fournisseur connu de la table de routage) : jours avant (négatif : après) l'échéance. */
    record DueInvoice(String number, String supplier, int dueInDays) {}

    static final List<DueInvoice> DUE = List.of(
            new DueInvoice("2026-098", "compta@fournisseur.example", 3),
            new DueInvoice("2026-091", "compta@fournisseur.example", -2));

    private final MailRoomDemoPack mailRoom;
    private final ManageConnectorInstanceUseCase connectors;
    private final ConnectorInstanceRepository instances;
    private final RecordUserLoginUseCase directory;
    private final UserGroupsUseCase groups;
    private final DossierRepository dossiers;
    private final TenantProperties tenant;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String imapHost;
    private final int imapPort;
    private final String agentSubject;
    private final ProcessEngine engine;
    private final ProposeDraftUseCase drafts;
    private final JdbcTemplate jdbc;

    @Autowired
    public VideoMailRoomDemoPack(MailRoomDemoPack mailRoom, ManageConnectorInstanceUseCase connectors,
            ConnectorInstanceRepository instances, RecordUserLoginUseCase directory,
            UserGroupsUseCase groups, DossierRepository dossiers, TenantProperties tenant, ObjectMapper json,
            PlatformTransactionManager transactionManager, ProcessEngine engine, JdbcTemplate jdbc, ProposeDraftUseCase drafts,
            @Value("${app.demo.video.imap-host:localhost}") String imapHost,
            @Value("${app.demo.video.imap-port:3143}") int imapPort,
            @Value("${app.demo.video.agent-subject:agent-ia-tri}") String agentSubject) {
        this(mailRoom, connectors, instances, directory, groups, dossiers, tenant, json, transactionManager, engine, jdbc, drafts, imapHost,
                imapPort, agentSubject, Clock.systemDefaultZone());
    }

    VideoMailRoomDemoPack(MailRoomDemoPack mailRoom, ManageConnectorInstanceUseCase connectors,
            ConnectorInstanceRepository instances, RecordUserLoginUseCase directory,
            UserGroupsUseCase groups, DossierRepository dossiers, TenantProperties tenant, ObjectMapper json,
            PlatformTransactionManager transactionManager, ProcessEngine engine, JdbcTemplate jdbc, ProposeDraftUseCase drafts,
            String imapHost, int imapPort, String agentSubject, Clock clock) {
        this.drafts = drafts;
        this.engine = engine;
        this.jdbc = jdbc;
        this.mailRoom = mailRoom;
        this.connectors = connectors;
        this.instances = instances;
        this.directory = directory;
        this.groups = groups;
        this.dossiers = dossiers;
        this.tenant = tenant;
        this.json = json;
        this.transaction = new TransactionTemplate(transactionManager);
        this.imapHost = imapHost;
        this.imapPort = imapPort;
        this.agentSubject = agentSubject;
        this.clock = clock;
    }

    @Override
    public String code() {
        return "video-mailroom";
    }

    @Override
    public int order() {
        return ORDER;
    }

    /** Le dernier effet du paquet : le brouillon de la table de routage. */
    @Override
    public boolean installed() {
        return routingDraftProposed();
    }

    @Override
    /**
     * Chaque étape est idempotente (revue de clôture de #56) : une installation interrompue (routage trop lent, par
     * exemple) puis relancée reprend là où elle s'est arrêtée, sans seconde boîte IMAP qui relèverait la même boîte.
     */
    public void install() {
        DemoExample example = mailRoom.example();
        if (instance("imap-inbound", mailboxName(example)).isEmpty()) {
            mailbox(example);
        }
        agent();
        ConnectorInstanceData simulated = instance("mail-deposit", mailRoom.simulatedMailboxName())
                .orElseGet(mailRoom::simulatedMailbox);
        LocalDate today = LocalDate.now(clock);
        Set<String> present = dossiers.findByDossierTypeCode(tenant.id(), "FACTURE").stream()
                .map(d -> String.valueOf(d.fields().get("objet"))).collect(Collectors.toSet());
        for (DueInvoice invoice : DUE) {
            if (!present.contains(subject(invoice))) {
                dueInvoice(simulated, invoice, today.plusDays(invoice.dueInDays()), example);
            }
        }
        // La conclusion : une règle de plus dans la table de routage, proposée en brouillon (DMN 1.3, que l'éditeur ouvre).
        if (!routingDraftProposed()) {
            String xml = StatesDemoPack.routingWithPrinterRule(example);
            transaction.executeWithoutResult(status -> drafts.proposeDmn(ADMIN, Provenance.MCP, StatesDemoPack.ROUTING, xml,
                    example.text("Factures de l'imprimerie : comptabilité, 30 jours", "Printer invoices: accounting, 30 days"),
                    false));
        }
        log.info("demo-video-mailroom: IMAP mailbox on {}:{}, agent {} in the sorting queue, {} invoices near their due date, "
                + "routing draft", imapHost, imapPort, agentSubject, DUE.size());
    }

    private boolean routingDraftProposed() {
        Integer count = jdbc.queryForObject("select count(*) from dmn_draft where decision_key = ?", Integer.class,
                StatesDemoPack.ROUTING);
        return count != null && count > 0;
    }

    private Optional<ConnectorInstanceData> instance(String type, String name) {
        return instances.listByTenantId(tenant.id()).stream()
                .filter(i -> i.connectorType().equals(type) && i.name().equals(name)).findFirst();
    }

    private static String mailboxName(DemoExample example) {
        return example.text("Boîte courrier (courrier@demo.example)", "Mailbox (courrier@demo.example)");
    }

    /** La boîte IMAP de l'exemple, pointée sur le GreenMail local (sans chiffrement : boucle locale, profil local). */
    private void mailbox(DemoExample example) {
        JsonNode imap = example.json("config/connecteur-imap.exemple.json");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = new HashMap<>(json.convertValue(imap.get("config"), Map.class));
        config.put("host", imapHost);
        config.put("port", imapPort);
        config.put("security", "PLAIN");
        config.put("username", "courrier");
        config.put("password", "demo");
        config.put("pollSeconds", 30);
        config.put("trustedAuthservId", TRUSTED_AUTHSERV_ID);
        connectors.create(new ManageConnectorInstanceUseCase.CreateCommand(tenant.id(), mailboxName(example),
                imap.get("connectorType").asString(), true, config), ADMIN);
    }

    /** L'agent IA de la capsule : connu de l'annuaire (sinon il ne peut rejoindre un groupe), membre de la file de tri. */
    private void agent() {
        directory.record(agentSubject, mailRoom.example().text("Agent IA (modèle local)", "AI agent (local model)"), null,
                Set.of("API_SERVICE"));
        boolean member = groups.list().stream()
                .anyMatch(g -> g.code().equals("COURRIER") && g.memberIds().contains(agentSubject));
        if (!member) {
            groups.addMember("COURRIER", agentSubject);
        }
    }

    /** Arrivée par le vrai pipeline (routage, dossier « facture »), puis échéance rapprochée (D-VIDEO-DEADLINE). */
    private void dueInvoice(ConnectorInstanceData mailbox, DueInvoice invoice, LocalDate due, DemoExample example) {
        Set<UUID> before = invoiceIds();
        String subject = subject(invoice);
        mailRoom.email(mailbox, "<demo-" + invoice.number() + "@" + invoice.supplier().substring(invoice.supplier().indexOf('@') + 1)
                + ">", invoice.supplier(), subject, List.of(new InboundAttachment(
                example.text("facture-" + invoice.number() + ".pdf", "invoice-" + invoice.number() + ".pdf"), "application/pdf",
                MailRoomDemoPack.pdf(subject))));
        List<UUID> created = awaitRouting(before, invoice);
        transaction.executeWithoutResult(status -> {
            Dossier current = dossiers.findById(created.getFirst()).orElseThrow();
            Map<String, Object> fields = new HashMap<>(current.fields());
            fields.put("echeance", due.toString());
            dossiers.save(new Dossier(current.id(), current.tenantId(), current.reference(), current.dossierTypeCode(),
                    current.label(), current.status(), fields, current.currentWorkflowLinkId(), current.version(),
                    current.createdBy(), current.createdAt(), current.updatedBy(), current.updatedAt(), current.closedBy(),
                    current.closedAt(), current.purgeEligibleAt()));
        });
    }

    /**
     * Le routage du courrier est asynchrone. Exécuteur actif (la démo réelle) : on attend. Exécuteur coupé (les tests) : on
     * exécute les jobs, une fois l'outbox vidée (sinon un job croise la confirmation du lien et perd son verrou optimiste).
     */
    private List<UUID> awaitRouting(Set<UUID> before, DueInvoice invoice) {
        Instant deadline = Instant.now().plus(ROUTING_TIMEOUT);
        RuntimeException last = null;
        while (Instant.now().isBefore(deadline)) {
            List<UUID> created = invoiceIds().stream().filter(id -> !before.contains(id)).toList();
            if (!created.isEmpty()) {
                return created;
            }
            var executor = engine.getProcessEngineConfiguration().getAsyncExecutor();
            Integer pending = jdbc.queryForObject("select count(*) from outbox_event where status = 'PENDING'", Integer.class);
            if ((executor == null || !executor.isActive()) && pending != null && pending == 0) {
                ManagementService management = engine.getManagementService();
                for (var job : management.createJobQuery().list()) {
                    try {
                        management.executeJob(job.getId());
                    } catch (RuntimeException ex) {
                        last = ex;
                    }
                }
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("demo-video-mailroom: interrupted", ex);
            }
        }
        throw new IllegalStateException("demo-video-mailroom: invoice " + invoice.number() + " was not routed to a FACTURE dossier", last);
    }

    private String subject(DueInvoice invoice) {
        return mailRoom.example().text("Facture " + invoice.number(), "Invoice " + invoice.number());
    }

    private Set<UUID> invoiceIds() {
        return dossiers.findByDossierTypeCode(tenant.id(), "FACTURE").stream().map(Dossier::id).collect(Collectors.toSet());
    }
}
