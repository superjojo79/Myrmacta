package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.model.RelationType;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.workflow.model.StatusOutcome;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.dossier.ChildDossierRelationConfirmer;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class CreateChildDossierTaskIT {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired
    private DossierTypeRepository typeRepository;

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private DossierRelationRepository relationRepository;

    @Autowired
    private DossierWorkflowLinkRepository linkRepository;

    @Autowired
    private WorkflowTypeConfigRepository configRepository;

    @Autowired
    private ChildDossierRelationConfirmer confirmer;

    @Test
    void serviceTask_createsChildRelationAndWorkflow_andReplayDoesNotDuplicate() {
        String tenantId = tenantProperties.id();
        String parentType = "PAR_" + unique();
        String childType = "CHD_" + unique();
        typeRepository.save(type(tenantId, parentType, "Parent"));
        typeRepository.save(type(tenantId, childType, "Child"));
        if (configRepository.findByWorkflowKey(tenantId, "smokeProcess").isEmpty()) {
            configRepository.save(smokeConfig(tenantId, "smokeProcess"));
        }

        Dossier parent = dossierRepository.save(dossier(tenantId, parentType, "Parent dossier"));
        runtimeService.startProcessInstanceByKeyAndTenantId(
                "createChildDossierSmoke",
                "child-smoke-" + UUID.randomUUID(),
                Map.of(
                        "dossierId", parent.id().toString(),
                        "childDossierTypeCode", childType,
                        "childDossierLabel", "Child dossier",
                        "relationType", "PARENT_CHILD",
                        "workflowProcessKey", "smokeProcess",
                        "childDossierFields", Map.of(),
                        "childWorkflowVariables", Map.of()),
                tenantId);

        var relations = relationRepository.findByDossier(tenantId, parent.id());
        assertThat(relations).hasSize(1);
        assertThat(relations.getFirst().type()).isEqualTo(RelationType.PARENT_CHILD);
        UUID childId = relations.getFirst().targetDossierId();
        Dossier child = dossierRepository.findById(childId).orElseThrow();
        assertThat(child.dossierTypeCode()).isEqualTo(childType);
        List<DossierWorkflowLink> links = linkRepository.findByDossierIdOrderBySequence(childId);
        assertThat(links).isNotEmpty();
        assertThat(links.getFirst().processInstanceId()).isNotBlank();

        UUID replayed = confirmer.confirm(
                tenantId,
                parent.id(),
                childType,
                "Child dossier replay",
                Map.of(),
                RelationType.PARENT_CHILD,
                relations.getFirst().idempotencyKey());
        assertThat(replayed).isEqualTo(childId);
        assertThat(relationRepository.findByDossier(tenantId, parent.id())).hasSize(1);
    }

    private static DossierType type(String tenantId, String code, String label) {
        return DossierType.inherit(UUID.randomUUID(), tenantId, code, label, "wf-" + code, true, false, List.of());
    }

    private static WorkflowTypeConfig smokeConfig(String tenantId, String workflowKey) {
        return new WorkflowTypeConfig(
                UUID.randomUUID(),
                tenantId,
                workflowKey,
                "Smoke",
                List.of(
                        new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE),
                        new WorkflowStatusDefinition("COMPLETED", "Completed", false, StatusOutcome.COMPLETED)),
                List.of(DossierStatus.OUVERT, DossierStatus.EN_COURS, DossierStatus.SUSPENDU),
                null,
                List.of());
    }

    private static Dossier dossier(String tenantId, String typeCode, String label) {
        Instant now = Instant.parse("2026-08-15T08:00:00Z");
        UUID id = UUID.randomUUID();
        return new Dossier(
                id,
                tenantId,
                "DOS-" + typeCode + "-" + id.toString().substring(0, 8),
                typeCode,
                label,
                DossierStatus.OUVERT,
                Map.of(),
                null,
                0,
                "creator",
                now,
                "creator",
                now,
                null,
                null,
                null);
    }

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
