package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import com.flowforge.domain.workflow.model.DecisionTableVersionInfo;
import com.flowforge.domain.workflow.model.DecisionVersionAlreadyCurrentException;
import com.flowforge.domain.workflow.model.InvalidDmnException;
import com.flowforge.domain.workflow.model.UnknownDecisionKeyException;
import com.flowforge.domain.workflow.model.UnknownDecisionVersionException;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.DmnParserPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DecisionHistoryUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final AccessContext.User ADMIN = AccessContext.user("admin", Set.of("TENANT_ADMIN"));
    private static final AccessContext.User API = AccessContext.user("svc", Set.of("API_SERVICE"));
    private static final AccessContext.User NO_ROLE = AccessContext.user("mgr", Set.of("DOSSIER_MANAGER"));

    @Mock
    private DmnDeploymentPort deploymentPort;

    @Mock
    private DmnParserPort dmnParserPort;

    @Test
    void list_sortsVersionsAscendingAndKeepsDeployedAt() {
        Instant older = Instant.parse("2026-09-01T00:00:00Z");
        Instant newer = Instant.parse("2026-09-02T00:00:00Z");
        when(deploymentPort.findAllVersions(TENANT, "eligibilite"))
                .thenReturn(List.of(
                        new DecisionTableVersionInfo("eligibilite", 2, "dep-2", newer),
                        new DecisionTableVersionInfo("eligibilite", 1, "dep-1", older)));

        var listed = listUseCase().list(ADMIN, "eligibilite");

        assertThat(listed).extracting(DecisionTableVersionInfo::version).containsExactly(1, 2);
        assertThat(listed.getFirst().deployedAt()).isEqualTo(older);
    }

    @Test
    void list_unknownKey_isNotFound() {
        when(deploymentPort.findAllVersions(TENANT, "missing")).thenReturn(List.of());

        assertThatThrownBy(() -> listUseCase().list(ADMIN, "missing")).isInstanceOf(UnknownDecisionKeyException.class);
    }

    @Test
    void list_withoutRole_isDeniedBeforeThePort() {
        assertThatThrownBy(() -> listUseCase().list(NO_ROLE, "eligibilite")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(deploymentPort);
    }

    @Test
    void redeploy_deploysXmlOfTheRequestedVersionAfterAllowlist() {
        byte[] xml = "<definitions/>".getBytes(StandardCharsets.UTF_8);
        when(deploymentPort.findAllVersions(TENANT, "eligibilite"))
                .thenReturn(List.of(
                        version(1),
                        version(2)));
        when(deploymentPort.findDeployedXml(TENANT, "eligibilite", 1)).thenReturn(Optional.of(xml));
        when(dmnParserPort.extractDecisionKey(xml)).thenReturn("eligibilite");
        when(deploymentPort.deploy(TENANT, "eligibilite.dmn", xml))
                .thenReturn(new DecisionTableDeploymentResult("eligibilite", 3, "dep-3"));

        var result = redeployUseCase().redeploy(API, "eligibilite", 1);

        assertThat(result.deployedVersion()).isEqualTo(3);
        InOrder order = Mockito.inOrder(dmnParserPort, deploymentPort);
        order.verify(dmnParserPort).extractDecisionKey(xml);
        order.verify(deploymentPort).deploy(TENANT, "eligibilite.dmn", xml);
    }

    @Test
    void redeploy_missingVersion_isNotFoundAndDoesNotDeploy() {
        when(deploymentPort.findAllVersions(TENANT, "eligibilite")).thenReturn(List.of(version(1), version(2)));

        assertThatThrownBy(() -> redeployUseCase().redeploy(ADMIN, "eligibilite", 9))
                .isInstanceOf(UnknownDecisionVersionException.class);
        verify(deploymentPort, never()).deploy(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(dmnParserPort);
    }

    @Test
    void redeploy_unknownKey_isNotFound() {
        when(deploymentPort.findAllVersions(TENANT, "missing")).thenReturn(List.of());

        assertThatThrownBy(() -> redeployUseCase().redeploy(ADMIN, "missing", 1))
                .isInstanceOf(UnknownDecisionKeyException.class);
    }

    @Test
    void redeploy_rejectedByAllowlist_doesNotDeploy() {
        byte[] xml = "<bad/>".getBytes(StandardCharsets.UTF_8);
        when(deploymentPort.findAllVersions(TENANT, "eligibilite")).thenReturn(List.of(version(1), version(2)));
        when(deploymentPort.findDeployedXml(TENANT, "eligibilite", 1)).thenReturn(Optional.of(xml));
        when(dmnParserPort.extractDecisionKey(xml)).thenThrow(new InvalidDmnException("not allowed"));

        assertThatThrownBy(() -> redeployUseCase().redeploy(ADMIN, "eligibilite", 1))
                .isInstanceOf(InvalidDmnException.class);
        verify(deploymentPort, never()).deploy(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void redeploy_currentVersion_conflictsWithoutDeploy() {
        when(deploymentPort.findAllVersions(TENANT, "eligibilite")).thenReturn(List.of(version(1), version(2)));

        assertThatThrownBy(() -> redeployUseCase().redeploy(ADMIN, "eligibilite", 2))
                .isInstanceOf(DecisionVersionAlreadyCurrentException.class);
        verify(deploymentPort, never()).findDeployedXml(TENANT, "eligibilite", 2);
        verifyNoInteractions(dmnParserPort);
    }

    private ListDecisionTableVersionsUseCaseImpl listUseCase() {
        return new ListDecisionTableVersionsUseCaseImpl(TENANT, deploymentPort, new TenantAdminGuard());
    }

    private RedeployDecisionTableUseCaseImpl redeployUseCase() {
        return new RedeployDecisionTableUseCaseImpl(TENANT, deploymentPort, dmnParserPort, new TenantAdminGuard());
    }

    private static DecisionTableVersionInfo version(int number) {
        return new DecisionTableVersionInfo("eligibilite", number, "dep-" + number, Instant.parse("2026-09-01T00:00:00Z"));
    }
}
