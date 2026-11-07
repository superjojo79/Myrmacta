package be.myrmacta;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.FieldDefinition;
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
@AnalyzeClasses(packages = "be.myrmacta", importOptions = ImportOption.DoNotIncludeTests.class)
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
     * {@link be.myrmacta.domain.workflow.port.out.WorkflowStartConfirmationPort} ; cette règle
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
            .that().resideOutsideOfPackage("be.myrmacta.infrastructure.expression..")
            .should().dependOnClassesThat().resideInAPackage("com.dashjoin..");

    /**
     * TODO §75 (LC2) : le moteur d'expressions ne touche ni Flowable ni aucun évaluateur UEL — une expression métier n'est
     * jamais interprétée (ni concaténée) en JUEL ; la passerelle ne lit que la variable que le delegate a posée.
     */
    @ArchTest
    static final ArchRule the_expression_engine_never_touches_uel_or_flowable = noClasses()
            .that().resideInAPackage("be.myrmacta.infrastructure.expression..")
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
            "be.myrmacta.domain.dossier.DossierFieldRedactor",
            "be.myrmacta.domain.dossier.usecase.UpdateDossierFieldsUseCaseImpl",
            "be.myrmacta.domain.dossier.usecase.CheckDossierTypeSchemaCompatibilityUseCaseImpl",
            "be.myrmacta.infrastructure.persistence.dossier.DossierJpaEntity",
            "be.myrmacta.infrastructure.web.dossier.DossierResponse",
            "be.myrmacta.infrastructure.web.dossier.DossierExportController",
            "be.myrmacta.infrastructure.mcp.tool.DossierMcpView",
            // Les trois chemins de démarrage de workflow (API manuelle, relais outbox,
            // réconciliation RESERVED) injectent Dossier.fields() dans la variable de processus
            // réservée `dossierFields` (WorkflowVariables.withPlatformVariables) — comble l'écart
            // DossierAwareTask identifié dans docs/guides/GUIDE-CONCEPTION-BPMN.md §3. Appelant système
            // (AccessContext.System), même rationale que DossierFieldRedactor : la donnée brute
            // est nécessaire au moteur de workflow, jamais renvoyée telle quelle à un navigateur.
            "be.myrmacta.application.workflow.ManualStartOrchestrator",
            "be.myrmacta.infrastructure.flowable.OutboxRelayScheduler",
            "be.myrmacta.infrastructure.flowable.ReservedReconcileJob",
            // TODO §68 (portail-externe) : lit une seule clé — l'adresse e-mail du tiers désignée par
            // flowable:portalRecipient="dossierField:<nom>" — pour lui envoyer son lien. La valeur
            // n'est jamais renvoyée à un navigateur ni journalisée en clair (PortalEmails.mask).
            "be.myrmacta.infrastructure.flowable.PortalRecipientResolverAdapter",
            // TODO §102 (issue #184) : calcule les clés d'unicité d'un dossier (champs calculés recalculés) — les règles ne
            // lisent que des champs non sensibles, un champ `unique` n'est jamais sensible (FieldDefinition le refuse), et
            // aucune valeur n'est renvoyée à un navigateur ni à un journal.
            "be.myrmacta.domain.dossier.DossierUniqueKeys",
            // TODO §102 (lot B) : la brique « Rechercher des dossiers » lit la valeur d'un champ source déclaré et non
            // sensible du dossier courant (R8), la passe à la requête et ne la renvoie jamais (seuls un nombre et une
            // référence sortent).
            "be.myrmacta.domain.dossier.usecase.SearchSimilarDossiersUseCaseImpl",
            // TODO §113 (D-MAILROOM-ROUTE) : la brique « Router le courrier » copie, côté serveur, les champs NON sensibles du
            // courrier vers le dossier métier qu'elle crée (même nom), et prend l'objet comme libellé. Aucune valeur n'est
            // renvoyée à un navigateur ni journalisée.
            "be.myrmacta.infrastructure.flowable.RouteMailDelegate",
            // TODO §115 (D-DEADLINE-TIMER) : la brique « Calculer une échéance » lit un seul champ DATE non sensible désigné par
            // la brique et en tire des instants de minuteur ; la valeur n'est ni renvoyée à un navigateur ni journalisée.
            "be.myrmacta.infrastructure.flowable.ComputeDeadlineDelegate",
            // TODO §104 (issue #210) : lit le seul champ d'identité (sensible) d'une tâche à authentification forte pour le
            // comparer au NRN vérifié par FAS ou itsme ; la valeur n'est jamais renvoyée au tiers ni journalisée.
            "be.myrmacta.domain.workflow.portal.PortalIdentityFieldValue",
            // TODO §106 (issue #211, lot B) : la pré-vérification IA lit la présence des valeurs (complétude, sensibles
            // compris sans valeur) et la valeur des seuls champs désignés par la brique et non sensibles (P7, C3), qu'elle
            // envoie au modèle ; aucune valeur n'est renvoyée à un navigateur ni journalisée (seule une empreinte SHA-256
            // des entrées est rangée).
            "be.myrmacta.domain.precheck.PrecheckEngine");

    /** Same rationale as {@link #DOSSIER_FIELDS_READ_ALLOWLIST}, for {@code FieldDefinition.defaultValue()}. */
    private static final Set<String> FIELD_DEFINITION_DEFAULT_VALUE_READ_ALLOWLIST = Set.of(
            "be.myrmacta.domain.dossier.model.DossierFieldValidator",
            // D1 : le constructeur refuse un sous-champ de groupe portant une valeur par défaut (lecture pour rejet, jamais exposée).
            "be.myrmacta.domain.dossier.model.FieldDefinition");

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
            .and().doNotHaveFullyQualifiedName("be.myrmacta.domain.dossier.model.AccessContext")
            .should().callMethod(
                    be.myrmacta.domain.dossier.model.AccessContext.class, "external", java.util.UUID.class)
            .orShould().callConstructor(
                    be.myrmacta.domain.dossier.model.AccessContext.External.class, java.util.UUID.class)
            .because("AccessContext.External bypasses dossier ACLs and must only come from a resolved portal link");

    /**
     * TODO §122 : la persistance applicative passe par JPA seulement — une entité {@code *JpaEntity}, un dépôt
     * {@code SpringData*Repository} et un adaptateur {@code Jpa*Adapter} (requêtes JPQL ou natives au besoin, verrou
     * pessimiste par {@code @Lock}). Ni {@code JdbcTemplate}, ni {@code NamedParameterJdbcTemplate}, ni {@code JdbcClient}.
     *
     * <p>Ces adaptateurs écrits avant la règle y échappent encore ; ils sont à migrer, et cette liste ne doit que se
     * raccourcir : n'y ajouter aucune classe.
     */
    private static final Set<String> LEGACY_JDBC_ADAPTERS = Set.of(
            "be.myrmacta.infrastructure.persistence.deadline.JdbcDeadlineAlertClaims",
            "be.myrmacta.infrastructure.persistence.deadline.JdbcDeadlineCandidates",
            "be.myrmacta.infrastructure.persistence.dossier.JdbcDossierUniqueValueStore",
            "be.myrmacta.infrastructure.persistence.dossier.SequentialReferenceGenerator",
            "be.myrmacta.infrastructure.persistence.precheck.JdbcPrecheckRunRepository",
            "be.myrmacta.infrastructure.persistence.workflow.JdbcPortalIdentityRepository");

    @ArchTest
    static final ArchRule persistence_goes_through_jpa_only = noClasses()
            .that(notIn(LEGACY_JDBC_ADAPTERS))
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc.core..")
            .because("la persistance applicative est en JPA seulement (registre §122) ; ajouter une classe à "
                    + "ArchitectureRulesTest.LEGACY_JDBC_ADAPTERS est interdit, la liste ne fait que se raccourcir");

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
