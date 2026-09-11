package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentInfo;
import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;
import org.flowable.dmn.api.DmnDecision;
import org.flowable.dmn.api.DmnDeployment;
import org.flowable.dmn.api.DmnRepositoryService;
import org.springframework.stereotype.Component;

@Component
class FlowableDmnDeploymentAdapter implements DmnDeploymentPort {

    private static final String[] DMN_RESOURCE_SUFFIXES = {".dmn", ".dmn.xml", ".xml"};

    private final DmnRepositoryService dmnRepositoryService;

    FlowableDmnDeploymentAdapter(DmnRepositoryService dmnRepositoryService) {
        this.dmnRepositoryService = Objects.requireNonNull(dmnRepositoryService, "dmnRepositoryService");
    }

    @Override
    public Optional<DecisionTableDeploymentInfo> findDeploymentInfo(String tenantId, String decisionKey) {
        DmnDecision latest = latestDecision(tenantId, decisionKey);
        if (latest == null) {
            return Optional.empty();
        }
        return Optional.of(new DecisionTableDeploymentInfo(decisionKey, latest.getVersion()));
    }

    @Override
    public DecisionTableDeploymentResult deploy(String tenantId, String filename, byte[] dmnXml) {
        DmnDeployment deployment = dmnRepositoryService
                .createDeployment()
                .name(filename)
                .tenantId(tenantId)
                .addString(dmnResourceName(filename), new String(dmnXml, java.nio.charset.StandardCharsets.UTF_8))
                .deploy();
        DmnDecision decision = dmnRepositoryService.createDecisionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        return new DecisionTableDeploymentResult(decision.getKey(), decision.getVersion(), deployment.getId());
    }

    @Override
    public Optional<byte[]> findDeployedXml(String tenantId, String decisionKey) {
        DmnDecision latest = latestDecision(tenantId, decisionKey);
        if (latest == null) {
            return Optional.empty();
        }
        try (InputStream in = dmnRepositoryService.getResourceAsStream(latest.getDeploymentId(), latest.getResourceName())) {
            return Optional.of(in.readAllBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private DmnDecision latestDecision(String tenantId, String decisionKey) {
        return dmnRepositoryService.createDecisionQuery()
                .decisionKey(decisionKey)
                .decisionTenantId(tenantId)
                .latestVersion()
                .singleResult();
    }

    private static String dmnResourceName(String filename) {
        String name = filename == null ? "" : filename;
        for (String suffix : DMN_RESOURCE_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return name;
            }
        }
        return name + ".dmn";
    }
}
