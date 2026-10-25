package be.myrmacta.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.port.out.DossierExportPort;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierSearchPort;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DossierExportPersistenceTest {

    private static final String TENANT = "tenant_dev";

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private DossierExportPort dossierExportPort;

    @Test
    @Transactional
    void streamForExport_returnsFullDossiersIncludingFields() {
        Dossier saved = save(
                "DOS-EXPORT-001",
                "FAC",
                DossierStatus.OUVERT,
                Map.of("priority", "HIGH", "ssn", "123-45-6789"));

        List<Dossier> content;
        try (Stream<Dossier> stream = dossierExportPort.streamForExport(
                TENANT, DossierSearchPort.Filter.restricted(null, Set.of("FAC"), null))) {
            content = stream.filter(d -> d.id().equals(saved.id())).toList();
        }

        assertThat(content).hasSize(1);
        assertThat(content.getFirst().fields()).containsEntry("priority", "HIGH").containsEntry("ssn", "123-45-6789");
    }

    @Test
    @Transactional
    void streamForExport_appliesStatusAndTypeFilters() {
        Dossier facOpen = save("DOS-EXPORT-002", "FAC", DossierStatus.OUVERT, Map.of());
        Dossier facDraft = save("DOS-EXPORT-003", "FAC", DossierStatus.DRAFT, Map.of());
        Dossier ctrOpen = save("DOS-EXPORT-004", "CTR", DossierStatus.OUVERT, Map.of());

        List<Dossier> content;
        try (Stream<Dossier> stream = dossierExportPort.streamForExport(
                TENANT,
                DossierSearchPort.Filter.restricted(DossierStatus.OUVERT, Set.of("FAC"), null))) {
            content = stream.toList();
        }

        assertThat(content).extracting(Dossier::id).contains(facOpen.id())
                .doesNotContain(facDraft.id(), ctrOpen.id());
    }

    @Test
    @Transactional
    void streamForExport_returnsEmptyStreamWhenNoDossierMatches() {
        try (Stream<Dossier> stream = dossierExportPort.streamForExport(
                TENANT, DossierSearchPort.Filter.restricted(null, Set.of("NO-SUCH-TYPE-" + UUID.randomUUID()), null))) {
            assertThat(stream.toList()).isEmpty();
        }
    }

    private Dossier save(String reference, String type, DossierStatus status, Map<String, Object> fields) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-21T10:00:00Z");
        Dossier dossier = new Dossier(
                id, TENANT, reference, type, "label", status, fields, null, 0,
                "creator", now, "creator", now, null, null, null);
        return dossierRepository.save(dossier);
    }
}
