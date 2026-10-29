package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.workflow.model.StartVariableDeclaration;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import be.myrmacta.domain.workflow.port.out.StartVariableCatalogPort;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** TODO §111 (D-START-DECLARED) — lit les déclarations sur le XML de la dernière version déployée. */
@Component
class FlowableStartVariableCatalogAdapter implements StartVariableCatalogPort {

    private final TenantProperties tenantProperties;
    private final WorkflowDeploymentPort deploymentPort;
    private final BpmnParserPort parser;

    FlowableStartVariableCatalogAdapter(
            TenantProperties tenantProperties, WorkflowDeploymentPort deploymentPort, BpmnParserPort parser) {
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.deploymentPort = Objects.requireNonNull(deploymentPort, "deploymentPort");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    @Override
    public List<StartVariableDeclaration> findDeclared(String processDefinitionKey) {
        return deploymentPort
                .findDeployedXml(tenantProperties.id(), processDefinitionKey)
                .map(xml -> parser.extractStartVariables(xml, processDefinitionKey))
                .orElse(List.of());
    }
}
