package com.flowforge.infrastructure.web.observability;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class ObservabilityPrometheusApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prometheus_isScrapableWithoutOidc_andListsFlowforgeMeters() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("flowforge_outbox_trigger_oldest_pending_age_seconds")))
                .andExpect(content().string(containsString("flowforge_outbox_notification_oldest_pending_age_seconds")))
                .andExpect(content().string(containsString("flowforge_outbox_purge_oldest_unfinished_age_seconds")))
                .andExpect(content().string(containsString("flowforge_flowable_jobs_dead_letter")))
                .andExpect(content().string(containsString("flowforge_flowable_jobs_exception")))
                .andExpect(content().string(containsString("flowforge_pdf_render_seconds")))
                .andExpect(content().string(containsString("flowforge_notification_webhook_dead")))
                .andExpect(content().string(containsString("flowforge_sse_emitters_active")));
    }

    @Test
    void healthProbes_areUp_andSensitiveEndpointsAreHidden() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/heapdump")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(content().string(not(containsString("secret"))));
    }
}
