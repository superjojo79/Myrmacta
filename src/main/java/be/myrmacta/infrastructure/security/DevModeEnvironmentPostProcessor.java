package be.myrmacta.infrastructure.security;

import java.util.Arrays;
import java.util.Locale;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * TODO §95 (P1) : {@code app.environment} (APP_ENVIRONMENT) est le seul interrupteur « mode dev ».
 *
 * <p>Les profils {@code local} et {@code test} relâchent les contrôles de {@link SecretsEnvironmentPostProcessor}
 * et de {@link SecurityHeadersEnvironmentPostProcessor} (et coupent le flag {@code Secure} des cookies pour
 * {@code local}). Un {@code .env} de dev recopié en production ne doit pas pouvoir les activer en silence :
 * <ul>
 *   <li>profil {@code local} : refusé si {@code app.environment} n'est pas {@code local} ;</li>
 *   <li>profil {@code test} : refusé si {@code app.environment} vaut {@code production} (la suite de tests
 *       exerce aussi {@code staging}) ;</li>
 *   <li>antivirus {@code noop} : refusé en {@code production} sans acquittement explicite
 *       ({@code app.dossier.attachment.scan.noop-acknowledged=true}), signalé en {@code staging}.</li>
 * </ul>
 * {@code app.environment} absent vaut {@code production}, comme pour la gouvernance MCP.
 */
public class DevModeEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private final Log log;

    public DevModeEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DevModeEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String appEnvironment = environment.getProperty("app.environment", "production").strip().toLowerCase(Locale.ROOT);
        if (hasProfile(environment, "local") && !"local".equals(appEnvironment)) {
            throw new IllegalStateException("SPRING_PROFILES_ACTIVE=local requires APP_ENVIRONMENT=local (got '"
                    + appEnvironment + "'): the local profile relaxes secrets and cookie checks");
        }
        if (hasProfile(environment, "test") && "production".equals(appEnvironment)) {
            throw new IllegalStateException("SPRING_PROFILES_ACTIVE=test is refused when APP_ENVIRONMENT=production: "
                    + "the test profile relaxes secrets checks");
        }
        checkAntivirus(environment, appEnvironment);
    }

    private void checkAntivirus(ConfigurableEnvironment environment, String appEnvironment) {
        String scanType = environment.getProperty("app.dossier.attachment.scan.type", "noop").strip();
        if (!scanType.isEmpty() && !"noop".equalsIgnoreCase(scanType)) {
            return;
        }
        boolean acknowledged = environment.getProperty("app.dossier.attachment.scan.noop-acknowledged", Boolean.class, false);
        if ("production".equals(appEnvironment) && !acknowledged) {
            throw new IllegalStateException("APP_DOSSIER_ATTACHMENT_SCAN_TYPE=noop (no antivirus) is refused when "
                    + "APP_ENVIRONMENT=production: configure clamav, or set "
                    + "APP_DOSSIER_ATTACHMENT_SCAN_NOOP_ACKNOWLEDGED=true to accept unscanned attachments");
        }
        if (!"local".equals(appEnvironment)) {
            log.warn("Attachment antivirus disabled (APP_DOSSIER_ATTACHMENT_SCAN_TYPE=noop) with APP_ENVIRONMENT="
                    + appEnvironment + ": uploaded attachments are not scanned");
        }
    }

    private static boolean hasProfile(ConfigurableEnvironment environment, String profile) {
        return Arrays.stream(environment.getActiveProfiles())
                .map(active -> active.toLowerCase(Locale.ROOT))
                .anyMatch(profile::equals);
    }
}
