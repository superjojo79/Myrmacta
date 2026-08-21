package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
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
        UUID dossierId = UUID.randomUUID();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("templateKey", "contrat");
        metadata.put("contentType", "application/pdf");
        metadata.put("variantKey", "premium");
        metadata.put("locale", "fr");

        DossierElement saved = dossierElementRepository.save(new DossierElement(
                id,
                dossierId,
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

    @Test
    void findByDossierIdOrderByAddedAtAsc_returnsElementsForDossierInChronologicalOrder() {
        UUID dossierId = UUID.randomUUID();
        DossierElement newer = dossierElement(dossierId, "newer", "2026-08-04T05:05:00Z");
        DossierElement older = dossierElement(dossierId, "older", "2026-08-04T05:00:00Z");
        DossierElement otherDossier = dossierElement(UUID.randomUUID(), "other", "2026-08-04T04:00:00Z");
        dossierElementRepository.save(newer);
        dossierElementRepository.save(older);
        dossierElementRepository.save(otherDossier);

        assertThat(dossierElementRepository.findByDossierIdOrderByAddedAtAsc(dossierId))
                .extracting(DossierElement::label)
                .containsExactly("older", "newer");
    }

    @Test
    void pageByDossierId_returnsPageOrderedChronologicallyWithTotal() {
        UUID dossierId = UUID.randomUUID();
        DossierElement newer = dossierElement(dossierId, "newer", "2026-08-04T05:05:00Z");
        DossierElement older = dossierElement(dossierId, "older", "2026-08-04T05:00:00Z");
        DossierElement otherDossier = dossierElement(UUID.randomUUID(), "other", "2026-08-04T04:00:00Z");
        dossierElementRepository.save(newer);
        dossierElementRepository.save(older);
        dossierElementRepository.save(otherDossier);

        PageResult<DossierElement> page =
                dossierElementRepository.pageByDossierId(dossierId, PageQuery.of(0, 20));

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content())
                .extracting(DossierElement::label)
                .containsExactly("older", "newer");
    }

    @Test
    void pageByDossierId_dossierWithoutElements_returnsEmptyPage() {
        PageResult<DossierElement> page =
                dossierElementRepository.pageByDossierId(UUID.randomUUID(), PageQuery.of(0, 20));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    private static DossierElement dossierElement(UUID dossierId, String label, String addedAt) {
        return new DossierElement(
                UUID.randomUUID(),
                dossierId,
                TENANT_ID,
                ElementType.PRODUCED_DOC,
                label,
                TENANT_ID + "/" + UUID.randomUUID() + ".pdf",
                Map.of(),
                "system",
                Instant.parse(addedAt),
                null);
    }
}
