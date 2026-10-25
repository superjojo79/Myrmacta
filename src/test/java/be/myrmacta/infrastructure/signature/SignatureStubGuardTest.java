package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.infrastructure.signature.SignatureStubGuard.Decision;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** S-STUB-4, S-STUB-5, S-STUB-6 : liste positive de profils (D-SIGNATURE-GUARD-CRITERION). */
class SignatureStubGuardTest {

    @Test
    void safeProfilesNeedNoFlag() {
        assertThat(SignatureStubGuard.evaluate(Set.of("local"), false)).isEqualTo(Decision.ALLOWED_SAFE_PROFILE);
        assertThat(SignatureStubGuard.evaluate(Set.of("test"), false)).isEqualTo(Decision.ALLOWED_SAFE_PROFILE);
        assertThat(SignatureStubGuard.evaluate(Set.of("prod", "local"), false))
                .isEqualTo(Decision.ALLOWED_SAFE_PROFILE);
    }

    @Test
    void anythingElseIsRefusedWithoutTheFlag_includingNoProfileAtAll() {
        assertThat(SignatureStubGuard.evaluate(Set.of(), false)).isEqualTo(Decision.REFUSED);
        assertThat(SignatureStubGuard.evaluate(Set.of("prod"), false)).isEqualTo(Decision.REFUSED);
        assertThat(SignatureStubGuard.evaluate(Set.of("staging", "cloud"), false)).isEqualTo(Decision.REFUSED);
    }

    @Test
    void theExplicitFlagAllowsOutsideLocalWithAWarning() {
        assertThat(SignatureStubGuard.evaluate(Set.of(), true)).isEqualTo(Decision.ALLOWED_WITH_WARNING);
        assertThat(SignatureStubGuard.evaluate(Set.of("prod"), true)).isEqualTo(Decision.ALLOWED_WITH_WARNING);
    }

    @Test
    void refusalMessageNamesTheFlagAndTheProfiles() {
        assertThat(SignatureStubGuard.refusalMessage(Set.of()))
                .contains("allow-outside-local", "none", "SIMULATED");
        assertThat(SignatureStubGuard.refusalMessage(Set.of("prod"))).contains("prod");
    }
}
