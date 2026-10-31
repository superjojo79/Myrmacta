package be.myrmacta.support;

import be.myrmacta.domain.notification.port.out.EmailPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * No-op {@link EmailPort} — no test SMTP server (GreenMail) runs in this repo, so any full-context
 * test exercising a real send (e.g. the {@code smtp} connector via HTTP) would otherwise fail on a
 * real connection attempt. Overrides {@code SmtpEmailAdapter} for tests that need a deterministic
 * success path instead.
 */
@TestConfiguration
public class NoopEmailPortTestConfiguration {

    @Bean
    @Primary
    EmailPort emailPort() {
        return (to, subject, body, attachment) -> null;
    }
}
