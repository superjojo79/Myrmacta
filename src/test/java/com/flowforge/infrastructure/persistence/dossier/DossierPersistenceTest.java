package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.out.DossierReferenceGenerator;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DossierPersistenceTest {

    @Autowired
    private DossierTypeRepository dossierTypeRepository;

    @Autowired
    private DossierReferenceGenerator referenceGenerator;

    @Autowired
    private DossierRepository dossierRepository;

    @Test
    void savesAndLoadsDossierTypeWithFieldDefinitions() {
        var type = DossierType.inherit(
                UUID.randomUUID(),
                "tenant_dev",
                "SINISTRE",
                "Sinistre",
                "sinistre-process",
                true,
                false,
                List.of(
                        new FieldDefinition(
                                "victim",
                                "Victim",
                                FieldType.STRING,
                                true,
                                null,
                                List.of(),
                                "identity",
                                true),
                        new FieldDefinition(
                                "severity",
                                "Severity",
                                FieldType.SELECT,
                                false,
                                "LOW",
                                List.of("LOW", "HIGH"),
                                "details")));

        dossierTypeRepository.save(type);

        assertThat(dossierTypeRepository.findByCode("tenant_dev", "SINISTRE"))
                .contains(type);
        assertThat(dossierTypeRepository.findByCode("tenant_dev", "SINISTRE"))
                .get()
                .extracting(DossierType::fieldDefinitions)
                .asList()
                .first()
                .extracting("sensitive")
                .isEqualTo(true);
        assertThat(dossierTypeRepository.findAll("tenant_dev"))
                .contains(type);
    }

    @Test
    void generatesSequentialReferencesForCurrentUtcYear() {
        String first = referenceGenerator.generate("tenant_dev", "SINISTRE");
        String second = referenceGenerator.generate("tenant_dev", "SINISTRE");

        assertThat(first).matches("DOS-SINISTRE-\\d{4}-000001");
        assertThat(second).isEqualTo(first.substring(0, first.length() - 6) + "000002");
    }

    @Test
    void savingDossierTwiceWithStaleVersionThrowsOptimisticLockException() {
        Dossier original = dossier(0, DossierStatus.DRAFT);
        dossierRepository.save(original);
        Dossier persisted = dossierRepository.findById(original.id()).orElseThrow();

        dossierRepository.save(persisted.withStatus(DossierStatus.OUVERT, "editor-1", Instant.parse("2026-07-31T12:05:00Z")));

        assertThatThrownBy(() -> dossierRepository.save(
                        persisted.withStatus(DossierStatus.CLOTURE, "editor-2", Instant.parse("2026-07-31T12:10:00Z"))))
                .isInstanceOfAny(ObjectOptimisticLockingFailureException.class, OptimisticLockException.class);
    }

    private static Dossier dossier(long version, DossierStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T12:00:00Z");
        return new Dossier(
                id,
                "tenant_dev",
                "DOS-STALE-" + id.toString().replace("-", "").substring(0, 12),
                "STALE",
                "Stale dossier",
                status,
                Map.of("priority", "NORMAL"),
                null,
                version,
                "creator",
                now,
                "creator",
                now,
                null,
                null,
                null);
    }
}
