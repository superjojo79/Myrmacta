package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Flags;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Un message déjà remis sous l'ancien nom n'est pas traité une seconde fois. */
class LegacyMailMarkersTest {

    @Test
    void imapRecognizesThePreviousFlagAndTheCurrentOne() {
        assertThat(AngusImapMailbox.alreadyDelivered(new Flags("FlowForgeDelivered"))).isTrue(); // flowforge-legacy
        assertThat(AngusImapMailbox.alreadyDelivered(new Flags("MyrmactaDelivered"))).isTrue();
        assertThat(AngusImapMailbox.alreadyDelivered(new Flags())).isFalse();
        assertThat(AngusImapMailbox.alreadyDelivered(null)).isFalse();
    }

    @Test
    void graphRecognizesThePreviousCategoryAndTheCurrentOne() {
        JsonMapper mapper = JsonMapper.builder().build();
        assertThat(HttpGraphMailbox.wasDelivered(mapper.readTree("{\"categories\":[\"FlowForge-delivered\"]}"))).isTrue(); // flowforge-legacy
        assertThat(HttpGraphMailbox.wasDelivered(mapper.readTree("{\"categories\":[\"Myrmacta-delivered\"]}"))).isTrue();
        assertThat(HttpGraphMailbox.wasDelivered(mapper.readTree("{\"categories\":[]}"))).isFalse();
    }
}
