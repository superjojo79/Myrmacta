package be.myrmacta.domain.notification.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NotificationChannelTest {

    @ParameterizedTest
    @ValueSource(strings = {"EMAIL", "email", " Email ", "WEBHOOK", "browser"})
    void parse_acceptsKnownChannelsCaseInsensitive(String raw) {
        assertThat(NotificationChannel.parse(raw)).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "SMS", "unknown"})
    void parse_rejectsBlankOrUnknown(String raw) {
        assertThatThrownBy(() -> NotificationChannel.parse(raw))
                .isInstanceOf(NotificationValidationException.class);
    }

    @Test
    void parse_mapsExactEnums() {
        assertThat(NotificationChannel.parse("EMAIL")).isEqualTo(NotificationChannel.EMAIL);
        assertThat(NotificationChannel.parse("WEBHOOK")).isEqualTo(NotificationChannel.WEBHOOK);
        assertThat(NotificationChannel.parse("BROWSER")).isEqualTo(NotificationChannel.BROWSER);
    }
}
