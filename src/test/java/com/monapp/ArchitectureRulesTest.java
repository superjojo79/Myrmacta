package com.monapp;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Règles de dépendance hexagonales — analysis doc/01-architecture-hexagonale.md.
 *
 * {@code allowEmptyShould(true)} : le socle n'a pas encore de use cases ;
 * la convention sera appliquée dès qu'ils apparaissent.
 */
@AnalyzeClasses(packages = "com.monapp", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domain_has_no_framework_dependency = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.flowable..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_never_depends_on_infrastructure = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule usecase_impl_naming_convention = classes()
            .that().resideInAPackage("..usecase..")
            .and().areNotInterfaces()
            .should().haveSimpleNameEndingWith("Impl")
            .allowEmptyShould(true);
}
