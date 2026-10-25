package be.myrmacta;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.dossier.model.Dossier;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Proves that {@link ArchitectureRulesTest#only_allowlisted_classes_read_raw_dossier_fields}
 * actually catches a violation, rather than trivially passing because nothing happens to
 * violate it today. Imports a deliberately-violating fixture class directly (not via the
 * {@code @AnalyzeClasses(packages = "be.myrmacta")} sweep, which would need the fixture to
 * live under {@code src/main/java} to be reachable — here we build a small, explicit
 * {@link JavaClasses} set instead).
 */
class ArchitectureRulesFixtureTest {

    /** Deliberately outside {@code DOSSIER_FIELDS_READ_ALLOWLIST} — must trip the rule. */
    static final class NotOnTheAllowlist {
        Object leak(Dossier dossier) {
            return dossier.fields();
        }
    }

    @Test
    void rule_failsWhenAClassOutsideTheAllowlistReadsDossierFields() {
        JavaClasses classes = new ClassFileImporter().importClasses(NotOnTheAllowlist.class, Dossier.class);

        assertThatThrownBy(() ->
                        ArchitectureRulesTest.only_allowlisted_classes_read_raw_dossier_fields.check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("NotOnTheAllowlist");
    }

    /** Hors de {@code ..workflow.portal..} — ne doit pas pouvoir fabriquer un contexte externe (TODO §68). */
    static final class ForgesAnExternalContext {
        Object viaFactory() {
            return be.myrmacta.domain.dossier.model.AccessContext.external(java.util.UUID.randomUUID());
        }

        Object viaConstructor() {
            return new be.myrmacta.domain.dossier.model.AccessContext.External(java.util.UUID.randomUUID());
        }
    }

    @Test
    void rule_failsWhenCodeOutsideThePortalBuildsAnExternalAccessContext() {
        JavaClasses classes = new ClassFileImporter().importClasses(
                ForgesAnExternalContext.class,
                be.myrmacta.domain.dossier.model.AccessContext.class,
                be.myrmacta.domain.dossier.model.AccessContext.External.class);

        assertThatThrownBy(() ->
                        ArchitectureRulesTest.only_portal_code_builds_external_access_context.check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ForgesAnExternalContext")
                .hasMessageContaining("external")
                .hasMessageContaining("<init>");
    }
}
