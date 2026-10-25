package be.myrmacta.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import be.myrmacta.infrastructure.config.SecurityHeadersProperties;
import be.myrmacta.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * IT HSTS hors profil {@code test} (A4-HSTS-001) : le customizer prod est
 * {@code @Primary} parce que {@code @SpringBootTest} active toujours {@code test},
 * ce qui coupe HSTS via {@code hstsEnabled()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({OidcClientTestConfiguration.class, SecurityHeadersHstsApiTest.ProdLikeHeadersConfig.class})
class SecurityHeadersHstsApiTest {

    static final String HSTS_VALUE = "max-age=31536000 ; includeSubDomains";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicPeer_spoofedForwardedProto_doesNotEmitHsts() throws Exception {
        String header = performBff("/api/auth/me", remoteAddr("203.0.113.10"), forwardedHttps());

        assertThat(header).isNull();
    }

    @Test
    void loopbackPeer_forwardedHttps_emitsHsts() throws Exception {
        String header = performBff("/api/auth/me", remoteAddr("127.0.0.1"), forwardedHttps());

        assertThat(header).isEqualTo(HSTS_VALUE);
    }

    @Test
    void privatePeer_forwardedHttps_emitsHsts() throws Exception {
        String header = performBff("/api/auth/me", remoteAddr("172.18.0.4"), forwardedHttps());

        assertThat(header).isEqualTo(HSTS_VALUE);
    }

    @Test
    void loopbackPeer_cleartextHttp_doesNotEmitHsts() throws Exception {
        String header = performBff("/api/auth/me", remoteAddr("127.0.0.1"));

        assertThat(header).isNull();
    }

    @Test
    void loopbackPeer_forwardedForPublicClient_stillEmitsHsts() throws Exception {
        String header = performBff(
                "/api/auth/me",
                remoteAddr("127.0.0.1"),
                forwardedHttps(),
                request -> {
                    request.addHeader("X-Forwarded-For", "203.0.113.50");
                    return request;
                });

        assertThat(header).isEqualTo(HSTS_VALUE);
    }

    private String performBff(String path, RequestPostProcessor... extras) throws Exception {
        var builder = get(path)
                .with(oidcLogin()
                        .idToken(token -> token
                                .claim("tenant_id", "tenant_dev")
                                .claim("email", "ada@example.com")
                                .claim("name", "Ada Lovelace"))
                        .authorities(new SimpleGrantedAuthority("ROLE_WORKFLOW_ADMIN")));
        for (RequestPostProcessor extra : extras) {
            builder = builder.with(extra);
        }
        return mockMvc.perform(builder).andReturn().getResponse().getHeader("Strict-Transport-Security");
    }

    private static RequestPostProcessor remoteAddr(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }

    private static RequestPostProcessor forwardedHttps() {
        return request -> {
            request.addHeader("X-Forwarded-Proto", "https");
            return request;
        };
    }

    @TestConfiguration
    static class ProdLikeHeadersConfig {

        @Bean
        @Primary
        SecurityHeadersCustomizer prodLikeSecurityHeadersCustomizer(SecurityHeadersProperties properties) {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("prod");
            return new SecurityHeadersCustomizer(properties, env);
        }
    }
}
