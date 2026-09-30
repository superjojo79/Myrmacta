package com.flowforge.domain.workflow.palette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.UnknownBlockReferenceException;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.palette.port.out.BlockReferenceLookupPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** S-REFCHK-1..3 — existence des ressources référencées par les attributs de brique, au déploiement (TODO §73). */
class BlockReferenceVerifierTest {

    private static final String TENANT = "tenant_a";
    private final BlockReferenceLookupPort lookup = mock(BlockReferenceLookupPort.class);
    private final BlockReferenceVerifier verifier = new BlockReferenceVerifier(TENANT, lookup);

    private static BlockParameterUsage usage(String parameter, String source, String value) {
        return new BlockParameterUsage("act", "block", parameter, source, value);
    }

    private static WorkflowTypeConfig config(String... statuses) {
        WorkflowTypeConfig config = mock(WorkflowTypeConfig.class);
        List<WorkflowStatusDefinition> defs = new java.util.ArrayList<>();
        for (int i = 0; i < statuses.length; i++) {
            defs.add(new WorkflowStatusDefinition(statuses[i], statuses[i], i == 0, StatusOutcome.NONE));
        }
        when(config.statusDefinitions()).thenReturn(defs);
        return config;
    }

    @Test
    void existingResourcesAreAccepted() {
        when(lookup.exists(TENANT, "documentTemplates", "facture")).thenReturn(true);
        when(lookup.exists(TENANT, "connectorInstances", "uuid-1")).thenReturn(true);
        when(lookup.exists(TENANT, "dossierTypes", "ENFANT")).thenReturn(true);
        when(lookup.exists(TENANT, "workflowTypes", "suite")).thenReturn(true);
        when(lookup.exists(TENANT, "notificationWebhooks", "uuid-2")).thenReturn(true);

        verifier.verify("claim", Optional.of(config("OUVERT")), List.of(
                usage("documentType", "documentTemplates", "facture"),
                usage("connectorInstanceId", "connectorInstances", "uuid-1"),
                usage("childDossierTypeCode", "dossierTypes", "ENFANT"),
                usage("workflowProcessKey", "workflowTypes", "suite"),
                usage("notificationWebhookConfigId", "notificationWebhooks", "uuid-2")));
    }

    @Test
    void missingResourceIsRefusedNamingActivityAttributeAndValue() {
        when(lookup.exists(TENANT, "documentTemplates", "absent")).thenReturn(false);

        assertThatThrownBy(() -> verifier.verify("claim", Optional.empty(),
                List.of(usage("documentType", "documentTemplates", "absent"))))
                .isInstanceOf(UnknownBlockReferenceException.class)
                .hasMessageContaining("act").hasMessageContaining("documentType").hasMessageContaining("absent");
    }

    @Test
    void theProcessBeingDeployedIsAValidChildWorkflowEvenWithoutAStoredConfigLookup() {
        verifier.verify("claim", Optional.empty(), List.of(usage("workflowProcessKey", "workflowTypes", "claim")));

        verifyNoInteractions(lookup);
    }

    @Test
    void workflowStatusesAreCheckedAgainstTheEffectiveConfigOfThisProcess() {
        verifier.verify("claim", Optional.of(config("OUVERT", "VALIDE")),
                List.of(usage("targetWorkflowStatus", "workflowStatuses", "VALIDE")));

        assertThatThrownBy(() -> verifier.verify("claim", Optional.of(config("OUVERT")),
                List.of(usage("targetWorkflowStatus", "workflowStatuses", "VALIDE"))))
                .isInstanceOf(UnknownBlockReferenceException.class).hasMessageContaining("VALIDE");
        assertThatThrownBy(() -> verifier.verify("claim", Optional.empty(),
                List.of(usage("targetWorkflowStatus", "workflowStatuses", "VALIDE"))))
                .isInstanceOf(UnknownBlockReferenceException.class);
        verifyNoInteractions(lookup);
    }

    @Test
    void noUsageMeansNoLookup() {
        verifier.verify("claim", Optional.empty(), List.of());
        verifyNoInteractions(lookup);
    }
}
