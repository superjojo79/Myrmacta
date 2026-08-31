package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.support.OidcClientTestConfiguration;
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
}
