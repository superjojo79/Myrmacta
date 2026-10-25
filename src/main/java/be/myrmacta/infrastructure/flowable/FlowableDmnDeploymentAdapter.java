package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo;
import be.myrmacta.domain.workflow.model.DecisionTableDeploymentResult;
import be.myrmacta.domain.workflow.model.DecisionTableVersionInfo;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
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
    public List<DecisionTableDeploymentInfo> findAllLatestDeployments(String tenantId) {
        return dmnRepositoryService.createDecisionQuery().decisionTenantId(tenantId).latestVersion().list().stream()
                .map(decision -> new DecisionTableDeploymentInfo(decision.getKey(), decision.getVersion()))
                .toList();
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
        return readXml(latest);
    }

    @Override
    public List<DecisionTableVersionInfo> findAllVersions(String tenantId, String decisionKey) {
        return dmnRepositoryService.createDecisionQuery()
                .decisionKey(decisionKey)
                .decisionTenantId(tenantId)
                .orderByDecisionVersion()
                .asc()
                .list()
                .stream()
                .map(decision -> new DecisionTableVersionInfo(
                        decisionKey,
                        decision.getVersion(),
                        decision.getDeploymentId(),
                        deploymentTime(decision.getDeploymentId())))
                .toList();
    }

    @Override
    public Optional<byte[]> findDeployedXml(String tenantId, String decisionKey, int version) {
        DmnDecision decision = dmnRepositoryService.createDecisionQuery()
                .decisionKey(decisionKey)
                .decisionVersion(version)
                .decisionTenantId(tenantId)
                .singleResult();
        if (decision == null) {
            return Optional.empty();
        }
        return readXml(decision);
    }

    private Optional<byte[]> readXml(DmnDecision decision) {
        try (InputStream in = dmnRepositoryService.getResourceAsStream(decision.getDeploymentId(), decision.getResourceName())) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(in.readAllBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Instant deploymentTime(String deploymentId) {
        DmnDeployment deployment = dmnRepositoryService.createDeploymentQuery()
                .deploymentId(deploymentId)
                .singleResult();
        if (deployment == null || deployment.getDeploymentTime() == null) {
            return Instant.EPOCH;
        }
        return deployment.getDeploymentTime().toInstant();
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
