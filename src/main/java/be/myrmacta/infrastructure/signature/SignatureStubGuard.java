package be.myrmacta.infrastructure.signature;

import java.util.Set;

/**
 * Garde du stub (TODO §72, D-SIGNATURE-STUB-GUARD / D-SIGNATURE-GUARD-CRITERION, risque
 * R-SIM-EN-PROD). Liste <strong>positive</strong> : le stub démarre sans drapeau seulement sous les
 * profils {@code local} ou {@code test}. Dans tout autre cas — y compris aucun profil, donc la
 * production par défaut — il exige {@code app.signature.stub.allow-outside-local=true}, et démarre
 * alors avec un avertissement.
 */
public final class SignatureStubGuard {

    static final Set<String> SAFE_PROFILES = Set.of("local", "test");

    public enum Decision {
        ALLOWED_SAFE_PROFILE,
        ALLOWED_WITH_WARNING,
        REFUSED
    }

    private SignatureStubGuard() {}

    public static Decision evaluate(Set<String> activeProfiles, boolean allowOutsideLocal) {
        if (activeProfiles.stream().anyMatch(SAFE_PROFILES::contains)) {
            return Decision.ALLOWED_SAFE_PROFILE;
        }
        return allowOutsideLocal ? Decision.ALLOWED_WITH_WARNING : Decision.REFUSED;
    }

    public static String refusalMessage(Set<String> activeProfiles) {
        return "app.signature.provider=stub produces SIMULATED signatures without any legal value and is refused "
                + "outside the 'local' or 'test' profile (active profiles: "
                + (activeProfiles.isEmpty() ? "none" : String.join(",", activeProfiles))
                + "). Use a real provider, or set app.signature.stub.allow-outside-local=true to accept the risk "
                + "explicitly (demo environments only).";
    }
}
