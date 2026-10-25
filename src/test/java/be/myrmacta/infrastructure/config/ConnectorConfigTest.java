package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class ConnectorConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void httpClientBean_isRegisteredWithDefaultConnectTimeout() {
        assertThat(applicationContext.getBeansOfType(HttpClient.class)).hasSize(1);
        HttpClient httpClient = applicationContext.getBean(HttpClient.class);
        assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(5));
    }

    @Test
    void httpClientBean_neverFollowsRedirects() {
        // D-44 / SSRF: a followed redirect is never re-checked against OutboundUrlGuard — the
        // pinned-IP validation would be silently bypassed by any 3xx response. Explicit, not
        // just relying on the JDK's default, so a future change here cannot regress this
        // guarantee unnoticed.
        HttpClient httpClient = applicationContext.getBean(HttpClient.class);
        assertThat(httpClient.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
    }
}
