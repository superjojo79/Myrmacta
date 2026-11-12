package be.myrmacta.infrastructure.demo;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierStatusUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §127 (D-DEMO-BACKDATE, lot 1d de #55) — paquet « états » de la démo, installé après la mail room et l'onboarding,
 * pour que la supervision, les rapports, les incidents et les brouillons aient quelque chose à montrer :
 * <ul>
 *   <li>objectifs SLA sur les types FACTURE (72 h) et COURRIER (24 h) ;</li>
 *   <li>un historique de factures sur quatre semaines : traitées dans le délai, en retard, ouvertes dans le délai et
 *       ouvertes au SLA dépassé. Le SLA se mesure depuis {@code createdAt} : ces dossiers, créés par les cas d'usage, sont
 *       ensuite antidatés par {@link DossierRepository} (seul écart aux cas d'usage, limité à la démo) ;</li>
 *   <li>un incident rejouable : une arrivée dont l'appel au SIRH tombe sur une coupure réseau ;</li>
 *   <li>un brouillon de la table de routage proposé par MCP (une règle de plus), en attente de publication.</li>
 * </ul>
 * Build {@code embedded-db} seulement.
 */
@Component
@Profile("demo")
public class StatesDemoPack implements DemoPack {

    static final int ORDER = 40;
    static final int INVOICE_SLA_HOURS = 72;
    static final int MAIL_SLA_HOURS = 24;
    private static final String ACTOR = "demo-etats";
    private static final AccessContext.User ADMIN = AccessContext.user(ACTOR, Set.of("TENANT_ADMIN", "WORKFLOW_ADMIN"));
    static final String ROUTING = "routageCourrier";
    /** Alias for assertions in embedded tests — source of truth : {@link be.myrmacta.domain.workflow.DmnXmlVersions}. */
    static final String DMN_11 = be.myrmacta.domain.workflow.DmnXmlVersions.DMN_11_NAMESPACE;
    static final String DMN_13 = be.myrmacta.domain.workflow.DmnXmlVersions.DMN_13_NAMESPACE;
    private static final Logger log = LoggerFactory.getLogger(StatesDemoPack.class);

    /** Une facture de l'historique : il y a {@code daysAgo} jours, traitée en {@code handledInHours} (null : ouverte). */
    record Invoice(String supplier, String subject, double daysAgo, Integer handledInHours) {}

    static final List<Invoice> HISTORY = List.of(
            new Invoice("compta@fournisseur.example", "Facture 2026-081", 28, 30),
            new Invoice("factures@imprimerie.example", "Facture IMP-1204", 25, 50),
            new Invoice("compta@fournisseur.example", "Facture 2026-088", 22, 96),
            new Invoice("billing@cloud.example", "Invoice CL-77810", 19, 20),
            new Invoice("compta@fournisseur.example", "Facture 2026-093", 15, 70),
            new Invoice("factures@imprimerie.example", "Facture IMP-1231", 12, 120),
            new Invoice("billing@cloud.example", "Invoice CL-78112", 9, 40),
            new Invoice("compta@fournisseur.example", "Facture 2026-101", 6, 26),
            new Invoice("factures@imprimerie.example", "Facture IMP-1250", 5, null),
            new Invoice("compta@fournisseur.example", "Facture 2026-106", 4, null),
            new Invoice("billing@cloud.example", "Invoice CL-78590", 1, null));

    private final DemoExample mailRoomExample;
    private final TenantProperties tenant;
    private final DossierTypeRepository types;
    private final UpdateDossierTypeUseCase updateType;
    private final CreateDossierUseCase createDossier;
    private final UpdateDossierStatusUseCase updateStatus;
    private final DossierRepository dossiers;
    private final ProposeDraftUseCase drafts;
    private final OnboardingDemoPack onboarding;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public StatesDemoPack(@Value("${app.demo.mailroom.dir:exemples/digital-mail-room}") String mailRoomDir,
            @Value("${app.demo.locale:fr}") String locale, ObjectMapper json, TenantProperties tenant, DossierTypeRepository types, UpdateDossierTypeUseCase updateType,
            CreateDossierUseCase createDossier, UpdateDossierStatusUseCase updateStatus, DossierRepository dossiers,
            ProposeDraftUseCase drafts, OnboardingDemoPack onboarding, PlatformTransactionManager transactionManager) {
        this.mailRoomExample = new DemoExample(Path.of(mailRoomDir), locale, json);
        this.tenant = tenant;
        this.types = types;
        this.updateType = updateType;
        this.createDossier = createDossier;
        this.updateStatus = updateStatus;
        this.dossiers = dossiers;
        this.drafts = drafts;
        this.onboarding = onboarding;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = Clock.systemUTC();
    }

    @Override
    public String code() {
        return "etats";
    }

    @Override
    public int order() {
        return ORDER;
    }

    /** Installé dès que la facture porte son objectif SLA : c'est la première chose que fait le paquet. */
    @Override
    public boolean installed() {
        return types.findByCode(tenant.id(), "FACTURE").map(type -> type.slaTargetHours() != null).orElse(false);
    }

    @Override
    public void install() {
        slaTarget("COURRIER", MAIL_SLA_HOURS);
        slaTarget("FACTURE", INVOICE_SLA_HOURS);
        Instant now = clock.instant();
        for (Invoice invoice : HISTORY) {
            invoice(invoice, now);
        }
        onboarding.seedIncident();
        proposeRoutingDraft();
        log.info("demo: states installed ({} invoices over four weeks, 1 incident, 1 MCP draft)", HISTORY.size());
    }

    private void slaTarget(String code, int hours) {
        DossierType type = types.findByCode(tenant.id(), code)
                .orElseThrow(() -> new IllegalStateException("demo: type " + code + " missing (mail room pack first)"));
        transaction.executeWithoutResult(status -> updateType.update(new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                tenant.id(), type.code(), type.label(), type.workflowKey(), type.active(), type.allowUnknownFields(),
                type.fieldDefinitions(), type.retentionDays(), type.retentionNever(), hours, type.documentTemplateKeys(),
                type.pieceTypes()), ADMIN));
    }

    /** Créée et menée par les cas d'usage, puis antidatée (D-DEMO-BACKDATE). */
    private void invoice(Invoice invoice, Instant now) {
        String subject = mailRoomExample.text(invoice.subject(), invoice.subject().replace("Facture", "Invoice"));
        Dossier created = transaction.execute(status -> createDossier.create(new CreateDossierUseCase.CreateDossierCommand(
                tenant.id(), "FACTURE", subject + " — " + invoice.supplier(),
                Map.of("expediteur", invoice.supplier(), "objet", subject), ACTOR), ADMIN));
        status(created, DossierStatus.OUVERT);
        status(created, invoice.handledInHours() == null ? DossierStatus.EN_COURS : DossierStatus.CLOTURE);

        Instant createdAt = now.minus(Duration.ofMinutes(Math.round(invoice.daysAgo() * 24 * 60)));
        Instant closedAt = invoice.handledInHours() == null ? null : createdAt.plus(Duration.ofHours(invoice.handledInHours()));
        transaction.executeWithoutResult(status -> {
            Dossier current = dossiers.findById(created.id()).orElseThrow();
            dossiers.save(new Dossier(current.id(), current.tenantId(), current.reference(), current.dossierTypeCode(),
                    current.label(), current.status(), current.fields(), current.currentWorkflowLinkId(), current.version(),
                    current.createdBy(), createdAt, current.updatedBy(), closedAt == null ? createdAt : closedAt,
                    current.closedBy(), closedAt, current.purgeEligibleAt()));
        });
    }

    private void status(Dossier dossier, DossierStatus target) {
        transaction.executeWithoutResult(status -> updateStatus.update(
                new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(dossier.id(), target, ACTOR, null), ADMIN));
    }

    /** Une règle de plus dans la table de routage : les factures de l'imprimerie vont aussi à la comptabilité. */
    private void proposeRoutingDraft() {
        String xml = routingWithPrinterRule();
        transaction.executeWithoutResult(status -> drafts.proposeDmn(ADMIN, Provenance.MCP, ROUTING, xml,
                mailRoomExample.text("Factures de l'imprimerie : comptabilité, 30 jours",
                        "Printer invoices: accounting, 30 days"), false));
    }

    String routingWithPrinterRule() {
        return routingWithPrinterRule(mailRoomExample);
    }

    /** Aussi utilisé par la capsule vidéo (TODO §128) : la règle ajoutée « sans code » de sa conclusion. */
    static String routingWithPrinterRule(DemoExample mailRoomExample) {
        String xml = mailRoomExample.xml("routage-courrier.dmn");
        String description = mailRoomExample.text("Facture de l'imprimerie : la comptabilité, 30 jours.",
                "Invoice from the printer: accounting, 30 days.");
        String rule = """
                      <rule id="regleFactureImprimerie">
                        <description>%s</description>
                        <inputEntry id="regleFactureImprimerieCanal"><text>"EMAIL"</text></inputEntry>
                        <inputEntry id="regleFactureImprimerieDomaine"><text>"imprimerie.example"</text></inputEntry>
                        <outputEntry id="regleFactureImprimerieAction"><text>"OUVRIR"</text></outputEntry>
                        <outputEntry id="regleFactureImprimerieService"><text>"COMPTABILITE"</text></outputEntry>
                        <outputEntry id="regleFactureImprimerieType"><text>"FACTURE"</text></outputEntry>
                        <outputEntry id="regleFactureImprimerieProcessus"><text>"traiterFacture"</text></outputEntry>
                        <outputEntry id="regleFactureImprimerieDelai"><text>30</text></outputEntry>
                      </rule>
                """.formatted(description);
        int end = xml.indexOf("</decisionTable>");
        if (end < 0) {
            throw new IllegalStateException("demo: routage-courrier.dmn has no decision table");
        }
        int lineStart = xml.lastIndexOf('\n', end) + 1;
        // TODO §130 : l'exemple reste en 1.1 ; DmnXmlVersions normalise pour le brouillon (et l'éditeur à l'ouverture).
        return be.myrmacta.domain.workflow.DmnXmlVersions.to13(
                xml.substring(0, lineStart) + rule + xml.substring(lineStart));
    }
}
