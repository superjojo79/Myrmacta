package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * TODO §61 (introduction-dmn), M11, T5.5 -- proves the shipped "grille d'éligibilité" example
 * (unlike {@link DmnServiceTaskEndToEndSmokeIT}, which deploys an inline table programmatically to
 * isolate the JUEL security proof) deploys for real at application boot via {@link
 * TenantDmnDeployment}, exactly like the shipped BPMN "-smoke" processes already covered by {@link
 * FormKeySmokeIT}/{@code SignalSmokeIT}: no manual deployment step in this test, just starting the
 * shipped process by key and reading the decision's output back as real process variables.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DmnGrilleEligibiliteSmokeIT {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private TenantProperties tenantProperties;

    private ProcessInstance start(String typeDemandeur, double montantDossier) {
        String tenantId = tenantProperties.id();
        return runtimeService.startProcessInstanceByKeyAndTenantId(
                "dmnGrilleEligibiliteSmoke",
                "dmn-grille-eligibilite-smoke-" + UUID.randomUUID(),
                Map.of("typeDemandeur", typeDemandeur, "montantDossier", montantDossier),
                tenantId);
    }

    private Object variable(ProcessInstance instance, String name) {
        return historyService
                .createHistoricVariableInstanceQuery()
                .processInstanceId(instance.getId())
                .variableName(name)
                .singleResult()
                .getValue();
    }

    @Test
    void grandeEntreprise_estEligible_pourGrandCompte() {
        ProcessInstance instance = start("ENTREPRISE", 50_000);

        assertThat(variable(instance, "statutEligibilite")).isEqualTo("ELIGIBLE");
        assertThat(variable(instance, "motif")).isEqualTo("Grand compte entreprise");
    }

    @Test
    void particulierPetitDossier_estEligible_pourDossierStandard() {
        ProcessInstance instance = start("PARTICULIER", 10_000);

        assertThat(variable(instance, "statutEligibilite")).isEqualTo("ELIGIBLE");
        assertThat(variable(instance, "motif")).isEqualTo("Dossier particulier standard");
    }

    @Test
    void particulierGrosDossier_estNonEligible_horsCriteres() {
        ProcessInstance instance = start("PARTICULIER", 20_000);

        assertThat(variable(instance, "statutEligibilite")).isEqualTo("NON_ELIGIBLE");
        assertThat(variable(instance, "motif")).isEqualTo("Hors critères d'éligibilité");
    }
}
