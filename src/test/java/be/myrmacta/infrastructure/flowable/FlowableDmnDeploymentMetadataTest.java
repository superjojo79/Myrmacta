package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.workflow.model.DecisionTableVersionInfo;
import java.time.Instant;
import java.util.Optional;
import java.util.Date;
import java.util.List;
import org.flowable.dmn.api.DmnDecision;
import org.flowable.dmn.api.DmnDecisionQuery;
import org.flowable.dmn.api.DmnDeployment;
import org.flowable.dmn.api.DmnDeploymentQuery;
import org.flowable.dmn.api.DmnRepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableDmnDeploymentMetadataTest {

    private DmnRepositoryService repository;
    private DmnDecisionQuery decisionQuery;
    private DmnDeploymentQuery deploymentQuery;
    private FlowableDmnDeploymentAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(DmnRepositoryService.class);
        decisionQuery = mock(DmnDecisionQuery.class);
        deploymentQuery = mock(DmnDeploymentQuery.class);
        when(repository.createDecisionQuery()).thenReturn(decisionQuery);
        when(decisionQuery.decisionKey("eligibilite")).thenReturn(decisionQuery);
        when(decisionQuery.decisionTenantId("tenant")).thenReturn(decisionQuery);
        when(decisionQuery.orderByDecisionVersion()).thenReturn(decisionQuery);
        when(decisionQuery.asc()).thenReturn(decisionQuery);
        when(repository.createDeploymentQuery()).thenReturn(deploymentQuery);
        when(deploymentQuery.deploymentId("dep-1")).thenReturn(deploymentQuery);
        adapter = new FlowableDmnDeploymentAdapter(repository);
    }

    @Test
    void missingDeploymentTime_isEpoch() {
        DmnDecision decision = decision(1, "dep-1");
        when(decisionQuery.list()).thenReturn(List.of(decision));
        DmnDeployment deployment = mock(DmnDeployment.class);
        when(deploymentQuery.singleResult()).thenReturn(deployment);
        when(deployment.getDeploymentTime()).thenReturn(null);

        assertThat(adapter.findAllVersions("tenant", "eligibilite"))
                .singleElement()
                .extracting(DecisionTableVersionInfo::deployedAt)
                .isEqualTo(Instant.EPOCH);
    }

    @Test
    void missingDeployment_isEpoch() {
        DmnDecision decision = decision(1, "dep-1");
        when(decisionQuery.list()).thenReturn(List.of(decision));
        when(deploymentQuery.singleResult()).thenReturn(null);

        assertThat(adapter.findAllVersions("tenant", "eligibilite").getFirst().deployedAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void deploymentTime_isCopied() {
        DmnDecision decision = decision(1, "dep-1");
        when(decisionQuery.list()).thenReturn(List.of(decision));
        DmnDeployment deployment = mock(DmnDeployment.class);
        when(deploymentQuery.singleResult()).thenReturn(deployment);
        when(deployment.getDeploymentTime()).thenReturn(Date.from(Instant.parse("2026-09-01T00:00:00Z")));

        assertThat(adapter.findAllVersions("tenant", "eligibilite").getFirst().deployedAt())
                .isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    void versionedXml_missingDecision_isEmpty() {
        when(decisionQuery.decisionVersion(3)).thenReturn(decisionQuery);
        when(decisionQuery.singleResult()).thenReturn(null);

        assertThat(adapter.findDeployedXml("tenant", "eligibilite", 3)).isEqualTo(Optional.empty());
    }

    private static DmnDecision decision(int version, String deploymentId) {
        DmnDecision decision = mock(DmnDecision.class);
        when(decision.getVersion()).thenReturn(version);
        when(decision.getDeploymentId()).thenReturn(deploymentId);
        return decision;
    }
}
