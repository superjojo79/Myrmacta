package com.flowforge;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.Dossier;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Proves that {@link ArchitectureRulesTest#only_allowlisted_classes_read_raw_dossier_fields}
 * actually catches a violation, rather than trivially passing because nothing happens to
 * violate it today. Imports a deliberately-violating fixture class directly (not via the
 * {@code @AnalyzeClasses(packages = "com.flowforge")} sweep, which would need the fixture to
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
}
