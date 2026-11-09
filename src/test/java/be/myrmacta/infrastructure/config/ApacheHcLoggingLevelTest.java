package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * TODO §124 (D-CAL-LOG-HC) — org.apache.hc / org.apache.http ne doivent pas être en DEBUG : la ligne de requête
 * peut contenir l'URL secrète d'une source ICS.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class ApacheHcLoggingLevelTest {

    @Test
    void apacheHttpClientLoggersAreNotDebugByDefault() {
        assertThat(LoggerFactory.getLogger("org.apache.hc").isDebugEnabled()).isFalse();
        assertThat(LoggerFactory.getLogger("org.apache.http").isDebugEnabled()).isFalse();
        assertThat(LoggerFactory.getLogger("org.apache.hc.client5.http.wire").isDebugEnabled()).isFalse();
    }
}
