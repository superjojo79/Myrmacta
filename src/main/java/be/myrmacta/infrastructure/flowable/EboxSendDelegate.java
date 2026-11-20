package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.ExecuteConnectorUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.portal.model.PortalRecipientSource;
import be.myrmacta.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * TODO §155 (lot 2, D-EBOX-SEND-BLOCK) — brique « Envoyer en eBox ». Tous ses paramètres sont des attributs littéraux du BPMN
 * déployé, jamais des variables de même nom : le connecteur {@code ebox-outbound}, la source du destinataire et celle du
 * document ({@code dossierField:<champ>} ou {@code variable:<nom>}), le type de destinataire, l'objet, les identifiants
 * d'application et de type de message attribués à l'onboarding, la langue (littérale ou source), le recommandé et la voie de
 * secours. Le document doit être un document produit et joint au dossier courant (vérifié par le connecteur).
 *
 * <p>La clé d'idempotence est stable par dossier, étape, document et tour de boucle : un rejeu de l'étape (incident, issue
 * incertaine) ne publie jamais deux fois. Le destinataire n'entre pas dans la clé (un NISS ne doit pas être retrouvable depuis
 * une empreinte non salée) : la même étape vers un autre destinataire est refusée par le cas d'usage.
 *
 * <p>Variables posées : {@code eboxIssue} ({@code ENVOYE}, {@code DEJA_ENVOYE}, {@code VOIE_SECOURS}), {@code eboxLivraison},
 * {@code eboxSecoursRequis}, {@code eboxVoieSecours}, {@code eboxEnvoiMessageId}, {@code eboxEnvoiRejoue}. Une issue incertaine
 * fait échouer l'étape (incident rejouable, même clé). La preuve de dépôt d'un recommandé est versée au dossier.
 */
@Component("eboxSendTask")
class EboxSendDelegate implements JavaDelegate {

    static final String CONNECTOR_TYPE = "ebox-outbound";
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
    static final String PROOF = "eboxPreuveDepot";
    static final String KEY_PREFIX = "myr1-";
    static final Set<String> RESULT_VARIABLES = WorkflowVariables.EBOX_SEND_RESULTS;

    private final ExecuteConnectorUseCase executeConnector;
    private final ConnectorInstanceRepository instances;
    private final PortalRecipientResolverPort sources;
    private final DocumentStoragePort storage;
    private final AddDossierElementUseCase addElement;
    private final TenantProperties tenantProperties;

    EboxSendDelegate(ExecuteConnectorUseCase executeConnector, ConnectorInstanceRepository instances,
            PortalRecipientResolverPort sources, DocumentStoragePort storage, AddDossierElementUseCase addElement,
            TenantProperties tenantProperties) {
        this.executeConnector = Objects.requireNonNull(executeConnector, "executeConnector");
        this.instances = Objects.requireNonNull(instances, "instances");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.addElement = Objects.requireNonNull(addElement, "addElement");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
    }

    @Override
    public void execute(DelegateExecution execution) {
        String tenantId = tenantProperties.id();
        UUID instanceId = UUID.fromString(literal(execution, INSTANCE));
        ConnectorInstanceData instance = instances.findByIdAndTenantId(instanceId, tenantId)
                .orElseThrow(() -> new IllegalStateException("connector instance not found: " + instanceId));
        if (!CONNECTOR_TYPE.equals(instance.connectorType())) {
            throw new IllegalStateException("connector instance " + instanceId + " is not an " + CONNECTOR_TYPE + " instance");
        }
        UUID dossierId = dossierId(execution);
        String documentRef = resolve(execution, DOCUMENT, literal(execution, DOCUMENT), dossierId);
        String language = literal(execution, LANGUAGE);
        if (PortalRecipientSource.hasSourcePrefix(language)) {
            language = resolve(execution, LANGUAGE, language, dossierId);
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("recipientType", literal(execution, RECIPIENT_TYPE));
        input.put("recipient", resolve(execution, RECIPIENT, literal(execution, RECIPIENT), dossierId));
        input.put("documentRef", documentRef);
        input.put("subject", literal(execution, SUBJECT));
        input.put("senderApplicationId", literal(execution, APPLICATION));
        input.put("messageTypeId", literal(execution, MESSAGE_TYPE));
        input.put("language", language);
        input.put("registered", "OUI".equals(DeployedActivityParameters.getLiteral(execution, REGISTERED)));
        input.put("fallbackChannel", literal(execution, FALLBACK));
        input.put("idempotencyKey", idempotencyKey(execution, dossierId, documentRef));
        Map<String, Object> result = executeConnector.execute(instanceId, tenantId, input,
                ConnectorCallContext.processWithBuiltRequest(dossierId));
        if (result.get(PROOF) instanceof String proof && !proof.isBlank()) {
            attachProof(execution, tenantId, dossierId, proof);
        }
        for (String name : RESULT_VARIABLES) {
            if (!result.containsKey(name)) {
                throw new IllegalStateException("the eBox connector did not return " + name);
            }
            execution.setVariable(name, result.get(name));
        }
    }

    /** Clé stable : dossier, étape, document et tour de boucle multi-instance ; 69 caractères. */
    static String idempotencyKey(DelegateExecution execution, UUID dossierId, String documentRef) {
        return idempotencyKey(dossierId, execution.getCurrentActivityId(), documentRef, execution.getVariableLocal("loopCounter"));
    }

    static String idempotencyKey(UUID dossierId, String activityId, String documentRef, Object loop) {
        String material = dossierId + "|" + activityId + "|" + documentRef + "|" + (loop == null ? "" : loop);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Preuve de dépôt signée d'un recommandé, versée telle que reçue au dossier (pièce JSON). */
    private void attachProof(DelegateExecution execution, String tenantId, UUID dossierId, String proof) {
        String ref = storage.store(tenantId, "application/json", proof.getBytes(StandardCharsets.UTF_8));
        String activity = execution.getCurrentActivityName() == null || execution.getCurrentActivityName().isBlank()
                ? execution.getCurrentActivityId() : execution.getCurrentActivityName();
        addElement.add(new AddDossierElementUseCase.AddCommand(dossierId, ElementType.ATTACHMENT,
                        "Preuve de dépôt eBox — " + activity, ref, Map.of("contentType", "application/json"), "system",
                        execution.getCurrentActivityId()),
                AccessContext.system("flowable-delegate"));
    }

    /** Valeur d'une source ; le message nomme la source, jamais la valeur (un NISS ne doit pas finir dans un incident). */
    private String resolve(DelegateExecution execution, String parameter, String literal, UUID dossierId) {
        String origin = parameter + " source " + literal;
        PortalRecipientSource source = PortalRecipientSource.parse(literal)
                .orElseThrow(() -> new IllegalArgumentException(parameter + " must be dossierField:<name> or variable:<name>"));
        Object value = switch (source.kind()) {
            case PROCESS_VARIABLE -> execution.getVariable(source.name());
            case DOSSIER_FIELD -> sources.resolve(source, dossierId, execution.getProcessInstanceId()).orElse(null);
        };
        if (!(value instanceof CharSequence text) || text.toString().isBlank()) {
            throw new IllegalStateException(origin + " resolved to no text value");
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
