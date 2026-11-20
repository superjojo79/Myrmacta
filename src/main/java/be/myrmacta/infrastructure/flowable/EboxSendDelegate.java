package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.ebox.model.EboxDocument;
import be.myrmacta.domain.ebox.model.EboxLanguage;
import be.myrmacta.domain.ebox.model.EboxOutgoingMessage;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxSendOutcome;
import be.myrmacta.domain.ebox.model.EboxSenderAccount;
import be.myrmacta.domain.ebox.model.FallbackChannel;
import be.myrmacta.domain.ebox.model.SendEboxCommand;
import be.myrmacta.domain.ebox.port.in.SendEboxMessageUseCase;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.portal.model.PortalRecipientSource;
import be.myrmacta.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.ebox.EboxRecipientHmac;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.bpmn.model.ServiceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TODO §155 (lot 2, D-EBOX-SEND-BLOCK) — brique « Envoyer en eBox », seul chemin d'envoi vers l'eBox : elle appelle
 * {@link SendEboxMessageUseCase} elle-même, le connecteur {@code ebox-outbound} ne porte que le compte expéditeur et refuse tout
 * appel (revue H1 : « Appeler un connecteur » n'envoie jamais un message eBox).
 *
 * <p>Tous ses paramètres sont des attributs littéraux du BPMN déployé, jamais des variables de même nom : le compte
 * ({@code ebox-outbound}, activé), la source du destinataire et celle du document ({@code dossierField:<champ>} ou
 * {@code variable:<nom>}), le type de destinataire, l'objet, les identifiants d'application et de type de message attribués à
 * l'onboarding, la langue (littérale ou source), le recommandé et la voie de secours. Le document doit être un document produit
 * ({@code PRODUCED_DOC}) du dossier courant, en PDF. L'étape doit être asynchrone et non multi-instance (revue M1), contrôlé au
 * déploiement et ici.
 *
 * <p>Clé d'idempotence : {@code myr1-} + SHA-256 de (dossier, étape, document, tour de boucle hérité, référence du
 * destinataire : BCE, ou HMAC du NISS, jamais le NISS). Un rejeu de l'étape ne publie jamais deux fois ; chaque destinataire
 * d'une boucle a sa clé (revue H2).
 *
 * <p>Variables posées : {@code eboxIssue} ({@code ENVOYE}, {@code DEJA_ENVOYE}, {@code VOIE_SECOURS}), {@code eboxLivraison},
 * {@code eboxSecoursRequis}, {@code eboxVoieSecours}, {@code eboxEnvoiMessageId}, {@code eboxEnvoiRejoue}. Dans un
 * sous-processus multi-instance, elles sont locales au tour : la passerelle du même sous-processus lit la valeur de son tour.
 * Une issue incertaine fait échouer l'étape (incident rejouable, même clé). La preuve de dépôt d'un recommandé est versée au
 * dossier une seule fois : un rejeu ne la verse que si aucune pièce du dossier ne porte déjà sa clé (revue B1).
 */
@Component("eboxSendTask")
class EboxSendDelegate implements JavaDelegate {

    static final String SENDER_TYPE = "ebox-outbound";
    static final String INSTANCE = "connectorInstanceId";
    static final String RECIPIENT = "eboxEnvoiDestinataire";
    static final String RECIPIENT_TYPE = "eboxEnvoiTypeDestinataire";
    static final String DOCUMENT = "eboxEnvoiDocument";
    static final String SUBJECT = "eboxEnvoiObjet";
    static final String APPLICATION = "eboxEnvoiApplication";
    static final String MESSAGE_TYPE = "eboxEnvoiTypeMessage";
    static final String LANGUAGE = "eboxEnvoiLangue";
    static final String REGISTERED = "eboxEnvoiRecommande";
    static final String FALLBACK = "eboxEnvoiSecours";
    static final String KEY_PREFIX = "myr1-";
    static final String LOOP_COUNTER = "loopCounter";
    static final String PDF = "application/pdf";
    static final String PROOF_KEY = "eboxIdempotencyKey";
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^\\p{L}\\p{N} ._()'-]");
    private static final int MAX_FILE_NAME = 100;
    private static final Logger log = LoggerFactory.getLogger(EboxSendDelegate.class);

    private final SendEboxMessageUseCase send;
    private final ConnectorInstanceRepository instances;
    private final PortalRecipientResolverPort sources;
    private final DossierElementRepository elements;
    private final DocumentStoragePort storage;
    private final AddDossierElementUseCase addElement;
    private final EboxRecipientHmac recipients;
    private final TenantProperties tenantProperties;

    EboxSendDelegate(SendEboxMessageUseCase send, ConnectorInstanceRepository instances, PortalRecipientResolverPort sources,
            DossierElementRepository elements, DocumentStoragePort storage, AddDossierElementUseCase addElement,
            EboxRecipientHmac recipients, TenantProperties tenantProperties) {
        this.send = Objects.requireNonNull(send, "send");
        this.instances = Objects.requireNonNull(instances, "instances");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.elements = Objects.requireNonNull(elements, "elements");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.addElement = Objects.requireNonNull(addElement, "addElement");
        this.recipients = Objects.requireNonNull(recipients, "recipients");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
    }

    @Override
    public void execute(DelegateExecution execution) {
        requireAsynchronousSingleStep(execution);
        String tenantId = tenantProperties.id();
        UUID instanceId = UUID.fromString(literal(execution, INSTANCE));
        ConnectorInstanceData instance = instances.findByIdAndTenantId(instanceId, tenantId)
                .orElseThrow(() -> new IllegalStateException("connector instance not found: " + instanceId));
        if (!SENDER_TYPE.equals(instance.connectorType())) {
            throw new IllegalStateException("connector instance " + instanceId + " is not an " + SENDER_TYPE + " instance");
        }
        if (!instance.enabled()) {
            throw new IllegalStateException("the eBox sender connector instance " + instanceId + " is disabled");
        }
        UUID dossierId = dossierId(execution);
        EboxRecipient recipient = switch (literal(execution, RECIPIENT_TYPE)) {
            case "ENTREPRISE" -> new EboxRecipient.Enterprise(resolve(execution, RECIPIENT, literal(execution, RECIPIENT), dossierId));
            case "CITOYEN" -> new EboxRecipient.Citizen(resolve(execution, RECIPIENT, literal(execution, RECIPIENT), dossierId));
            default -> throw new IllegalArgumentException(RECIPIENT_TYPE + " must be ENTREPRISE or CITOYEN");
        };
        FallbackChannel fallback = switch (literal(execution, FALLBACK)) {
            case "PAPER" -> FallbackChannel.PAPER;
            case "EMAIL" -> FallbackChannel.EMAIL;
            default -> throw new IllegalArgumentException(FALLBACK + " must be PAPER or EMAIL");
        };
        String language = literal(execution, LANGUAGE);
        if (PortalRecipientSource.hasSourcePrefix(language)) {
            language = resolve(execution, LANGUAGE, language, dossierId);
        }
        String documentRef = resolve(execution, DOCUMENT, literal(execution, DOCUMENT), dossierId);
        EboxOutgoingMessage message = EboxOutgoingMessage.builder(recipient, literal(execution, APPLICATION),
                        literal(execution, MESSAGE_TYPE), EboxLanguage.of(language), literal(execution, SUBJECT))
                .document(document(tenantId, dossierId, documentRef))
                .registered("OUI".equals(DeployedActivityParameters.getLiteral(execution, REGISTERED)))
                .build();
        String key = idempotencyKey(dossierId, execution.getCurrentActivityId(), documentRef,
                execution.getVariable(LOOP_COUNTER), recipients.reference(tenantId, recipient));
        EboxSendOutcome outcome = send.send(new SendEboxCommand(new EboxSenderAccount(instanceId), message, key, dossierId,
                fallback));
        log.info("eboxSendTask: dossier {} step {} sent through the {} route: {}{}", dossierId, execution.getCurrentActivityId(),
                message.route(), outcome.outcome(), outcome.replayed() ? " (replayed)" : "");
        if (outcome.proof() != null && (!outcome.replayed() || !proofAttached(dossierId, key))) {
            attachProof(execution, tenantId, dossierId, key, outcome.proof().json());
        }
        DelegateExecution scope = loopScope(execution);
        variables(outcome).forEach((name, value) -> {
            if (scope == null) {
                execution.setVariable(name, value);
            } else {
                scope.setVariableLocal(name, value);
            }
        });
    }

    /** Variables de process documentées (GUIDE-CONCEPTION-BPMN, brique « Envoyer en eBox »). */
    static Map<String, Object> variables(EboxSendOutcome outcome) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eboxIssue", switch (outcome.outcome()) {
            case PUBLISHED -> "ENVOYE";
            case DUPLICATE -> "DEJA_ENVOYE";
            case FALLBACK -> "VOIE_SECOURS";
            case UNCERTAIN -> throw new IllegalStateException("an uncertain eBox outcome is never returned to the process");
        });
        result.put("eboxLivraison", outcome.delivery().name());
        result.put("eboxSecoursRequis", outcome.delivery().needsFallback());
        result.put("eboxVoieSecours", outcome.fallbackChannel() == null ? "" : outcome.fallbackChannel().name());
        result.put("eboxEnvoiMessageId", outcome.providerMessageId() == null ? "" : outcome.providerMessageId());
        result.put("eboxEnvoiRejoue", outcome.replayed());
        return result;
    }

    /** Clé stable : dossier, étape, document, tour de boucle et référence du destinataire (BCE ou HMAC du NISS) ; 69 caractères. */
    static String idempotencyKey(UUID dossierId, String activityId, String documentRef, Object loop, String recipientReference) {
        String material = dossierId + "|" + activityId + "|" + documentRef + "|" + (loop == null ? "" : loop) + "|"
                + recipientReference;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Exécution du tour d'un sous-processus multi-instance englobant (celle qui porte {@code loopCounter} en local), sinon
     * {@code null} : les variables vont alors au process.
     */
    static DelegateExecution loopScope(DelegateExecution execution) {
        for (DelegateExecution current = execution; current != null; current = current.getParent()) {
            if (current.getVariableLocal(LOOP_COUNTER) != null) {
                return current;
            }
        }
        return null;
    }

    /** Revue M1 : l'appel à l'eBox ne tient jamais dans une complétion de tâche, et une brique multi-instance est refusée. */
    private static void requireAsynchronousSingleStep(DelegateExecution execution) {
        if (!(execution.getCurrentFlowElement() instanceof ServiceTask task) || !task.isAsynchronous()) {
            throw new IllegalStateException("the eBox send step must be an asynchronous service task (flowable:async=\"true\")");
        }
        if (task.getLoopCharacteristics() != null) {
            throw new IllegalStateException("the eBox send step must not be multi-instance; use a multi-instance sub-process");
        }
    }

    /** Pièce principale : un document produit de ce dossier, en PDF. Les messages ne citent jamais la référence. */
    private EboxDocument document(String tenantId, UUID dossierId, String ref) {
        DossierElement element = elements.findByDossierIdOrderByAddedAtAsc(dossierId).stream()
                .filter(e -> e.type() == ElementType.PRODUCED_DOC && ref.equals(e.storageRef()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(DOCUMENT + " is not a produced document of this dossier "
                        + "(generate it, then attach it to the dossier first)"));
        byte[] content;
        try {
            content = storage.retrieve(tenantId, ref);
        } catch (RuntimeException unreadable) {
            throw new IllegalStateException(DOCUMENT + ": the document could not be read");
        }
        if (content == null || content.length < PDF_MAGIC.length
                || !Arrays.equals(Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC)) {
            throw new IllegalArgumentException(DOCUMENT + ": the document is not a PDF");
        }
        return new EboxDocument(fileName(element.label()), PDF, content, true);
    }

    static String fileName(String label) {
        String nfc = Normalizer.normalize(label == null ? "" : label.strip(), Normalizer.Form.NFC);
        String name = NOT_ALLOWED.matcher(nfc).replaceAll("_").replaceFirst("^[. ]+", "");
        if (name.codePointCount(0, name.length()) > MAX_FILE_NAME) {
            name = name.substring(0, name.offsetByCodePoints(0, MAX_FILE_NAME)).strip();
        }
        if (name.isEmpty()) {
            name = "document";
        }
        return name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? name : name + ".pdf";
    }

    /**
     * Revue B1 : un rejeu ne verse pas une seconde preuve. Il la verse pourtant si elle manque : la tentative qui a déposé a pu
     * reculer après le dépôt (le journal reste, la pièce recule avec la transaction du process).
     */
    private boolean proofAttached(UUID dossierId, String key) {
        return elements.findByDossierIdOrderByAddedAtAsc(dossierId).stream()
                .anyMatch(e -> e.type() == ElementType.ATTACHMENT && e.metadata() != null
                        && key.equals(e.metadata().get(PROOF_KEY)));
    }

    /** Preuve de dépôt signée d'un recommandé, versée telle que reçue au dossier (pièce JSON), marquée par la clé de l'envoi. */
    private void attachProof(DelegateExecution execution, String tenantId, UUID dossierId, String key, String proof) {
        String ref = storage.store(tenantId, "application/json", proof.getBytes(StandardCharsets.UTF_8));
        String activity = execution.getCurrentActivityName() == null || execution.getCurrentActivityName().isBlank()
                ? execution.getCurrentActivityId() : execution.getCurrentActivityName();
        addElement.add(new AddDossierElementUseCase.AddCommand(dossierId, ElementType.ATTACHMENT,
                        "Preuve de dépôt eBox — " + activity, ref, Map.of("contentType", "application/json", PROOF_KEY, key), "system",
                        execution.getCurrentActivityId()),
                AccessContext.system("flowable-delegate"));
    }

    /** Valeur d'une source ; le message nomme la source, jamais la valeur (un NISS ne doit pas finir dans un incident). */
    private String resolve(DelegateExecution execution, String parameter, String literal, UUID dossierId) {
        PortalRecipientSource source = PortalRecipientSource.parse(literal)
                .orElseThrow(() -> new IllegalArgumentException(parameter + " must be dossierField:<name> or variable:<name>"));
        Object value = switch (source.kind()) {
            case PROCESS_VARIABLE -> execution.getVariable(source.name());
            case DOSSIER_FIELD -> sources.resolve(source, dossierId, execution.getProcessInstanceId()).orElse(null);
        };
        if (!(value instanceof CharSequence text) || text.toString().isBlank()) {
            throw new IllegalStateException(parameter + " source " + literal + " resolved to no text value");
        }
        return text.toString().strip();
    }

    private static String literal(DelegateExecution execution, String name) {
        String value = DeployedActivityParameters.getLiteral(execution, name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be a literal flowable:" + name + " attribute");
        }
        return value.strip();
    }

    private static UUID dossierId(DelegateExecution execution) {
        Object value = execution.getVariable(WorkflowVariables.DOSSIER_ID);
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException notAUuid) {
            throw new IllegalStateException("the eBox send step needs the dossierId of its process");
        }
    }
}
