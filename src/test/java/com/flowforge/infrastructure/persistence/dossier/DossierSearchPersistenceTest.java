package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierListItem;
import com.flowforge.domain.dossier.model.DossierPage;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierSearchPort;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DossierSearchPersistenceTest {

    private static final String TENANT = "tenant_dev";

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private DossierSearchPort dossierSearchPort;

    @Test
    void searchesWithFiltersPaginationAndPrefixCaseInsensitive() {
        Instant t1 = Instant.parse("2026-08-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-08-02T10:00:00Z");
        Instant t3 = Instant.parse("2026-08-03T10:00:00Z");

        Dossier facOpen = save("DOS-FAC-SEARCH-001", "FAC", "Fac 1", DossierStatus.OUVERT, t1);
        Dossier facDraft = save("DOS-FAC-SEARCH-002", "FAC", "Fac 2", DossierStatus.DRAFT, t2);
        Dossier ctrOpen = save("dos-ctr-search-003", "CTR", "Ctr 1", DossierStatus.OUVERT, t3);

        DossierPage page0 = dossierSearchPort.search(
                TENANT,
                DossierSearchPort.Filter.restricted(null, Set.of("FAC", "CTR"), null),
                0,
                2);
        assertThat(page0.totalElements()).isGreaterThanOrEqualTo(3);
        assertThat(page0.content()).hasSize(2);
        assertThat(page0.content().get(0).updatedAt()).isAfterOrEqualTo(page0.content().get(1).updatedAt());

        DossierPage byStatus = dossierSearchPort.search(
                TENANT,
                DossierSearchPort.Filter.restricted(DossierStatus.OUVERT, Set.of("FAC"), null),
                0,
                20);
        assertThat(byStatus.content())
                .extracting(DossierListItem::id)
                .contains(facOpen.id())
                .doesNotContain(facDraft.id(), ctrOpen.id());

        DossierPage byPrefix = dossierSearchPort.search(
                TENANT,
                DossierSearchPort.Filter.unrestricted(null, "dos-fac-search"),
                0,
                20);
        assertThat(byPrefix.content())
                .extracting(DossierListItem::reference)
                .contains(facOpen.reference(), facDraft.reference())
                .doesNotContain(ctrOpen.reference());

        assertThat(byPrefix.content())
                .allSatisfy(item -> assertThat(item).hasNoNullFieldsOrPropertiesExcept());
        // projection must not expose fields — DossierListItem has no fields property
        assertThat(byPrefix.content().getFirst().dossierTypeLabel()).isEmpty();
    }

    private Dossier save(
            String reference, String type, String label, DossierStatus status, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        Instant created = updatedAt.minusSeconds(60);
        Dossier dossier = new Dossier(
                id,
                TENANT,
                reference,
                type,
                label,
                status,
                Map.of("secret", "should-not-load"),
                null,
                0,
                "creator",
                created,
                "creator",
                updatedAt,
                null,
                null);
        return dossierRepository.save(dossier);
    }
}
