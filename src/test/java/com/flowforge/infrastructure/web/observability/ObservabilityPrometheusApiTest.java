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
import org.springframework.context.annotation.Import;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "MANAGEMENT_PORT=0")
@Import(OidcClientTestConfiguration.class)
class ObservabilityPrometheusApiTest {

    @LocalManagementPort
    int managementPort;

    @Test
    void prometheus_isScrapableWithoutOidc_andListsFlowforgeMeters() throws Exception {
        HttpResponse<String> scrape = get("/actuator/prometheus");
        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body())
                .contains("flowforge_outbox_trigger_oldest_pending_age_seconds")
                .contains("flowforge_outbox_notification_oldest_pending_age_seconds")
                .contains("flowforge_outbox_purge_oldest_unfinished_age_seconds")
                .contains("flowforge_flowable_jobs_dead_letter")
                .contains("flowforge_flowable_jobs_exception")
                .contains("flowforge_pdf_render_seconds")
                .contains("flowforge_notification_webhook_dead")
                .contains("flowforge_sse_emitters_active")
                .doesNotContain("secret");
    }

    @Test
    void healthProbes_areUp_andSensitiveEndpointsAreHidden() throws Exception {
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
        assertHidden(get("/actuator/beans"));
        assertHidden(get("/actuator/env"));
        assertHidden(get("/actuator/heapdump"));
    }

    private static void assertHidden(HttpResponse<String> response) {
        assertThat(response.statusCode()).isNotEqualTo(200);
        assertThat(response.body() == null ? "" : response.body()).doesNotContain("\"contexts\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + path))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }
}
