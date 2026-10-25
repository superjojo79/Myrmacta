package be.myrmacta.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DossierTypeAclSchemaIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void permissionCheck_acceptsViewAndEdit() {
        assertThatCode(() -> insertRow(UUID.randomUUID(), "VIEW")).doesNotThrowAnyException();
        assertThatCode(() -> insertRow(UUID.randomUUID(), "EDIT")).doesNotThrowAnyException();
    }

    @Test
    void permissionCheck_rejectsUnknownValue() {
        assertThatThrownBy(() -> insertRow(UUID.randomUUID(), "DELETE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertRow(UUID id, String permission) {
        jdbc.update(
                """
                INSERT INTO dossier_type_acl (id, tenant_id, dossier_type_code, subject_user_id, permission)
                VALUES (?, 'tenant_dev', 'sinistre', ?, ?)
                """,
                id,
                "user-" + id,
                permission);
    }
}
