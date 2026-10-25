package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** TODO §82 (D6) — garde-fous de démarrage : environnement, publication directe, allowlist M2M (A3-M2). */
class McpGovernanceStartupGuardTest {

    private static final McpPublicationProperties DRAFT = new McpPublicationProperties(McpPublicationProperties.Mode.DRAFT);
    private static final McpPublicationProperties DIRECT = new McpPublicationProperties(McpPublicationProperties.Mode.DIRECT);
    private static final M2mSecurityProperties NO_M2M = new M2mSecurityProperties("", "", List.of());
    private static final M2mSecurityProperties OPEN_M2M = new M2mSecurityProperties("https://idp", "", List.of());
    private static final M2mSecurityProperties SECRET_M2M = new M2mSecurityProperties("", "s3cret", List.of());
    private static final M2mSecurityProperties LISTED_M2M = new M2mSecurityProperties("https://idp", "", List.of("agent-1"));

    private static void check(String env, McpPublicationProperties p, M2mSecurityProperties m) {
        new McpGovernanceStartupGuard(env, p, m).afterPropertiesSet();
    }

    @Test
    void anUnknownEnvironmentFailsFastInsteadOfSilentlyRelaxingAProtection() {
        assertThatThrownBy(() -> check("prod", DRAFT, NO_M2M)).hasMessageContaining("app.environment");
        assertThatThrownBy(() -> check("", DRAFT, NO_M2M)).hasMessageContaining("app.environment");
        assertThat(new McpGovernanceStartupGuard(null, DRAFT, NO_M2M).environment()).isEqualTo("production");
        assertThatCode(() -> check(" Staging ", DRAFT, NO_M2M)).doesNotThrowAnyException();
    }

    @Test
    void directPublicationIsRefusedInProductionOnly() {
        assertThatThrownBy(() -> check("production", DIRECT, NO_M2M)).hasMessageContaining("direct").hasMessageContaining("production");
        assertThatCode(() -> check("staging", DIRECT, NO_M2M)).doesNotThrowAnyException();
        assertThatCode(() -> check("local", DIRECT, NO_M2M)).doesNotThrowAnyException();
        assertThatCode(() -> check("production", DRAFT, NO_M2M)).doesNotThrowAnyException();
    }

    @Test
    void outsideLocalAnActiveM2mChainNeedsAnAllowlist() {
        assertThatThrownBy(() -> check("production", DRAFT, OPEN_M2M)).hasMessageContaining("trusted-client-ids");
        assertThatThrownBy(() -> check("staging", DRAFT, SECRET_M2M)).hasMessageContaining("trusted-client-ids");
        assertThatCode(() -> check("production", DRAFT, LISTED_M2M)).doesNotThrowAnyException();
        assertThatCode(() -> check("local", DRAFT, OPEN_M2M)).doesNotThrowAnyException();
        // chaîne M2M inactive : rien à protéger
        assertThatCode(() -> check("production", DRAFT, NO_M2M)).doesNotThrowAnyException();
    }

    @Test
    void retentionBoundsAreEnforced() {
        assertThatThrownBy(() -> new DraftRetentionProperties(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DraftRetentionProperties(4000)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DraftRetentionProperties(0).retentionDays()).isZero();
    }
}
