package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.connector.port.in.ExecuteConnectorUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.Map;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

/**
 * TODO §151 (D-EBOX-MOVE) — brique « Classer le message eBox » : déplace le message traité vers la partition cible
 * ({@code eboxPartitionCible}, attribut littéral obligatoire), qu'aucune instance active ne lit. Pose {@code eboxClasse} et
 * {@code eboxDejaClasse} (vrai quand un rejeu trouve le message déjà dans la cible).
 */
@Component("eboxMoveTask")
class EboxMoveDelegate extends EboxMessageDelegate {

    static final String TARGET = "eboxPartitionCible";
    static final String RESULT = "eboxClasse";
    static final String ALREADY = "eboxDejaClasse";

    EboxMoveDelegate(ExecuteConnectorUseCase executeConnector, ConnectorInstanceRepository instances, DossierRepository dossiers,
            TenantProperties tenantProperties) {
        super(executeConnector, instances, dossiers, tenantProperties);
    }

    @Override
    public void execute(DelegateExecution execution) {
        String target = DeployedActivityParameters.getLiteral(execution, TARGET);
        if (target == null) {
            throw new IllegalArgumentException("eboxPartitionCible must be a literal flowable:eboxPartitionCible attribute");
        }
        Map<String, Object> result = call(execution, "moveToPartition", Map.of("targetPartition", target));
        execution.setVariable(RESULT, Boolean.TRUE.equals(result.get(RESULT)));
        execution.setVariable(ALREADY, Boolean.TRUE.equals(result.get(ALREADY)));
    }
}
