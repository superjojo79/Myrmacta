package be.myrmacta.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Stub {@link ClientRegistrationRepository} — pas de discovery OIDC HTTP en tests.
 */
@TestConfiguration
public class OidcClientTestConfiguration {

    @Bean
    @Primary
    ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(oidcRegistration());
    }

    static ClientRegistration oidcRegistration() {
        return ClientRegistration.withRegistrationId("oidc")
                .clientId("test-client")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("http://127.0.0.1:9/oauth/authorize")
                .tokenUri("http://127.0.0.1:9/oauth/token")
                .jwkSetUri("http://127.0.0.1:9/oauth/jwks")
                .userInfoUri("http://127.0.0.1:9/oauth/userinfo")
                .userNameAttributeName("sub")
                .clientName("oidc")
                .build();
    }
}
