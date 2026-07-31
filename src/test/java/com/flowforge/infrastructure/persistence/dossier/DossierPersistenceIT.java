package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.out.DossierReferenceGenerator;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DossierPersistenceIT {

    @Autowired
    private DossierTypeRepository dossierTypeRepository;

    @Autowired
    private DossierReferenceGenerator referenceGenerator;

    @Test
    void savesAndLoadsDossierTypeWithFieldDefinitions() {
        var type = new DossierType(
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
                                "identity"),
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
}
