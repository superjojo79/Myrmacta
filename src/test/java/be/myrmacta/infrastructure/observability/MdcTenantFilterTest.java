package be.myrmacta.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.infrastructure.config.TenantProperties;
import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MdcTenantFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void putsTenantIdDuringChainAndClearsAfterwards() throws Exception {
        MdcTenantFilter filter = new MdcTenantFilter(new TenantProperties("tenant_dev"));
        AtomicReference<String> during = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(
                    jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                during.set(MDC.get(MdcScope.TENANT_ID));
            }
        };

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(during.get()).isEqualTo("tenant_dev");
        assertThat(MDC.get(MdcScope.TENANT_ID)).isNull();
    }

    @Test
    void clearsTenantIdWhenChainThrows() {
        MdcTenantFilter filter = new MdcTenantFilter(new TenantProperties("tenant_dev"));
        MockFilterChain throwing = new MockFilterChain() {
            @Override
            public void doFilter(
                    jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws ServletException {
                throw new ServletException("boom");
            }
        };

        assertThatThrownBy(() ->
                        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), throwing))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get(MdcScope.TENANT_ID)).isNull();
    }
}
