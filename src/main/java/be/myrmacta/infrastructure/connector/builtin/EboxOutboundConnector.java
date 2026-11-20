package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.ServiceConnector;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.ebox.model.EboxDocument;
import be.myrmacta.domain.ebox.model.EboxLanguage;
import be.myrmacta.domain.ebox.model.EboxOutgoingMessage;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry.Outcome;
import be.myrmacta.domain.ebox.model.EboxSendOutcome;
import be.myrmacta.domain.ebox.model.EboxSenderAccount;
import be.myrmacta.domain.ebox.model.FallbackChannel;
import be.myrmacta.domain.ebox.model.SendEboxCommand;
import be.myrmacta.domain.ebox.port.in.SendEboxMessageUseCase;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TODO §155 (D-EBOX-SEND-CHANNELS, lot 2) — type de connecteur {@value #TYPE} : le compte expéditeur eBox d'un tenant (URL de
 * publication, de la fédération et du recommandé, mandat, client OAuth v5 et clé du certificat, chiffrée au repos). La
 * configuration est celle que lit {@link EboxOutboundSettings} à l'envoi, contrôlée ici à l'enregistrement par les mêmes règles.
 *
 * <p>Côté SERVICE, une seule opération : envoyer la pièce principale d'un dossier par {@link SendEboxMessageUseCase}. Elle n'est
 * ouverte qu'à une étape de process dont la requête est construite par le BPMN déployé (brique « Envoyer en eBox ») : ni l'API
 * d'administration, ni l'API M2M, ni une variable {@code connectorInput} posée par un utilisateur n'envoient un message. Le
 * document doit être un document produit ({@code PRODUCED_DOC}) du dossier de l'étape, et un PDF.
 *
 * <p>La voie citoyen n'a pas encore de réglage par instance : l'API de l'eBox citoyen n'est pas connue (stub,
 * D-EBOX-SEND-CITIZEN-STUB) ; ses champs s'ajouteront avec le vrai adaptateur.
 */
@Component
public class EboxOutboundConnector implements ConnectorDefinition, ServiceConnector {

    static final String TYPE = EboxOutboundSettings.TYPE;
    static final String PDF = "application/pdf";
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^\\p{L}\\p{N} ._()'-]");
    private static final int MAX_FILE_NAME = 100;
    private static final Logger log = LoggerFactory.getLogger(EboxOutboundConnector.class);

    private final SendEboxMessageUseCase send;
    private final DossierElementRepository elements;
    private final DocumentStoragePort storage;
    private final OutboundUrlGuard guard;

    public EboxOutboundConnector(SendEboxMessageUseCase send, DossierElementRepository elements, DocumentStoragePort storage,
            OutboundUrlGuard guard) {
        this.send = Objects.requireNonNull(send, "send");
        this.elements = Objects.requireNonNull(elements, "elements");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getLabel() {
        return "eBox (envoi entreprise et citoyen)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.SERVICE;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField("publicationUrl", "Registre de messages (publication)", ConfigFieldType.URL, true, null, List.of(),
                        "e-Box REST 3.8, https, ex. https://services.socialsecurity.be/REST/ebox/enterprise/messageRegistry/v3"),
                new ConfigField("federationUrl", "e-Box Federation", ConfigFieldType.URL, true, null, List.of(),
                        "Préférence eBox des entreprises (Federation 1.4), https, ex. "
                                + "https://services.socialsecurity.be/REST/ebox/enterprise/federation/v1"),
                new ConfigField("registeredMailUrl", "Registered Mail (recommandé)", ConfigFieldType.URL, false, null, List.of(),
                        "Registered Mail 1.1, https. Vide : un envoi recommandé est refusé"),
                new ConfigField("onBehalfOf", "Mandat (BCE représenté)", ConfigFieldType.STRING, false, null, List.of(),
                        "Numéro BCE de la commune quand Myrmacta publie pour elle en prestataire technique (mandat Mahis) ; "
                                + "vide sinon"),
                new ConfigField("allowedHostSuffixes", "Domaines autorisés", ConfigFieldType.STRING, false,
                        EboxOutboundSettings.DEFAULT_SUFFIXES, List.of(),
                        "Le jeton n'est envoyé qu'aux hôtes qui finissent par l'un de ces domaines, pris parmi "
                                + "socialsecurity.be, fgov.be et belgium.be"),
                new ConfigField("oauthTokenUrl", "OAuth v5 token URL", ConfigFieldType.URL, true, null, List.of(),
                        "ex. https://services.socialsecurity.be/REST/oauth/v5/token"),
                new ConfigField("oauthClientId", "OAuth v5 client id", ConfigFieldType.STRING, true, null, List.of(),
                        "Client obtenu à l'onboarding Document Sender, 200 caractères au plus"),
                new ConfigField("oauthPrivateKey", "Clé privée du certificat", ConfigFieldType.SECRET_TEXTAREA, true, null, List.of(),
                        "PEM PKCS#8 RSA. Chiffrée au repos"),
                new ConfigField("oauthCertThumbprint", "Empreinte du certificat", ConfigFieldType.STRING, true, null, List.of(),
                        "SHA-1 (40) ou SHA-256 (64) en hexadécimal"),
                new ConfigField("oauthScope", "OAuth v5 scope (publication)", ConfigFieldType.STRING, false,
                        EboxOutboundSettings.DEFAULT_SCOPE, List.of(), null),
                new ConfigField("registeredMailScope", "OAuth v5 scope (recommandé)", ConfigFieldType.STRING, false,
                        EboxOutboundSettings.DEFAULT_REGISTERED_MAIL_SCOPE, List.of(), null));
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        try {
            EboxOutboundSettings.validate(config, guard);
        } catch (IllegalStateException invalid) {
            throw new ConnectorValidationException(invalid.getMessage());
        }
        Map<String, Object> oauth = new HashMap<>(config);
        oauth.put("oauthClientAuthMethod", OAuth2TokenProvider.METHOD_JWT);
        OAuth2ConfigSupport.validate(oauth, guard);
    }

    @Override
    public Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> input) {
        throw new IllegalArgumentException(TYPE + ": sending is only available from the « Envoyer en eBox » process step");
    }

    /**
     * Envoie le document et rend les variables de process de la brique (voir {@code EboxSendDelegate}). Une issue incertaine
     * ({@link be.myrmacta.domain.ebox.model.EboxSendUncertainException}) ou un refus remonte : l'étape échoue et se rejoue avec la
     * même clé d'idempotence.
     */
    @Override
    public Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> input, ConnectorCallContext context) {
        Objects.requireNonNull(instance, "instance");
        if (context == null || !context.fromProcess() || !context.requestBuiltByProcess() || context.dossierId() == null) {
            return execute(instance, input);
        }
        Map<String, Object> params = input == null ? Map.of() : input;
        UUID dossierId = context.dossierId();
        EboxRecipient recipient = switch (text(params, "recipientType")) {
            case "ENTREPRISE" -> new EboxRecipient.Enterprise(text(params, "recipient"));
            case "CITOYEN" -> new EboxRecipient.Citizen(text(params, "recipient"));
            default -> throw new IllegalArgumentException(TYPE + ": recipientType must be ENTREPRISE or CITOYEN");
        };
        FallbackChannel fallback = switch (text(params, "fallbackChannel")) {
            case "PAPER" -> FallbackChannel.PAPER;
            case "EMAIL" -> FallbackChannel.EMAIL;
            default -> throw new IllegalArgumentException(TYPE + ": fallbackChannel must be PAPER or EMAIL");
        };
        EboxOutgoingMessage message = EboxOutgoingMessage.builder(recipient, text(params, "senderApplicationId"),
                        text(params, "messageTypeId"), EboxLanguage.of(text(params, "language")), text(params, "subject"))
                .document(document(instance.tenantId(), dossierId, text(params, "documentRef")))
                .registered(Boolean.TRUE.equals(params.get("registered")))
                .build();
        EboxSendOutcome outcome = send.send(new SendEboxCommand(new EboxSenderAccount(instance.id()), message,
                text(params, "idempotencyKey"), dossierId, fallback));
        log.info("{}: instance {} sent dossier {} through the {} route: {}", TYPE, instance.id(), dossierId, message.route(),
                outcome.outcome());
        return variables(outcome);
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
        if (outcome.proof() != null) {
            result.put("eboxPreuveDepot", outcome.proof().json());
        }
        return result;
    }

    /** Pièce principale : un document produit de ce dossier, en PDF. Les messages ne citent jamais la référence. */
    private EboxDocument document(String tenantId, UUID dossierId, String ref) {
        DossierElement element = elements.findByDossierIdOrderByAddedAtAsc(dossierId).stream()
                .filter(e -> e.type() == ElementType.PRODUCED_DOC && ref.equals(e.storageRef()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(TYPE + ": the document is not a produced document of this dossier "
                        + "(generate it, then attach it to the dossier first)"));
        byte[] content;
        try {
            content = storage.retrieve(tenantId, ref);
        } catch (RuntimeException unreadable) {
            throw new IllegalStateException(TYPE + ": the document could not be read");
        }
        if (content == null || content.length < PDF_MAGIC.length
                || !Arrays.equals(Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC)) {
            throw new IllegalArgumentException(TYPE + ": the document is not a PDF");
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
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf") ? name : name + ".pdf";
    }

    private static String text(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? "" : String.valueOf(value).strip();
    }
}
