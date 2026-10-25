package be.myrmacta.infrastructure.web.observability;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.support.OidcClientTestConfiguration;
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
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> scrape = get(client, managementPort, "/actuator/prometheus");
        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body())
                .contains("myrmacta_outbox_trigger_oldest_pending_age_seconds")
                .contains("myrmacta_outbox_notification_oldest_pending_age_seconds")
                .contains("myrmacta_outbox_purge_oldest_unfinished_age_seconds")
                .contains("myrmacta_flowable_jobs_dead_letter")
                .contains("myrmacta_flowable_jobs_exception")
                .contains("myrmacta_pdf_render_seconds")
                .contains("myrmacta_notification_webhook_dead")
                .contains("myrmacta_sse_emitters_active");

        HttpResponse<String> app = get(client, appPort, "/actuator/prometheus");
        assertThat(app.body()).doesNotContain("myrmacta_outbox_trigger_oldest_pending_age_seconds");

        HttpResponse<String> liveness = get(client, managementPort, "/actuator/health/liveness");
        assertThat(liveness.statusCode()).isBetween(200, 299);
        assertThat(liveness.headers().firstValue("Content-Security-Policy").orElse(""))
                .startsWith(be.myrmacta.infrastructure.security.SecurityHeadersCustomizer.CONTENT_SECURITY_POLICY);
        assertThat(liveness.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(liveness.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(get(client, managementPort, "/actuator/health/readiness").statusCode()).isBetween(200, 299);

        assertHidden(get(client, managementPort, "/actuator/beans"));
        assertHidden(get(client, managementPort, "/actuator/env"));
        assertHidden(get(client, managementPort, "/actuator/heapdump"));
    }

    private static void assertHidden(HttpResponse<String> response) {
        assertThat(response.statusCode()).isNotEqualTo(200);
        assertThat(response.body() == null ? "" : response.body()).doesNotContain("\"contexts\"");
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
