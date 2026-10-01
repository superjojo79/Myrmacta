package com.flowforge.infrastructure.persistence.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.workflow.model.BpmnDraft;
import com.flowforge.domain.workflow.model.ConfigPublication;
import com.flowforge.domain.workflow.model.DmnDraft;
import com.flowforge.domain.workflow.model.DraftArtifactType;
import com.flowforge.domain.workflow.model.Provenance;
import com.flowforge.domain.workflow.model.ProposedWorkflowConfig;
import com.flowforge.domain.workflow.model.PublicationMode;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.port.out.BpmnDraftRepository;
import com.flowforge.domain.workflow.port.out.ConfigPublicationRepository;
import com.flowforge.domain.workflow.port.out.DmnDraftRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** TODO §82 (D6) — persistance des brouillons (provenance, configuration proposée), purge ciblée et journal des publications. */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DraftPersistenceTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant OLD = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2001-01-01T00:00:00Z");

    @Autowired
    private BpmnDraftRepository bpmnDrafts;

    @Autowired
    private DmnDraftRepository dmnDrafts;

    @Autowired
    private ConfigPublicationRepository publications;

    private static String key() {
        return "persist" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void provenanceAndProposedConfigurationRoundTrip() {
        String key = key();
        ProposedWorkflowConfig config = new ProposedWorkflowConfig("Libellé",
                List.of(new WorkflowStatusDefinition("S", "Start", true, StatusOutcome.NONE)),
                List.of(DossierStatus.OUVERT, DossierStatus.EN_COURS), DossierStatus.OUVERT, List.of("u1"));
        Instant stamp = Instant.parse("2026-10-01T10:00:00.123456Z");
        bpmnDrafts.save(new BpmnDraft(TENANT, key, "<x/>", stamp, "alice", Provenance.UI, config, "pilot",
                Provenance.ASSISTANT, "agent-1"));

        BpmnDraft loaded = bpmnDrafts.findByTenantIdAndProcessDefinitionKey(TENANT, key).orElseThrow();

        assertThat(loaded.provenance()).isEqualTo(Provenance.UI);
        assertThat(loaded.configProvenance()).as("l'origine de la config survit à la reprise du diagramme").isEqualTo(Provenance.ASSISTANT);
        assertThat(loaded.configAuthor()).isEqualTo("agent-1");
        assertThat(loaded.proposedConfig()).isEqualTo(config);
        assertThat(loaded.pilotUserId()).isEqualTo("pilot");
        assertThat(loaded.updatedAt()).isEqualTo(stamp);
        assertThat(bpmnDrafts.findAllByTenantId(TENANT)).extracting(BpmnDraft::processDefinitionKey).contains(key);
    }

    @Test
    void anEditorDraftKeepsTheLegacyShape() {
        String key = key();
        bpmnDrafts.save(new BpmnDraft(TENANT, key, "<x/>", Instant.now(), "alice"));
        BpmnDraft loaded = bpmnDrafts.findByTenantIdAndProcessDefinitionKey(TENANT, key).orElseThrow();
        assertThat(loaded.provenance()).isEqualTo(Provenance.UI);
        assertThat(loaded.proposedConfig()).isNull();
    }

    @Test
    void theRetentionPurgeRemovesStaleAutomatedDraftsAndNeverEditorDrafts() {
        String agentBpmn = key();
        String humanBpmn = key();
        String freshAgentBpmn = key();
        String agentDmn = key();
        String humanDmn = key();
        bpmnDrafts.save(new BpmnDraft(TENANT, agentBpmn, "<x/>", OLD, "agent", Provenance.MCP, null, null));
        bpmnDrafts.save(new BpmnDraft(TENANT, humanBpmn, "<x/>", OLD, "alice"));
        bpmnDrafts.save(new BpmnDraft(TENANT, freshAgentBpmn, "<x/>", Instant.now(), "agent", Provenance.MCP, null, null));
        dmnDrafts.save(new DmnDraft(TENANT, agentDmn, "<d/>", OLD, "agent", Provenance.ASSISTANT, "n", null));
        dmnDrafts.save(new DmnDraft(TENANT, humanDmn, "<d/>", OLD, "alice"));

        int bpmn = bpmnDrafts.deleteAutomatedOlderThan(TENANT, CUTOFF);
        int dmn = dmnDrafts.deleteAutomatedOlderThan(TENANT, CUTOFF);

        assertThat(bpmn).isGreaterThanOrEqualTo(1);
        assertThat(dmn).isGreaterThanOrEqualTo(1);
        assertThat(bpmnDrafts.findByTenantIdAndProcessDefinitionKey(TENANT, agentBpmn)).isEmpty();
        assertThat(bpmnDrafts.findByTenantIdAndProcessDefinitionKey(TENANT, humanBpmn)).isPresent();
        assertThat(bpmnDrafts.findByTenantIdAndProcessDefinitionKey(TENANT, freshAgentBpmn)).isPresent();
        assertThat(dmnDrafts.findByTenantIdAndDecisionKey(TENANT, agentDmn)).isEmpty();
        assertThat(dmnDrafts.findByTenantIdAndDecisionKey(TENANT, humanDmn)).isPresent();
        // idempotent : un second passage ne supprime plus rien de ce lot
        assertThat(bpmnDrafts.deleteAutomatedOlderThan(TENANT, CUTOFF)).isZero();
    }

    @Test
    void theJournalKeepsPublicationsMostRecentFirst() {
        String key = key();
        publications.save(new ConfigPublication(UUID.randomUUID(), TENANT, DraftArtifactType.DMN, key, 1, Provenance.MCP,
                "agent", "alice", Instant.parse("2026-10-01T10:00:00Z"), PublicationMode.MANUAL));
        publications.save(new ConfigPublication(UUID.randomUUID(), TENANT, DraftArtifactType.DMN, key, 2, Provenance.UI,
                "bob", "bob", Instant.parse("2026-10-02T10:00:00Z"), PublicationMode.MANUAL));

        publications.save(new ConfigPublication(UUID.randomUUID(), TENANT, DraftArtifactType.BPMN, key, 1, Provenance.UI,
                "bob", "bob", Instant.parse("2026-10-03T10:00:00Z"), PublicationMode.MANUAL, Provenance.MCP, "agent-1"));
        assertThat(publications.findByArtifact(TENANT, DraftArtifactType.BPMN, key, 1).getFirst().configProvenance())
                .isEqualTo(Provenance.MCP);
        assertThat(publications.findByArtifact(TENANT, DraftArtifactType.DMN, key, 10))
                .extracting(ConfigPublication::version).containsExactly(2, 1);
        assertThat(publications.findRecent(TENANT, 200)).extracting(ConfigPublication::artifactKey).contains(key);
    }
}
