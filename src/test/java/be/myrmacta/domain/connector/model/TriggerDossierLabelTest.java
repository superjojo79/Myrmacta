package be.myrmacta.domain.connector.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** TODO §129 (D-TRIGGER-LABEL) — libellé lisible d'un dossier créé par un déclencheur. */
class TriggerDossierLabelTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-12T08:15:00Z"), ZoneOffset.UTC);

    @Test
    void prefersObjetThenSubjectThenSender() {
        assertThat(TriggerDossierLabel.resolve("COURRIER", Map.of("objet", "  Facture 42  "), CLOCK))
                .isEqualTo("Facture 42");
        assertThat(TriggerDossierLabel.resolve("COURRIER", Map.of("subject", "Hello"), CLOCK)).isEqualTo("Hello");
        assertThat(TriggerDossierLabel.resolve("COURRIER", Map.of("expediteur", "a@b.c"), CLOCK)).isEqualTo("a@b.c");
        assertThat(TriggerDossierLabel.resolve("COURRIER", Map.of("from", "x@y.z"), CLOCK)).isEqualTo("x@y.z");
        assertThat(TriggerDossierLabel.resolve(
                        "COURRIER", Map.of("subject", "ignored", "objet", "prioritaire"), CLOCK))
                .isEqualTo("prioritaire");
    }

    @Test
    void fallsBackToTypeAndUtcDate_whenNoReadableField() {
        assertThat(TriggerDossierLabel.resolve("SINISTRE", Map.of(), CLOCK)).isEqualTo("SINISTRE — 2026-11-12");
        assertThat(TriggerDossierLabel.resolve("SINISTRE", Map.of("objet", "  "), CLOCK))
                .isEqualTo("SINISTRE — 2026-11-12");
        assertThat(TriggerDossierLabel.resolve("SINISTRE", Map.of("objet", 12), CLOCK))
                .isEqualTo("SINISTRE — 2026-11-12");
    }

    @Test
    void truncatesToTwoHundredCharacters() {
        String longObjet = "x".repeat(TriggerDossierLabel.MAX_CHARS + 40);
        String label = TriggerDossierLabel.resolve("COURRIER", Map.of("objet", longObjet), CLOCK);
        assertThat(label).hasSize(TriggerDossierLabel.MAX_CHARS).isEqualTo("x".repeat(TriggerDossierLabel.MAX_CHARS));
    }

    @Test
    void ignoresBlankKeysAndNullMap() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("objet", null);
        fields.put("subject", "");
        assertThat(TriggerDossierLabel.resolve("COURRIER", fields, CLOCK)).isEqualTo("COURRIER — 2026-11-12");
        assertThat(TriggerDossierLabel.resolve("COURRIER", null, CLOCK)).isEqualTo("COURRIER — 2026-11-12");
    }
}
