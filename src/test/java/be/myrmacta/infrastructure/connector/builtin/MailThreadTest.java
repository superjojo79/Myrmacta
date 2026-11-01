package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** TODO §113 (D-MAILROOM-THREAD) — en-têtes de fil d'un e-mail entrant : lus, bornés, jamais pris tels quels. */
class MailThreadTest {

    @Test
    void inReplyToAndReferencesAreReadAsMessageIds() {
        MailThread thread = MailThread.of("<b@example.org>", "<a@example.org>\r\n <b@example.org>", "");

        assertThat(thread.inReplyTo()).isEqualTo("<b@example.org>");
        assertThat(thread.references()).containsExactly("<a@example.org>", "<b@example.org>");
        assertThat(thread.conversationId()).isEmpty();
        assertThat(thread.isEmpty()).isFalse();
    }

    @Test
    void absentOrBlankHeadersGiveAnEmptyThread() {
        assertThat(MailThread.of(null, null, null).isEmpty()).isTrue();
        assertThat(MailThread.of("  ", "", "").isEmpty()).isTrue();
        assertThat(MailThread.NONE.references()).isEmpty();
    }

    @Test
    void textOutsideAngleBracketsIsIgnored() {
        MailThread thread = MailThread.of("pas un identifiant", "commentaire (x) <ok@example.org> bruit", "");

        assertThat(thread.inReplyTo()).isEmpty();
        assertThat(thread.references()).containsExactly("<ok@example.org>");
    }

    @Test
    void onlyTheMostRecentReferencesAreKept() {
        String references = String.join(" ", IntStream.range(0, 30).mapToObj(i -> "<r" + i + "@example.org>").toList());

        List<String> kept = MailThread.of("", references, "").references();

        assertThat(kept).hasSize(MailThread.MAX_REFERENCES);
        assertThat(kept.getLast()).isEqualTo("<r29@example.org>");
        assertThat(kept.getFirst()).isEqualTo("<r" + (30 - MailThread.MAX_REFERENCES) + "@example.org>");
    }

    @Test
    void anOverlongOrControlCharacterIdIsDropped() {
        String overlong = "<" + "a".repeat(MailThread.MAX_ID_CHARS) + "@example.org>";

        MailThread thread = MailThread.of(overlong, "<ok@example.org> <bad\u0000@example.org>", "");

        assertThat(thread.inReplyTo()).isEmpty();
        assertThat(thread.references()).containsExactly("<ok@example.org>");
    }

    @Test
    void anOverlongHeaderIsReadOnlyUpToItsBound_theEndOfReferences_theStartOfInReplyTo() {
        String huge = "<early@example.org> " + "x".repeat(100_000) + " <late@example.org>";

        // Revue de clôture de #217 : References se lit de la fin (les identifiants les plus récents y sont), In-Reply-To du début.
        assertThat(MailThread.of("", huge, "").references()).containsExactly("<late@example.org>");
        assertThat(MailThread.of(huge, "", "").inReplyTo()).isEqualTo("<early@example.org>");
    }

    @Test
    void theGraphConversationIdIsKeptOnlyWhenItLooksLikeOne() {
        assertThat(MailThread.of("", "", " AAQkAGI2_x-y=+/ ").conversationId()).isEqualTo("AAQkAGI2_x-y=+/");
        assertThat(MailThread.of("", "", "a b").conversationId()).isEmpty();
        assertThat(MailThread.of("", "", "c".repeat(MailThread.MAX_ID_CHARS + 1)).conversationId()).isEmpty();
    }
}
