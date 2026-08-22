package com.flowforge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

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
                    "org.springframework.boot.actuate..");

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
