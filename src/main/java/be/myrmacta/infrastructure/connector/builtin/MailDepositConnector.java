package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.TriggerConnector;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.domain.mailroom.model.DepositPoint;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * TODO §113 (D-MAILROOM-DEPOSIT) — point de dépôt de l'écran « Déposer » : dit quel type de dossier créer, quel process démarrer,
 * quels champs reçoivent l'expéditeur et l'objet saisis, et les valeurs constantes (canal PAPIER). Il ne se déclenche jamais
 * seul : seul l'écran lui remet des PDF ({@code DepositMailUseCase}).
 */
@Component
public class MailDepositConnector implements ConnectorDefinition, TriggerConnector {

    @Override
    public String getType() {
        return DepositPoint.CONNECTOR_TYPE;
    }

    @Override
    public String getLabel() {
        return "Dépôt manuel de courrier (écran Déposer)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.TRIGGER;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
                new ConfigField("processKey", "Process key", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("dossierTypeCode", "Dossier type", ConfigFieldType.STRING, true, null, List.of(), null),
                new ConfigField("senderField", "Champ de l'expéditeur", ConfigFieldType.STRING, false, "expediteur", List.of(), null),
                new ConfigField("subjectField", "Champ de l'objet", ConfigFieldType.STRING, false, "objet", List.of(),
                        "Objet saisi ; à défaut, le nom du fichier"),
                new ConfigField("defaultFields", "Valeurs par défaut", ConfigFieldType.STRING, false, null, List.of(),
                        "Objet JSON de valeurs constantes, ex. {\"canal\": \"PAPIER\"}"));
    }

    @Override
    public void validate(Map<String, Object> config) {
        if (config == null) {
            throw new ConnectorValidationException("config is required");
        }
        for (String key : List.of("processKey", "dossierTypeCode")) {
            if (config.get(key) == null || String.valueOf(config.get(key)).isBlank()) {
                throw new ConnectorValidationException(key + " is required");
            }
        }
        for (String key : List.of("senderField", "subjectField")) {
            Object value = config.get(key);
            if (value != null && !String.valueOf(value).matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
                throw new ConnectorValidationException(key + " is not a field name");
            }
        }
        if (config.get("defaultFields") != null && !(config.get("defaultFields") instanceof Map<?, ?>)) {
            throw new ConnectorValidationException("defaultFields must be a JSON object");
        }
    }

    /** Jamais déclenché par un webhook ni une interrogation : les PDF n'arrivent que par l'écran « Déposer ». */
    @Override
    public List<WorkflowTriggerEvent> onEvent(ConnectorInstanceData instance, Object rawPayload) {
        return List.of();
    }
}
