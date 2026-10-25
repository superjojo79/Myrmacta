package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class SecurityCookiePropertiesTest {

    @Test
    void defaultsSecureToTrueWhenMissing() {
        var binder = new Binder(new MapConfigurationPropertySource(Map.of()));
        SecurityCookieProperties props = binder
                .bind("app.security.cookie", Bindable.of(SecurityCookieProperties.class))
                .orElseGet(() -> new SecurityCookieProperties(true));

        assertThat(props.secure()).isTrue();
    }

    @Test
    void bindsSecureFalseOverride() {
        var binder = new Binder(new MapConfigurationPropertySource(
                Map.of("app.security.cookie.secure", "false")));
        SecurityCookieProperties props = binder
                .bind("app.security.cookie", Bindable.of(SecurityCookieProperties.class))
                .get();

        assertThat(props.secure()).isFalse();
    }
}
