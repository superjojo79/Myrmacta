package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TODO §115 (D-DEADLINE-TIMER) — brique « Calculer une échéance ». Paramètres littéraux seulement (jamais une variable de même
 * nom) : {@code champEcheance} (champ {@code DATE} non sensible du dossier), {@code delaiJours} (repli : aujourd'hui + délai),
 * {@code rappelJours}. Écrit les variables réservées :
 *
 * <ul>
 *   <li>{@code echeance} : la date, {@code AAAA-MM-JJ} ;
 *   <li>{@code echeanceRappel} : {@code rappelJours} jours avant, à 00:00 UTC ;
 *   <li>{@code echeanceDepassee} : le lendemain de l'échéance, à 00:00 UTC (le jour même est encore dans les temps, comme
 *       l'escalade de la plateforme, D-DEADLINE-ESCALATE).
 * </ul>
 *
 * Un minuteur porte ensuite {@code ${echeanceRappel}} ou {@code ${echeanceDepassee}}. Sans date ni délai, la brique échoue :
 * l'incident est visible en supervision, plutôt qu'un minuteur qui ne se déclencherait jamais.
 */
@Component("computeDeadlineTask")
class ComputeDeadlineDelegate implements JavaDelegate {

    static final String FIELD = "champEcheance";
    static final String DELAY_DAYS = "delaiJours";
    static final String REMINDER_DAYS = "rappelJours";

    private final DossierRepository dossiers;
    private final DossierTypeRepository types;
    private final TenantProperties tenantProperties;
    private final Clock clock;

    @Autowired
    ComputeDeadlineDelegate(DossierRepository dossiers, DossierTypeRepository types, TenantProperties tenantProperties) {
        this(dossiers, types, tenantProperties, Clock.systemUTC());
    }

    ComputeDeadlineDelegate(DossierRepository dossiers, DossierTypeRepository types, TenantProperties tenantProperties, Clock clock) {
        this.dossiers = Objects.requireNonNull(dossiers, "dossiers");
        this.types = Objects.requireNonNull(types, "types");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void execute(DelegateExecution execution) {
        String field = text(DeployedActivityParameters.getLiteral(execution, FIELD));
        Integer delay = integer(execution, DELAY_DAYS);
        int reminder = Optional.ofNullable(integer(execution, REMINDER_DAYS)).orElse(0);
        LocalDate deadline = field == null ? null : fromField(execution, field);
        if (deadline == null && delay != null) {
            deadline = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).plusDays(delay);
        }
        if (deadline == null) {
            throw new FlowableException(execution.getCurrentActivityId() + ": aucune échéance — "
                    + (field == null ? "ni champ ni délai" : "champ " + field + " vide et pas de délai"));
        }
        execution.setVariable(WorkflowVariables.ECHEANCE, deadline.toString());
        execution.setVariable(WorkflowVariables.ECHEANCE_RAPPEL,
                deadline.minusDays(reminder).atStartOfDay(ZoneOffset.UTC).toInstant().toString());
        execution.setVariable(WorkflowVariables.ECHEANCE_DEPASSEE, deadline.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString());
    }

    /** La valeur du champ, relue dans le dossier (pas l'instantané du démarrage) ; un champ non {@code DATE} ou sensible est refusé. */
    private LocalDate fromField(DelegateExecution execution, String field) {
        UUID dossierId = UUID.fromString(String.valueOf(execution.getVariable(WorkflowVariables.DOSSIER_ID)));
        Dossier dossier = dossiers.findById(dossierId)
                .orElseThrow(() -> new FlowableException("dossier not found: " + dossierId));
        DossierType type = types.findByCode(tenantProperties.id(), dossier.dossierTypeCode())
                .orElseThrow(() -> new FlowableException("dossier type not found: " + dossier.dossierTypeCode()));
        FieldDefinition definition = type.fieldDefinitions().stream().filter(candidate -> candidate.name().equals(field)).findFirst()
                .orElseThrow(() -> new FlowableException(execution.getCurrentActivityId() + ": champ inconnu du type " + type.code()
                        + " : " + field));
        if (definition.type() != FieldType.DATE || definition.isSensitive()) {
            throw new FlowableException(execution.getCurrentActivityId() + ": " + field + " n'est pas un champ date non sensible");
        }
        Object value = dossier.fields().get(field);
        if (value == null || String.valueOf(value).length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value).substring(0, 10));
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    private static Integer integer(DelegateExecution execution, String name) {
        String raw = text(DeployedActivityParameters.getLiteral(execution, name));
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException invalid) {
            throw new FlowableException(execution.getCurrentActivityId() + ": " + name + " doit être un entier");
        }
    }

    private static String text(String raw) {
        return raw == null || raw.isBlank() ? null : raw.strip();
    }
}
