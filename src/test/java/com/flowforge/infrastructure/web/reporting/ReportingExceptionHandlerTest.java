package com.flowforge.infrastructure.web.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.reporting.model.SlaCohortTooLargeException;
import com.flowforge.domain.reporting.model.TenantSlaSettingsNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

class ReportingExceptionHandlerTest {

    private final ReportingExceptionHandler handler = new ReportingExceptionHandler();

    @Test
    void mapsAccessDeniedTo403() {
        var problem = handler.handleAccessDenied(
                new AccessDeniedException("Access denied for SLA reports"),
                new MockHttpServletRequest("GET", "/api/reports/sla"),
                mock(Authentication.class));

        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getTitle()).isEqualTo("Access denied");
    }

    @Test
    void mapsMissingSettingsTo404() {
        var problem = handler.handleSettingsNotFound(new TenantSlaSettingsNotFoundException("tenant_dev"));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getTitle()).isEqualTo("SLA settings not found");
    }

    @Test
    void mapsIllegalArgumentAndCohortTooLargeTo400() {
        var inverted = handler.handleBadRequest(new IllegalArgumentException("from must be before to"));
        var tooLarge = handler.handleBadRequest(new SlaCohortTooLargeException(10_001));

        assertThat(inverted.getStatus()).isEqualTo(400);
        assertThat(tooLarge.getStatus()).isEqualTo(400);
        assertThat(tooLarge.getTitle()).isEqualTo("Bad request");
    }
}
