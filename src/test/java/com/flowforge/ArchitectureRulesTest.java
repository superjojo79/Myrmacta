package com.flowforge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Règles de dépendance hexagonales — analysis doc/01-architecture-hexagonale.md.
 */
@AnalyzeClasses(packages = "com.flowforge", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domain_has_no_framework_dependency = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.flowable..",
                    "org.springdoc..",
                    "io.swagger..",
                    "io.micrometer..",
                    "org.slf4j..",
                    "org.springframework.boot.actuate..",
                    // serveur-mcp T8.1 : org.springframework.ai.. déjà couvert par
                    // "org.springframework.." ci-dessus ; io.modelcontextprotocol.. (SDK MCP,
                    // groupId différent) ajouté explicitement pour la même garantie.
                    "io.modelcontextprotocol..");

    @ArchTest
    static final ArchRule domain_never_depends_on_infrastructure = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule usecase_impl_naming_convention = classes()
            .that().resideInAPackage("..usecase..")
            .and().areTopLevelClasses()
            .and().areNotInterfaces()
            .should().haveSimpleNameEndingWith("Impl");

    @ArchTest
    static final ArchRule usecase_impl_implements_port_in = classes()
            .that().resideInAPackage("..usecase..")
            .and().areTopLevelClasses()
            .and().areNotInterfaces()
            .and().haveSimpleNameEndingWith("Impl")
            .should(implementAnInterfaceFromPortIn());

    /**
     * D-LOG-GUARD (masquage-donnees-sensibles-logs-webhooks): {@code Dossier.fields()} exposes
     * raw field values verbatim, which may include values of fields marked
     * {@link FieldDefinition#isSensitive()} — {@code Dossier} itself cannot filter them (it does
     * not carry {@code FieldDefinition}s), so access is restricted to an explicit allowlist of
     * classes that are known to handle the raw map responsibly (redaction, persistence,
     * validated API responses) rather than left open to any future caller (e.g. a stray
     * {@code log.info}). ArchUnit cannot detect "was this value logged" (no call-site dataflow
     * analysis over erased/widened parameter types) — restricting the read itself is the
     * property that is actually statically verifiable.
     */
    private static final Set<String> DOSSIER_FIELDS_READ_ALLOWLIST = Set.of(
            "com.flowforge.domain.dossier.DossierFieldRedactor",
            "com.flowforge.domain.dossier.usecase.UpdateDossierFieldsUseCaseImpl",
            "com.flowforge.domain.dossier.usecase.CheckDossierTypeSchemaCompatibilityUseCaseImpl",
            "com.flowforge.infrastructure.persistence.dossier.DossierJpaEntity",
            "com.flowforge.infrastructure.web.dossier.DossierResponse",
            "com.flowforge.infrastructure.web.dossier.DossierExportController",
            "com.flowforge.infrastructure.mcp.tool.DossierMcpView");

    /** Same rationale as {@link #DOSSIER_FIELDS_READ_ALLOWLIST}, for {@code FieldDefinition.defaultValue()}. */
    private static final Set<String> FIELD_DEFINITION_DEFAULT_VALUE_READ_ALLOWLIST = Set.of(
            "com.flowforge.domain.dossier.model.DossierFieldValidator");

    @ArchTest
    static final ArchRule only_allowlisted_classes_read_raw_dossier_fields = noClasses()
            .that(notIn(DOSSIER_FIELDS_READ_ALLOWLIST))
            .should().callMethod(Dossier.class, "fields")
            .because("Dossier.fields() may expose sensitive values — extend "
                    + "ArchitectureRulesTest.DOSSIER_FIELDS_READ_ALLOWLIST deliberately rather than "
                    + "letting a new, unreviewed call site appear");

    @ArchTest
    static final ArchRule only_allowlisted_classes_read_field_definition_default_value = noClasses()
            .that(notIn(FIELD_DEFINITION_DEFAULT_VALUE_READ_ALLOWLIST))
            .should().callMethod(FieldDefinition.class, "defaultValue")
            .because("FieldDefinition.defaultValue() may carry a sensitive prefilled value — extend "
                    + "ArchitectureRulesTest.FIELD_DEFINITION_DEFAULT_VALUE_READ_ALLOWLIST deliberately "
                    + "rather than letting a new, unreviewed call site appear");

    private static DescribedPredicate<JavaClass> notIn(Set<String> allowlist) {
        return new DescribedPredicate<>("not in the allowlist " + allowlist) {
            @Override
            public boolean test(JavaClass javaClass) {
                return !allowlist.contains(javaClass.getFullName());
            }
        };
    }

    private static ArchCondition<JavaClass> implementAnInterfaceFromPortIn() {
        return new ArchCondition<>("implement an interface residing in a ..port.in.. package") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean implementsPortIn = javaClass.getInterfaces().stream()
                        .anyMatch(itf -> itf.toErasure().getPackageName().contains(".port.in"));
                if (!implementsPortIn) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getFullName() + " does not implement any interface from a ..port.in.. package"));
                }
            }
        };
    }
}
