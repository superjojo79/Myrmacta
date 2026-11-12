package be.myrmacta.support;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** TODO §132 : le garde de test à DNS figé suit la même règle que {@code NotificationConfig}. */
class StubPublicDnsLoopbackTest {

    private static OutboundUrlGuard guard(String appEnvironment, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.environment", appEnvironment);
        environment.setActiveProfiles(profiles);
        return new StubPublicDnsTestConfiguration().stubPublicDnsOutboundUrlGuard(environment);
    }

    @Test
    void testProfile_withStaging_refusesLoopback() {
        assertThat(guard("staging", "test").isLocalLoopback("localhost")).isFalse();
        assertThat(guard("staging", "test").isLocalLoopback("[::1]")).isFalse();
    }

    @Test
    void testProfile_withLocalEnvironment_allowsLoopbackIncludingIpv6() {
        OutboundUrlGuard guard = guard("local", "test");
        assertThat(guard.isLocalLoopback("localhost")).isTrue();
        assertThat(guard.isLocalLoopback("::1")).isTrue();
        assertThat(guard.isLocalLoopback("[::1]")).isTrue();
    }

    @Test
    void emptyEnvironment_paddedProfile_andNonLocalValues_followTheSameRule() {
        assertThat(guard("", "test").isLocalLoopback("localhost")).isFalse();
        assertThat(guard("   ", "test").isLocalLoopback("localhost")).isFalse();
        assertThat(guard("production", "test").isLocalLoopback("localhost")).isFalse();
        assertThat(guard("ci", "test").isLocalLoopback("localhost")).isFalse();
        assertThat(guard("local", "prod").isLocalLoopback("localhost")).isFalse();
        assertThat(guard("local", " test ").isLocalLoopback("localhost")).isTrue();
        assertThat(guard("local", " LOCAL ").isLocalLoopback("[::1]")).isTrue();
    }
}
