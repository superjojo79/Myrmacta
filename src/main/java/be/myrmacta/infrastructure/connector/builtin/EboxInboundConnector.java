package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.InboundAttachment;
import be.myrmacta.domain.connector.model.PollingTriggerConnector;
import be.myrmacta.domain.connector.model.ServiceConnector;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.domain.mailroom.model.EboxTakenMessage;
import be.myrmacta.domain.mailroom.port.out.EboxReadJournal;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.config.InboundConnectorProperties;
import be.myrmacta.infrastructure.connector.builtin.EboxClient.Access;
import be.myrmacta.infrastructure.connector.builtin.EboxClient.EboxResourceException;
import be.myrmacta.infrastructure.connector.builtin.EboxClient.EboxUnauthorizedException;
import be.myrmacta.infrastructure.connector.builtin.EboxClient.MessagePage;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §150 — connecteur TRIGGER « eBox Enterprise » : lit, en <em>Document Consumer</em>, les partitions de l'eBox d'une
 * organisation qui sont dédiées à Myrmacta (D-EBOX-PARTITIONS) ; chaque message devient un courrier de la mail room (§113), avec
 * ses pièces passées aux contrôles (types, contenu, antivirus). Les registres de messages sont découverts par le Document
 * Provider Registry et le jeton ne part que vers des hôtes autorisés (D-EBOX-REGISTRY-HOSTS). Un message est inscrit au journal
 * dans la transaction qui crée le courrier (D-EBOX-JOURNAL) ; la lecture reprend à la veille du dernier jour lu entièrement
 * (D-EBOX-WINDOW). Rien n'est modifié dans l'eBox, sinon l'état « lu » que l'API pose au téléchargement de la pièce principale.
 *
 * <p>Côté SERVICE, une seule opération : {@code markActionExecuted} confirme l'action demandée par l'expéditeur d'un message que
 * cette instance a pris en charge (D-EBOX-ACTION).
 */
@Component
public class EboxInboundConnector implements ConnectorDefinition, PollingTriggerConnector, ServiceConnector {

    static final String TYPE = "ebox-inbound";
    static final String DEFAULT_SCOPE = "scope:document:management:consult:ws-eboxrestentreprise:documentconsumer";
    static final String DEFAULT_SUFFIXES = "socialsecurity.be,fgov.be,belgium.be";
    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 20;
    static final int MAX_PARTITIONS = 20;
    static final int MAX_ATTACHMENTS = 20;
    static final int DEFAULT_LOOKBACK_DAYS = 14;
    static final int MAX_LOOKBACK_DAYS = 90;
    static final ZoneId EBOX_ZONE = ZoneId.of("Europe/Brussels");
    static final String MARK_ACTION_EXECUTED = "markActionExecuted";

    /** Variables posées par la plateforme : un mapping ne peut pas les écraser. */
    static final Set<String> PLATFORM_VARIABLES = Set.of("eboxMessageId", "eboxRegistre", "eboxPartition", "eboxEntreprise",
            "eboxExpediteur", "eboxApplication", "eboxTypeMessage", "eboxObjet", "eboxRecuLe", "eboxExpireLe", "eboxRecommande",
            "eboxActionRequise", "eboxReponseAutorisee", "eboxDateLimiteReponse", "eboxCorps", "eboxPiecesNonImportees");

    private static final Pattern ENTERPRISE_NUMBER = Pattern.compile("[01]\\d{9}");
    private static final Pattern PARTITION = Pattern.compile("[A-Za-z0-9._:-]{1,100}");
    private static final Pattern REGISTRY_ID = Pattern.compile("[A-Za-z0-9._-]{1,50}");
    private static final Pattern MESSAGE_ID = Pattern.compile("[^\\p{Cntrl}]{1,200}");
    private static final Pattern HOST_SUFFIX = Pattern.compile("[a-z0-9-]+(\\.[a-z0-9-]+)+");
    private static final Set<String> LANGUAGES = Set.of("fr", "nl", "de");
    private static final Logger log = LoggerFactory.getLogger(EboxInboundConnector.class);

    private final EboxClient client;
    private final EboxReadJournal journal;
    private final SimpleJsonPathMapper mapper;
    private final ObjectMapper objectMapper;
    private final OutboundUrlGuard guard;
    private final InboundConnectorProperties inbound;
    private final OAuth2TokenProvider tokenProvider;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Autowired
    public EboxInboundConnector(EboxClient client, EboxReadJournal journal, SimpleJsonPathMapper mapper, ObjectMapper objectMapper,
            OutboundUrlGuard guard, ConnectorProperties connectorProperties, InboundConnectorProperties inbound,
            PlatformTransactionManager transactionManager) {
        this(client, journal, mapper, objectMapper, guard, inbound,
                new OAuth2TokenProvider(guard, objectMapper, connectorProperties, PinnedHttpClient.sender(), Clock.systemUTC()),
                new TransactionTemplate(transactionManager), Clock.systemUTC());
    }

    EboxInboundConnector(EboxClient client, EboxReadJournal journal, SimpleJsonPathMapper mapper, ObjectMapper objectMapper,
            OutboundUrlGuard guard, InboundConnectorProperties inbound, OAuth2TokenProvider tokenProvider,
            TransactionOperations transactions, Clock clock) {
        this.client = Objects.requireNonNull(client, "client");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.inbound = Objects.requireNonNull(inbound, "inbound");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getLabel() {
        return "eBox Enterprise (lecture, interrogation, action faite)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.BOTH;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField("enterpriseNumber", "Numéro BCE de l'eBox lue", ConfigFieldType.STRING, true, null, List.of(),
                        "Dix chiffres, sans point ni espace, ex. 0212345678"),
                new ConfigField("onBehalfOf", "Lecture par un prestataire technique", ConfigFieldType.BOOLEAN, false, "false", List.of(),
                        "Vrai : chaque appel porte l'en-tête On-Behalf-Of-Enterprise-Number (mandat enregistré dans Mahis)"),
                new ConfigField("partitions", "Partitions dédiées à Myrmacta", ConfigFieldType.STRING, true, null, List.of(),
                        "Identifiants des partitions lues, séparés par des virgules. Seules ces partitions sont lues ; le reste de "
                                + "l'eBox n'est jamais consulté (télécharger la pièce principale marque le message comme lu)"),
                new ConfigField("registryUrl", "Document Provider Registry", ConfigFieldType.URL, true, null, List.of(),
                        "https, ex. https://services.socialsecurity.be/REST/ebox/enterprise/federation/documentProviderRegistry/v1"),
                new ConfigField("allowedHostSuffixes", "Domaines autorisés", ConfigFieldType.STRING, false, DEFAULT_SUFFIXES, List.of(),
                        "Le jeton n'est envoyé qu'aux hôtes qui finissent par l'un de ces domaines (registre, registres de messages, "
                                + "jeton)"),
                new ConfigField("initialLookbackDays", "Premier passage : jours relus", ConfigFieldType.INTEGER, false,
                        String.valueOf(DEFAULT_LOOKBACK_DAYS), List.of(), "De 1 à " + MAX_LOOKBACK_DAYS),
                new ConfigField("language", "Langue préférée", ConfigFieldType.SELECT, false, "fr", List.of("fr", "nl", "de"),
                        "Langue de l'objet et du corps, quand le message en a plusieurs"),
                new ConfigField("pollSeconds", "Intervalle (secondes)", ConfigFieldType.INTEGER, false, "900", List.of(), null),
                new ConfigField("processKey", "Process key", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("dossierTypeCode", "Dossier type", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("mapping", "Mapping", ConfigFieldType.STRING, false, null, List.of(),
                        "Objet JSON {fields, variables} de chemins sur {\"message\": {…}, \"objet\": texte, \"expediteur\": BCE, "
                                + "\"corps\": texte, \"registre\": id, \"partition\": id}, ex. \"$.objet\""),
                new ConfigField("defaultFields", "Valeurs par défaut", ConfigFieldType.STRING, false, null, List.of(),
                        "Objet JSON de valeurs constantes, prises quand le mapping ne donne rien, ex. {\"canal\": \"EBOX\"}"),
                new ConfigField("oauthTokenUrl", "OAuth v5 token URL", ConfigFieldType.URL, true, null, List.of(),
                        "ex. https://services.socialsecurity.be/REST/oauth/v5/token"),
                new ConfigField("oauthClientId", "OAuth v5 client id", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("oauthPrivateKey", "Clé privée du certificat", ConfigFieldType.SECRET_TEXTAREA, true, null, List.of(),
                        "PEM PKCS#8 RSA. Chiffrée au repos"),
                new ConfigField("oauthCertThumbprint", "Empreinte du certificat", ConfigFieldType.STRING, true, null, List.of(),
                        "SHA-1 (40) ou SHA-256 (64) en hexadécimal"),
                new ConfigField("oauthScope", "OAuth v5 scope", ConfigFieldType.STRING, false, DEFAULT_SCOPE, List.of(), null));
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        enterpriseNumber(config);
        partitions(config);
        List<String> suffixes = suffixes(config);
        requireAllowedHost(text(config, "registryUrl"), suffixes, "registryUrl");
        requireAllowedHost(text(config, "oauthTokenUrl"), suffixes, "oauthTokenUrl");
        Object method = config.get("oauthClientAuthMethod");
        if (method != null && !OAuth2TokenProvider.METHOD_JWT.equals(String.valueOf(method))) {
            throw new ConnectorValidationException("eBox only supports oauthClientAuthMethod " + OAuth2TokenProvider.METHOD_JWT);
        }
        OAuth2ConfigSupport.validate(oauthConfig(config), guard);
        lookbackDays(config);
        language(config);
        int poll = InboundMailRouting.intOr(config.get("pollSeconds"), 900);
        if (poll < inbound.minPollSeconds()) {
            throw new ConnectorValidationException("pollSeconds must be at least " + inbound.minPollSeconds());
        }
        for (String key : List.of("mapping", "defaultFields")) {
            if (config.get(key) != null && !(config.get(key) instanceof Map<?, ?>)) {
                throw new ConnectorValidationException(key + " must be a JSON object");
            }
        }
        text(config, "processKey");
        text(config, "dossierTypeCode");
    }

    @Override
    public void poll(ConnectorInstanceData instance, EventDelivery delivery) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(delivery, "delivery");
        Run run;
        try {
            validate(instance.config());
            run = new Run(instance, delivery);
        } catch (ConnectorValidationException invalid) {
            throw new IllegalStateException(invalid.getMessage());
        }
        for (Endpoint endpoint : endpoints(run)) {
            for (String partition : run.partitions) {
                if (run.taken >= inbound.maxMessagesPerPoll()) {
                    return;
                }
                readPartition(run, endpoint, partition);
            }
        }
    }

    /**
     * TODO §150 (D-EBOX-ACTION) — confirme l'action d'un message pris en charge par une instance de ce tenant et de cette eBox
     * ({@code PATCH .../messageActions/{actionId}}, {@code isExecuted: true}). Sans {@code actionId}, l'action est la seule encore
     * ouverte du message. Toute erreur remonte : l'étape BPMN échoue et son incident se rejoue.
     */
    @Override
    public Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> input, ConnectorCallContext context) {
        // Revue de clôture : ni l'API d'administration ni l'API M2M ne confirment une action eBox, seul un process le fait.
        if (context == null || !context.fromProcess()) {
            throw new IllegalArgumentException(TYPE + ": " + MARK_ACTION_EXECUTED + " is only available from a process step");
        }
        return execute(instance, input);
    }

    @Override
    public Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> input) {
        Objects.requireNonNull(instance, "instance");
        Map<String, Object> params = input == null ? Map.of() : input;
        String operation = param(params, "operation");
        if (!MARK_ACTION_EXECUTED.equals(operation)) {
            throw new IllegalArgumentException(TYPE + " supports only the operation " + MARK_ACTION_EXECUTED);
        }
        String registryId = param(params, "registry");
        String messageId = param(params, "messageId");
        String actionId = param(params, "actionId");
        if (!REGISTRY_ID.matcher(registryId).matches() || !MESSAGE_ID.matcher(messageId).matches()
                || (!actionId.isEmpty() && !MESSAGE_ID.matcher(actionId).matches())) {
            throw new IllegalArgumentException(TYPE + ": registry and messageId are required (eboxRegistre, eboxMessageId)");
        }
        Run run;
        try {
            validate(instance.config());
            run = new Run(instance, null);
        } catch (ConnectorValidationException invalid) {
            throw new IllegalStateException(invalid.getMessage());
        }
        if (journal.alreadyTaken(instance.tenantId(), run.enterpriseNumber, registryId, List.of(messageId)).isEmpty()) {
            throw new IllegalArgumentException(TYPE + ": the message was not taken from this eBox by this tenant");
        }
        Endpoint endpoint = endpoints(run).stream()
                .filter(candidate -> candidate.registryId().equals(registryId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(TYPE + ": message registry " + registryId + " has no active endpoint"));
        if (actionId.isEmpty()) {
            List<JsonNode> statuses = run.authorized(() -> client.actionStatuses(endpoint.url(), run.access, messageId));
            List<String> open = statuses.stream()
                    .filter(status -> !status.path("isExecuted").asBoolean(false))
                    .map(status -> status.path("messageActionId").asString(""))
                    .toList();
            if (open.stream().anyMatch(id -> !MESSAGE_ID.matcher(id).matches())) {
                throw new IllegalStateException(TYPE + ": an open action of the message has an invalid id");
            }
            if (open.isEmpty() && !statuses.isEmpty()) {
                return Map.of("eboxActionExecutee", true, "eboxActionDejaFaite", true);
            }
            if (open.size() != 1) {
                throw new IllegalStateException(TYPE + ": the message has " + open.size()
                        + " open actions; name the one to confirm with actionId");
            }
            actionId = open.getFirst();
        }
        String confirmed = actionId;
        run.authorized(() -> {
            client.markActionExecuted(endpoint.url(), run.access, messageId, confirmed);
            return null;
        });
        log.info("{}: instance {} confirmed an action of a message of registry {}", TYPE, instance.id(), registryId);
        return Map.of("eboxActionExecutee", true, "eboxActionId", confirmed);
    }

    /** Un point d'accès en version 3.x d'un registre de messages, actif aujourd'hui. */
    record Endpoint(String registryId, String url) {}

    private List<Endpoint> endpoints(Run run) {
        List<JsonNode> registries = run.authorized(() -> client.registries(run.registryUrl, run.access));
        List<Endpoint> endpoints = new ArrayList<>();
        LocalDate today = LocalDate.now(clock.withZone(EBOX_ZONE));
        for (JsonNode registry : registries) {
            String id = registry.path("id").asString("");
            if (!REGISTRY_ID.matcher(id).matches()) {
                log.warn("{}: instance {} ignores a message registry with an invalid id", TYPE, run.instance.id());
                continue;
            }
            Optional<String> url = activeEndpoint(registry.path("endpoints"), today);
            if (url.isEmpty()) {
                log.info("{}: instance {} finds no active 3.x endpoint for message registry {}", TYPE, run.instance.id(), id);
                continue;
            }
            if (!allowedHost(url.get(), run.suffixes)) {
                log.warn("{}: instance {} ignores message registry {}: its host is not allowed", TYPE, run.instance.id(), id);
                continue;
            }
            endpoints.add(new Endpoint(id, url.get()));
        }
        return endpoints;
    }

    private static Optional<String> activeEndpoint(JsonNode endpoints, LocalDate today) {
        for (JsonNode endpoint : endpoints) {
            if (endpoint.path("isTemporarilyDeactivated").asBoolean(false)) {
                continue;
            }
            if (!endpoint.path("apiVersion").asString("").startsWith("3.")) {
                continue;
            }
            Optional<LocalDate> activation = date(endpoint.path("activationDate"));
            Optional<LocalDate> deactivation = date(endpoint.path("deactivationDate"));
            if (activation.isPresent() && activation.get().isAfter(today)) {
                continue;
            }
            if (deactivation.isPresent() && !deactivation.get().isAfter(today)) {
                continue;
            }
            String url = endpoint.path("url").asString("");
            if (!url.isBlank()) {
                return Optional.of(url.strip());
            }
        }
        return Optional.empty();
    }

    private void readPartition(Run run, Endpoint endpoint, String partition) {
        LocalDate today = LocalDate.now(clock.withZone(EBOX_ZONE));
        // Un message qui échoue toujours retient le curseur : la fenêtre ne remonte jamais au-delà de MAX_LOOKBACK_DAYS.
        LocalDate floor = today.minusDays(MAX_LOOKBACK_DAYS);
        LocalDate cursorDay = journal.readUntil(run.instance.id(), endpoint.registryId(), partition)
                .map(day -> day.minusDays(1))
                .orElse(today.minusDays(run.lookbackDays));
        LocalDate from = cursorDay.isBefore(floor) ? floor : cursorDay;
        List<JsonNode> listed = new ArrayList<>();
        boolean complete = false;
        try {
            for (int page = 1; page <= MAX_PAGES; page++) {
                int current = page;
                MessagePage result = run.authorized(() -> client.messages(endpoint.url(), run.access, partition, from, current, PAGE_SIZE));
                listed.addAll(result.items());
                if (result.items().size() < PAGE_SIZE || listed.size() >= result.totalItems()) {
                    complete = true;
                    break;
                }
            }
        } catch (RuntimeException failure) {
            log.warn("{}: instance {} could not list partition {} of message registry {} ({}), next attempt at the next poll", TYPE,
                    run.instance.id(), partition, endpoint.registryId(), failure.getClass().getSimpleName());
            return;
        }
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        for (JsonNode message : listed) {
            String id = message.path("messageId").asString("");
            if (!MESSAGE_ID.matcher(id).matches()) {
                continue;
            }
            if (!inPartition(message, partition)) {
                // Le serveur a rendu un message d'une autre partition : il n'est jamais ouvert (D-EBOX-PARTITIONS).
                log.warn("{}: instance {} skips a message outside partition {}", TYPE, run.instance.id(), partition);
                continue;
            }
            byId.putIfAbsent(id, message);
        }
        Set<String> taken = byId.isEmpty() ? Set.of()
                : journal.alreadyTaken(run.instance.tenantId(), run.enterpriseNumber, endpoint.registryId(), byId.keySet());
        List<JsonNode> candidates = byId.values().stream()
                .filter(message -> !taken.contains(message.path("messageId").asString("")))
                .sorted(Comparator.comparing(message -> message.path("receiptDate").asString("")))
                .toList();
        boolean allTaken = complete;
        for (JsonNode message : candidates) {
            if (run.taken >= inbound.maxMessagesPerPoll()) {
                allTaken = false;
                break;
            }
            try {
                if (!take(run, endpoint, partition, message)) {
                    allTaken = false;
                }
            } catch (EboxClient.EboxTransportException | EboxUnauthorizedException unavailable) {
                log.warn("{}: instance {} stops reading partition {} of message registry {} ({})", TYPE, run.instance.id(), partition,
                        endpoint.registryId(), unavailable.getMessage());
                allTaken = false;
                break;
            }
        }
        if (allTaken) {
            journal.advance(run.instance.id(), endpoint.registryId(), partition, today);
        }
    }

    private static boolean inPartition(JsonNode message, String partition) {
        JsonNode recipientPartition = message.path("recipient").path("partition");
        // Partition absente : étrangère. Un registre qui ignore le filtre rendrait sinon toute la boîte principale.
        return recipientPartition.isString() && partition.equals(recipientPartition.asString());
    }

    /** Remet un message ; faux s'il doit être relu au passage suivant. */
    private boolean take(Run run, Endpoint endpoint, String partition, JsonNode message) {
        String messageId = message.path("messageId").asString();
        List<InboundAttachment> attachments = new ArrayList<>();
        List<String> notImported = new ArrayList<>();
        long kept = 0;
        int index = 0;
        for (JsonNode attachment : message.path("attachments")) {
            String name = fileName(attachment, ++index);
            if (index > MAX_ATTACHMENTS) {
                notImported.add(name);
                continue;
            }
            double sizeKb = attachment.path("size").asDouble(0);
            if (sizeKb * 1024 > inbound.maxAttachmentBytes() || kept + sizeKb * 1024 > inbound.maxMessageBytes()) {
                notImported.add(name);
                continue;
            }
            String attachmentId = attachment.path("attachmentId").asString("");
            if (!MESSAGE_ID.matcher(attachmentId).matches()) {
                notImported.add(name);
                continue;
            }
            try {
                byte[] content = run.authorized(() -> client.attachment(endpoint.url(), run.access, messageId, attachmentId,
                        inbound.maxAttachmentBytes()));
                if (kept + content.length > inbound.maxMessageBytes()) {
                    // Plafond par message (app.inbound.max-message-bytes), comme pour l'e-mail : la pièce est nommée, pas gardée.
                    notImported.add(name);
                    continue;
                }
                kept += content.length;
                attachments.add(new InboundAttachment(name, mediaType(attachment), content));
            } catch (EboxResourceException refused) {
                if (refused.status == 403) {
                    log.warn("{}: instance {} may not read an attachment of message {} (HTTP 403), retried at the next poll", TYPE,
                            run.instance.id(), messageId);
                    return false;
                }
                notImported.add(name);
            }
        }
        String body = null;
        if (message.path("hasBody").asBoolean(false)) {
            try {
                body = run.authorized(() -> client.body(endpoint.url(), run.access, messageId))
                        .map(text -> localized(text, run.language))
                        .map(text -> text.length() > inbound.maxBodyChars() ? text.substring(0, inbound.maxBodyChars()) : text)
                        .orElse(null);
            } catch (EboxResourceException refused) {
                body = null;
            }
        }
        WorkflowTriggerEvent event = event(run, endpoint, partition, message, body, attachments, notImported);
        EboxTakenMessage entry = new EboxTakenMessage(run.instance.tenantId(), run.enterpriseNumber, endpoint.registryId(), messageId,
                partition, run.instance.id(), clock.instant());
        try {
            transactions.executeWithoutResult(status -> {
                int rejected = run.delivery.deliverAndReport(event).rejectedAttachments();
                if (!journal.recordTaken(entry)) {
                    // Pris entre-temps par une autre instance qui lit la même eBox : ce courrier-ci est annulé.
                    status.setRollbackOnly();
                    return;
                }
                if (rejected > 0) {
                    log.warn("{}: instance {} message {}: {} attachment(s) refused by the checks, the mail keeps the others", TYPE,
                            run.instance.id(), messageId, rejected);
                }
            });
        } catch (RuntimeException failure) {
            log.warn("{}: instance {} message {} could not be taken in ({}), retried at the next poll", TYPE, run.instance.id(), messageId,
                    failure.getClass().getSimpleName());
            return false;
        }
        run.taken++;
        return true;
    }

    private WorkflowTriggerEvent event(Run run, Endpoint endpoint, String partition, JsonNode message, String body,
            List<InboundAttachment> attachments, List<String> notImported) {
        @SuppressWarnings("unchecked")
        Map<String, Object> messageMap = objectMapper.convertValue(message, Map.class);
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", messageMap);
        payload.put("registre", endpoint.registryId());
        payload.put("partition", partition);
        payload.put("corps", body == null ? "" : body);
        // Raccourcis prêts à mapper : l'objet dans la langue préférée, l'expéditeur par son numéro BCE.
        payload.put("objet", localized(message.path("subject"), run.language));
        payload.put("expediteur", message.path("sender").path("enterpriseNumber").asString(""));
        Map<String, Object> fields = new LinkedHashMap<>(run.defaults);
        for (Map.Entry<String, Object> path : run.fieldPaths.entrySet()) {
            Object value = mapper.read(payload, String.valueOf(path.getValue()));
            if (value != null && !(value instanceof String text && text.isBlank())) {
                fields.put(path.getKey(), value);
            }
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        for (Map.Entry<String, Object> path : run.variablePaths.entrySet()) {
            Object value = mapper.read(payload, String.valueOf(path.getValue()));
            if (value != null) {
                variables.put(path.getKey(), value);
            }
        }
        variables.keySet().removeAll(PLATFORM_VARIABLES);
        variables.keySet().removeAll(InboundMailRouting.PLATFORM_VARIABLES);
        variables.put("eboxMessageId", message.path("messageId").asString());
        variables.put("eboxRegistre", endpoint.registryId());
        variables.put("eboxPartition", partition);
        variables.put("eboxEntreprise", run.enterpriseNumber);
        putText(variables, "eboxExpediteur", message.path("sender").path("enterpriseNumber"));
        putText(variables, "eboxApplication", message.path("senderApplicationId"));
        putText(variables, "eboxTypeMessage", message.path("messageTypeId"));
        String subject = localized(message.path("subject"), run.language);
        if (!subject.isBlank()) {
            variables.put("eboxObjet", subject.length() > 500 ? subject.substring(0, 500) : subject);
        }
        putText(variables, "eboxRecuLe", message.path("receiptDate"));
        putText(variables, "eboxExpireLe", message.path("expirationDate"));
        putText(variables, "eboxDateLimiteReponse", message.path("replyDueDate"));
        variables.put("eboxRecommande", message.path("isRegisteredMail").asBoolean(false)
                || !message.path("registeredMailType").asString("").isBlank());
        variables.put("eboxActionRequise", message.path("hasMessageActions").asBoolean(false));
        variables.put("eboxReponseAutorisee", message.path("isReplyAuthorized").asBoolean(false));
        if (body != null && !body.isBlank()) {
            variables.put("eboxCorps", body);
        }
        if (!notImported.isEmpty()) {
            variables.put("eboxPiecesNonImportees", List.copyOf(notImported));
        }
        return new WorkflowTriggerEvent(text(run.config, "processKey"), text(run.config, "dossierTypeCode"), fields, variables, null,
                attachments, true);
    }

    /** Une interrogation d'une instance : configuration lue une fois, jeton renouvelé au plus une fois. */
    private final class Run {

        private final ConnectorInstanceData instance;
        private final ConnectorInstanceData oauthInstance;
        private final EventDelivery delivery;
        private final Map<String, Object> config;
        private final String enterpriseNumber;
        private final List<String> partitions;
        private final List<String> suffixes;
        private final String registryUrl;
        private final int lookbackDays;
        private final String language;
        private final Map<String, Object> fieldPaths;
        private final Map<String, Object> variablePaths;
        private final Map<String, Object> defaults;
        private Access access;
        private boolean renewed;
        private int taken;

        private Run(ConnectorInstanceData instance, EventDelivery delivery) {
            this.instance = instance;
            this.delivery = delivery;
            this.config = instance.config();
            this.oauthInstance = new ConnectorInstanceData(instance.id(), instance.tenantId(), instance.name(), instance.connectorType(),
                    instance.enabled(), oauthConfig(config));
            this.enterpriseNumber = enterpriseNumber(config);
            this.partitions = partitions(config);
            this.suffixes = suffixes(config);
            this.registryUrl = text(config, "registryUrl");
            this.lookbackDays = lookbackDays(config);
            this.language = language(config);
            Map<String, Object> mapping = InboundMailRouting.asMap(config.get("mapping"));
            this.fieldPaths = InboundMailRouting.asMap(mapping.get("fields"));
            this.variablePaths = InboundMailRouting.asMap(mapping.get("variables"));
            this.defaults = InboundMailRouting.asMap(config.get("defaultFields"));
            this.access = new Access(tokenProvider.accessToken(oauthInstance), onBehalfOf(config) ? enterpriseNumber : null);
        }

        /** Appel à l'eBox ; un 401 renouvelle le jeton une seule fois par passage, puis l'erreur remonte. */
        private <T> T authorized(Supplier<T> call) {
            try {
                return call.get();
            } catch (EboxUnauthorizedException refused) {
                if (renewed || !tokenProvider.invalidate(oauthInstance)) {
                    throw refused;
                }
                renewed = true;
                access = new Access(tokenProvider.accessToken(oauthInstance), access.onBehalfOf());
                return call.get();
            }
        }
    }

    private boolean allowedHost(String url, List<String> suffixes) {
        URI uri;
        try {
            uri = URI.create(url);
            guard.assertSafe(url);
        } catch (IllegalArgumentException refused) {
            return false;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (uri.getUserInfo() != null || uri.getRawFragment() != null || host.isEmpty()) {
            return false;
        }
        if (guard.isLocalLoopback(host)) {
            return true;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        return suffixes.stream().anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
    }

    private void requireAllowedHost(String url, List<String> suffixes, String key) {
        if (!allowedHost(url, suffixes)) {
            throw new ConnectorValidationException(key + " must be an https URL on an allowed domain (allowedHostSuffixes)");
        }
    }

    private static Map<String, Object> oauthConfig(Map<String, Object> config) {
        Map<String, Object> copy = new HashMap<>(config);
        copy.put("oauthClientAuthMethod", OAuth2TokenProvider.METHOD_JWT);
        Object scope = copy.get("oauthScope");
        if (scope == null || String.valueOf(scope).isBlank()) {
            copy.put("oauthScope", DEFAULT_SCOPE);
        }
        return copy;
    }

    static String enterpriseNumber(Map<String, Object> config) {
        String value = text(config, "enterpriseNumber").replace(".", "").replace(" ", "");
        if (!ENTERPRISE_NUMBER.matcher(value).matches()) {
            throw new ConnectorValidationException("enterpriseNumber must be a ten-digit CBE number starting with 0 or 1");
        }
        return value;
    }

    static List<String> partitions(Map<String, Object> config) {
        Object raw = config.get("partitions");
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            list.forEach(item -> values.add(String.valueOf(item)));
        } else if (raw != null) {
            values.addAll(List.of(String.valueOf(raw).split("[,;\\n]")));
        }
        Set<String> partitions = new LinkedHashSet<>();
        for (String value : values) {
            String partition = value.strip();
            if (partition.isEmpty()) {
                continue;
            }
            if (!PARTITION.matcher(partition).matches()) {
                throw new ConnectorValidationException("partitions contains an invalid partition id");
            }
            partitions.add(partition);
        }
        if (partitions.isEmpty()) {
            throw new ConnectorValidationException("partitions is required: Myrmacta only reads partitions dedicated to it");
        }
        if (partitions.size() > MAX_PARTITIONS) {
            throw new ConnectorValidationException("partitions accepts at most " + MAX_PARTITIONS + " partitions");
        }
        return List.copyOf(partitions);
    }

    private static List<String> suffixes(Map<String, Object> config) {
        String raw = InboundMailRouting.stringOr(config.get("allowedHostSuffixes"), DEFAULT_SUFFIXES);
        List<String> suffixes = new ArrayList<>();
        for (String value : raw.split(",")) {
            String suffix = value.strip().toLowerCase(Locale.ROOT);
            if (suffix.startsWith(".")) {
                suffix = suffix.substring(1);
            }
            if (suffix.isEmpty()) {
                continue;
            }
            if (!HOST_SUFFIX.matcher(suffix).matches()) {
                throw new ConnectorValidationException("allowedHostSuffixes must list domain names such as socialsecurity.be");
            }
            suffixes.add(suffix);
        }
        if (suffixes.isEmpty()) {
            throw new ConnectorValidationException("allowedHostSuffixes must list at least one domain");
        }
        return List.copyOf(suffixes);
    }

    private static int lookbackDays(Map<String, Object> config) {
        int days = InboundMailRouting.intOr(config.get("initialLookbackDays"), DEFAULT_LOOKBACK_DAYS);
        if (days < 1 || days > MAX_LOOKBACK_DAYS) {
            throw new ConnectorValidationException("initialLookbackDays must be between 1 and " + MAX_LOOKBACK_DAYS);
        }
        return days;
    }

    private static String language(Map<String, Object> config) {
        String language = InboundMailRouting.stringOr(config.get("language"), "fr").strip().toLowerCase(Locale.ROOT);
        if (!LANGUAGES.contains(language)) {
            throw new ConnectorValidationException("language must be fr, nl or de");
        }
        return language;
    }

    private static boolean onBehalfOf(Map<String, Object> config) {
        Object value = config.get("onBehalfOf");
        return Boolean.TRUE.equals(value) || (value instanceof String text && "true".equalsIgnoreCase(text.strip()));
    }

    /** Texte dans la langue préférée, sinon dans la première langue présente (fr, nl, de). */
    static String localized(JsonNode text, String language) {
        if (text == null || !text.isObject()) {
            return text != null && text.isString() ? text.asString() : "";
        }
        String preferred = text.path(language).asString("");
        if (!preferred.isBlank()) {
            return preferred;
        }
        for (String other : List.of("fr", "nl", "de")) {
            String value = text.path(other).asString("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String fileName(JsonNode attachment, int index) {
        String name = attachment.path("fileName").asString("").replaceAll("[\\p{Cntrl}/\\\\]", "_").strip();
        if (name.isEmpty()) {
            name = "piece-" + index;
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static String mediaType(JsonNode attachment) {
        String type = attachment.path("mediaType").asString("").strip().toLowerCase(Locale.ROOT);
        return type.matches("[a-z0-9.+-]+/[a-z0-9.+-]+") ? type : "application/octet-stream";
    }

    private static Optional<LocalDate> date(JsonNode node) {
        String value = node.asString("");
        if (value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value));
        } catch (DateTimeParseException unreadable) {
            return Optional.empty();
        }
    }

    private static void putText(Map<String, Object> variables, String key, JsonNode node) {
        String value = node.asString("");
        if (!value.isBlank() && value.length() <= 200) {
            variables.put(key, value);
        }
    }

    private static String param(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? "" : String.valueOf(value).strip();
    }

    private static String text(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ConnectorValidationException(key + " is required");
        }
        return String.valueOf(value).strip();
    }
}
