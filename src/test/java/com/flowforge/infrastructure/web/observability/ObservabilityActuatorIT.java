package com.flowforge.infrastructure.web.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.support.OidcClientTestConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "MANAGEMENT_PORT=0")
@Import(OidcClientTestConfiguration.class)
class ObservabilityActuatorIT {

    @LocalServerPort
    int appPort;

    @LocalManagementPort
    int managementPort;

    @Test
    void prometheusLivesOnManagementPortOnly() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> scrape = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + "/actuator/prometheus"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body()).contains("flowforge_outbox_trigger_oldest_pending_age_seconds");

        HttpResponse<String> app = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + appPort + "/actuator/prometheus"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(app.statusCode()).isNotEqualTo(200);

        HttpResponse<String> live = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + "/actuator/health/liveness"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> ready = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + "/actuator/health/readiness"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(live.statusCode()).isBetween(200, 299);
        assertThat(ready.statusCode()).isBetween(200, 299);

        HttpResponse<String> beans = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + "/actuator/beans"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> env = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + "/actuator/env"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(beans.statusCode()).isEqualTo(404);
        assertThat(env.statusCode()).isEqualTo(404);
    }
}
