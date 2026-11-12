package be.myrmacta.domain.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** TODO §132 (D-I18N-DOSSIER-TEXT) — phrases françaises inchangées, anglais sur la langue {@code en} seulement. */
class ServerDossierTextsTest {

    private static final LocalDate DEADLINE = LocalDate.parse("2026-11-10");

    @Test
    void frenchStaysTheHistoricalWording_includingALocaleThatIsNotEnglish() {
        assertThat(ServerDossierTexts.deadlineEscalationNote(Locale.FRENCH, DEADLINE, null, null, List.of()))
                .isEqualTo("Échéance du 2026-11-10 dépassée : aucun groupe d'escalade");
        assertThat(ServerDossierTexts.deadlineReminderTitle(Locale.FRENCH, "DOS-1")).isEqualTo("Échéance proche — DOS-1");
        assertThat(ServerDossierTexts.deadlineReminderBody(Locale.FRENCH, "DOS-1", DEADLINE))
                .isEqualTo("Le dossier DOS-1 arrive à échéance le 2026-11-10.");
        assertThat(ServerDossierTexts.supplierReplyLabel(Locale.forLanguageTag("fr-BE"), "DOS-COURRIER-2026-000006"))
                .isEqualTo("Réponse DOS-COURRIER-2026-000006");
    }

    @Test
    void englishCoversTheReminderAndAMissingEscalationGroup() {
        assertThat(ServerDossierTexts.deadlineReminderTitle(Locale.UK, "DOS-1")).isEqualTo("Deadline approaching — DOS-1");
        assertThat(ServerDossierTexts.deadlineReminderBody(Locale.ENGLISH, "DOS-1", DEADLINE))
                .isEqualTo("Dossier DOS-1 is due on 2026-11-10.");
        assertThat(ServerDossierTexts.deadlineEscalationNote(Locale.ENGLISH, DEADLINE, "DIRECTION", null, List.of()))
                .isEqualTo("Deadline of 2026-11-10 passed: escalation group not found (DIRECTION)");
        assertThat(ServerDossierTexts.deadlineEscalationNote(Locale.ENGLISH, DEADLINE, "DIRECTION", "DIRECTION", List.of()))
                .isEqualTo("Deadline of 2026-11-10 passed: group DIRECTION notified, no open task");
    }
}
