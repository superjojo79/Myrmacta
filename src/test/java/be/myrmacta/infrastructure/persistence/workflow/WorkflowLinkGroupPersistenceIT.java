package be.myrmacta.infrastructure.persistence.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** TODO §114 (D-GROUP-LINK) — groupe destinataire porté par un lien de workflow : pose, remplacement, suppression avec le lien. */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class WorkflowLinkGroupPersistenceIT {

    private static final String TENANT = "tenant_dev";

    @Autowired
    private WorkflowLinkGroupRepository groups;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID dossierId;

    @BeforeEach
    void setUp() {
        dossierId = UUID.randomUUID();
        jdbc.update("""
                insert into dossier (id, tenant_id, reference, dossier_type_code, label, status, fields, version, created_by, created_at)
                values (?, ?, ?, 'FACTURE', 'Dossier', 'OUVERT', '{}'::jsonb, 0, 'it', now())
                """, dossierId, TENANT, "DOS-IT-" + dossierId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from dossier_workflow_link where dossier_id = ?", dossierId);
        jdbc.update("delete from dossier where id = ?", dossierId);
    }

    private int sequence;

    /** Un lien terminé par appel : un dossier n'a qu'un lien sans date de fin à la fois (index unique partiel). */
    private UUID link() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into dossier_workflow_link (id, dossier_id, tenant_id, process_definition_key, sequence, status, trigger_ref, started_at, ended_at)
                values (?, ?, ?, 'traiter', ?, 'COMPLETED', 'it', now(), now())
                """, id, dossierId, TENANT, ++sequence);
        return id;
    }

    @Test
    void anAssignedGroupIsFoundForThatLinkOnly() {
        UUID linked = link();
        UUID other = link();

        groups.assign(TENANT, linked, "DIRECTION");

        assertThat(groups.findGroup(linked)).contains("DIRECTION");
        assertThat(groups.findGroup(other)).isEmpty();
        assertThat(groups.findGroup(UUID.randomUUID())).isEmpty();
    }

    @Test
    void assigningAgainReplacesTheGroupWithoutAddingARow() {
        UUID linked = link();
        groups.assign(TENANT, linked, "DIRECTION");

        groups.assign(TENANT, linked, "COMPTA");

        assertThat(groups.findGroup(linked)).contains("COMPTA");
        assertThat(jdbc.queryForObject("select count(*) from workflow_link_group where link_id = ?", Integer.class, linked)).isEqualTo(1);
    }

    @Test
    void theGroupDisappearsWithItsLink() {
        UUID linked = link();
        groups.assign(TENANT, linked, "DIRECTION");

        jdbc.update("delete from dossier_workflow_link where id = ?", linked);

        assertThat(groups.findGroup(linked)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from workflow_link_group where link_id = ?", Integer.class, linked)).isZero();
    }

    @Test
    void aGroupCannotBeAssignedToAnUnknownLink() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> groups.assign(TENANT, unknown, "DIRECTION")).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(groups.findGroup(unknown)).isEmpty();
    }
}
