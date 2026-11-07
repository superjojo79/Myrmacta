# §122 — Persistance applicative : JPA seulement

> **Statut :** clos · **Section :** [Décisions actées › Opérationnel](section-decisions-operationnel.md) · [registre](README.md)

**Contexte.** La persistance du projet est en JPA : 40 entités `*JpaEntity`, 40 dépôts Spring Data et leurs adaptateurs
`Jpa*Adapter` (décision n° 17). Dix adaptateurs écrits au fil des vagues passaient pourtant par `JdbcTemplate`, sans que
le registre ni les conventions ne le justifient, et le calendrier du tenant (§120, §121) en faisait partie. Cette
dualité obligeait à maintenir deux manières de lire, d'écrire, de verrouiller et de tester. Décision de l'utilisateur,
2026-11-07.

**D-PERSIST-JPA** — la persistance applicative passe par JPA seulement :

- une entité `*JpaEntity` (package-private), un dépôt `SpringData*Repository` et un adaptateur `Jpa*Adapter` qui mappe vers
  les modèles du domaine ;
- JPQL pour les requêtes ; `@Query(nativeQuery = true)` pour le SQL propre à PostgreSQL (`insert … on conflict`, opérateurs
  `jsonb`), toujours paramétré ; `@Lock(PESSIMISTIC_WRITE)` pour un verrou de ligne ; `@Modifying` pour les écritures
  groupées ;
- ni `JdbcTemplate`, ni `NamedParameterJdbcTemplate`, ni `JdbcClient`, ni JDBC brut dans le code applicatif.

**Contrôle.** La règle ArchUnit `persistence_goes_through_jpa_only` (`ArchitectureRulesTest`) interdit la dépendance à
`org.springframework.jdbc.core..` hors d'une liste nommée, `LEGACY_JDBC_ADAPTERS`. **Cette liste ne fait que se raccourcir :**
y ajouter une classe est interdit.

**Adaptateurs hérités, à migrer.** `JdbcDeadlineAlertClaims`, `JdbcDeadlineCandidates`, `JdbcDeadlineSettings`,
`JdbcDossierUniqueValueStore`, `SequentialReferenceGenerator`, `JdbcMailMessageIndex`, `JdbcReplyWorkflowSettings`,
`JdbcPrecheckRunRepository`, `JdbcPortalIdentityRepository`, `JdbcWorkflowLinkGroupRepository`. Chaque migration retire
sa ligne de la liste. Certaines demandent du SQL natif (`fields ->> champ` en `jsonb`, séquences, `on conflict`) : il se
fait par `@Query(nativeQuery = true)`.

**Migré par ce point.** Le calendrier du tenant : `JdbcTenantCalendar` devient `JpaTenantCalendarRepositoryAdapter`
(`TenantCalendarJpaEntity`, `TenantCalendarSourceJpaEntity`, `TenantCalendarEntryJpaEntity`). Le verrou du calendrier
(§121) est un `@Lock(PESSIMISTIC_WRITE)` sur la ligne de réglage ; les périodes s'insèrent par lots de 500 (taille de lot
de la session Hibernate, le temps de l'insertion). Aucun comportement visible ne change : les tests d'intégration du dépôt
(`JpaTenantCalendarRepositoryAdapterIT`), de l'API et de la promotion passent sans modification de leurs attentes.

**Hors périmètre.** Les accès JDBC qui ne sont pas de la persistance applicative : Liquibase, la création du schéma et de
la source de données (`DataSource`, Hikari), Flowable, le PostgreSQL embarqué des tests.

**Impacte.** `.project-skills/myrmacta-conventions/SKILL.md` (pile et anti-patterns), `AGENTS.md` (« Jamais »),
`ArchitectureRulesTest`, `infrastructure/persistence/deadline/` (calendrier), docs/decisions/121 (noms de classes).
