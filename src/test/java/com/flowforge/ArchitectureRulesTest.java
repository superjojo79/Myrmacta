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
 * Règles de dépendance hexagonales — docs/specs/01-architecture-hexagonale.md.
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

    /**
     * TODO §46 : la couche {@code application} (orchestration transversale légère, hors ports)
     * n'était gardée par aucune règle ArchUnit — {@code ManualStartOrchestrator} dépendait
     * directement de {@code infrastructure.workflow.WorkflowStartConfirmer}. Corrigé en extrayant
     * {@link com.flowforge.domain.workflow.port.out.WorkflowStartConfirmationPort} ; cette règle
     * empêche toute régression future du même type.
     */
    @ArchTest
    static final ArchRule application_never_depends_on_infrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    /**
     * TODO §75 (LC2) : la bibliothèque JSONata n'est appelée que par l'adaptateur d'expressions — c'est lui qui porte les
     * bornes (délai, mémoire, regex, fonctions interdites). Un second appelant contournerait ces défenses.
     */
    @ArchTest
    static final ArchRule only_the_expression_adapter_uses_the_jsonata_library = noClasses()
            .that().resideOutsideOfPackage("com.flowforge.infrastructure.expression..")
            .should().dependOnClassesThat().resideInAPackage("com.dashjoin..");

    /**
     * TODO §75 (LC2) : le moteur d'expressions ne touche ni Flowable ni aucun évaluateur UEL — une expression métier n'est
     * jamais interprétée (ni concaténée) en JUEL ; la passerelle ne lit que la variable que le delegate a posée.
     */
    @ArchTest
    static final ArchRule the_expression_engine_never_touches_uel_or_flowable = noClasses()
            .that().resideInAPackage("com.flowforge.infrastructure.expression..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.flowable..", "jakarta.el..", "javax.el..", "org.apache.el..", "org.springframework.expression..");

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
            "com.flowforge.infrastructure.mcp.tool.DossierMcpView",
            // Les trois chemins de démarrage de workflow (API manuelle, relais outbox,
            // réconciliation RESERVED) injectent Dossier.fields() dans la variable de processus
            // réservée `dossierFields` (WorkflowVariables.withPlatformVariables) — comble l'écart
            // DossierAwareTask identifié dans docs/guides/GUIDE-CONCEPTION-BPMN.md §3. Appelant système
            // (AccessContext.System), même rationale que DossierFieldRedactor : la donnée brute
            // est nécessaire au moteur de workflow, jamais renvoyée telle quelle à un navigateur.
            "com.flowforge.application.workflow.ManualStartOrchestrator",
            "com.flowforge.infrastructure.flowable.OutboxRelayScheduler",
            "com.flowforge.infrastructure.flowable.ReservedReconcileJob",
            // TODO §68 (portail-externe) : lit une seule clé — l'adresse e-mail du tiers désignée par
            // flowable:portalRecipient="dossierField:<nom>" — pour lui envoyer son lien. La valeur
            // n'est jamais renvoyée à un navigateur ni journalisée en clair (PortalEmails.mask).
            "com.flowforge.infrastructure.flowable.PortalRecipientResolverAdapter",
            // TODO §102 (issue #184) : calcule les clés d'unicité d'un dossier (champs calculés recalculés) — les règles ne
            // lisent que des champs non sensibles, un champ `unique` n'est jamais sensible (FieldDefinition le refuse), et
            // aucune valeur n'est renvoyée à un navigateur ni à un journal.
            "com.flowforge.domain.dossier.DossierUniqueKeys",
            // TODO §102 (lot B) : la brique « Rechercher des dossiers » lit la valeur d'un champ source déclaré et non
            // sensible du dossier courant (R8), la passe à la requête et ne la renvoie jamais (seuls un nombre et une
            // référence sortent).
            "com.flowforge.domain.dossier.usecase.SearchSimilarDossiersUseCaseImpl");

    /** Same rationale as {@link #DOSSIER_FIELDS_READ_ALLOWLIST}, for {@code FieldDefinition.defaultValue()}. */
    private static final Set<String> FIELD_DEFINITION_DEFAULT_VALUE_READ_ALLOWLIST = Set.of(
            "com.flowforge.domain.dossier.model.DossierFieldValidator",
            // D1 : le constructeur refuse un sous-champ de groupe portant une valeur par défaut (lecture pour rejet, jamais exposée).
            "com.flowforge.domain.dossier.model.FieldDefinition");

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

    /**
     * TODO §68 D-PORTAL-ACTOR : {@code AccessContext.External} court-circuite l'ACL de
     * {@code AddDossierElementUseCaseImpl} (le lien portail a déjà été résolu). Seul le code du
     * portail, qui a validé ce lien, peut en fabriquer un — sinon n'importe quel appelant futur
     * pourrait contourner {@code DossierAccessPolicy} en construisant un contexte externe.
     */
    @ArchTest
    static final ArchRule only_portal_code_builds_external_access_context = noClasses()
            .that().resideOutsideOfPackage("..workflow.portal..")
            .and().doNotHaveFullyQualifiedName("com.flowforge.domain.dossier.model.AccessContext")
            .should().callMethod(
                    com.flowforge.domain.dossier.model.AccessContext.class, "external", java.util.UUID.class)
            .orShould().callConstructor(
                    com.flowforge.domain.dossier.model.AccessContext.External.class, java.util.UUID.class)
            .because("AccessContext.External bypasses dossier ACLs and must only come from a resolved portal link");

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
