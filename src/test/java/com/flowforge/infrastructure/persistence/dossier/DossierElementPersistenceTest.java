package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DossierElementPersistenceTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private DossierElementRepository dossierElementRepository;

    @Test
    void saveAndLoad_roundTripsMetadataAndNullWorkflowStepId() {
        UUID id = UUID.randomUUID();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("templateKey", "contrat");
        metadata.put("contentType", "application/pdf");
        metadata.put("variantKey", "premium");
        metadata.put("locale", "fr");

        DossierElement saved = dossierElementRepository.save(new DossierElement(
                id,
                UUID.randomUUID(),
                TENANT_ID,
                ElementType.PRODUCED_DOC,
                "Document généré — persist",
                TENANT_ID + "/" + UUID.randomUUID() + ".pdf",
                metadata,
                "system",
                Instant.parse("2026-08-04T05:00:00Z"),
                null));

        assertThat(saved.workflowStepId()).isNull();
        assertThat(saved.metadata())
                .containsEntry("templateKey", "contrat")
                .containsEntry("contentType", "application/pdf")
                .containsEntry("variantKey", "premium")
                .containsEntry("locale", "fr");

        DossierElement loaded = dossierElementRepository
                .findByIdAndTenantId(id, TENANT_ID)
                .orElseThrow();

        assertThat(loaded.workflowStepId()).isNull();
        assertThat(loaded.metadata()).isEqualTo(metadata);
        assertThat(loaded.type()).isEqualTo(ElementType.PRODUCED_DOC);
        assertThat(loaded.label()).isEqualTo("Document généré — persist");
    }
}
