package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.connector.port.in.ExecuteConnectorUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.Map;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

/**
 * TODO §150 (D-EBOX-ACTION) — brique « Marquer l'action eBox comme faite ». L'action ({@code eboxActionId}, facultative) est un
 * attribut littéral ; sans elle, le connecteur confirme la seule action encore ouverte du message.
 */
@Component("eboxActionDoneTask")
class EboxActionDoneDelegate extends EboxMessageDelegate {

    static final String ACTION = "eboxActionId";
    static final String RESULT = "eboxActionExecutee";

    EboxActionDoneDelegate(ExecuteConnectorUseCase executeConnector, ConnectorInstanceRepository instances, DossierRepository dossiers,
            TenantProperties tenantProperties) {
        super(executeConnector, instances, dossiers, tenantProperties);
    }

    @Override
    public void execute(DelegateExecution execution) {
        String action = DeployedActivityParameters.getLiteral(execution, ACTION);
        Map<String, Object> result = call(execution, "markActionExecuted", action == null ? Map.of() : Map.of("actionId", action));
        execution.setVariable(RESULT, Boolean.TRUE.equals(result.get(RESULT)));
    }
}
