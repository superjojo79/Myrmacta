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

**Exceptions assumées.** `JdbcDeadlineAlertClaims` et `JdbcDeadlineCandidates` restent en `JdbcTemplate` : leur SQL est
natif de bout en bout (réservation concurrente des alertes, sélection des échéances candidates) et une entité n'y apporterait
rien. Ce sont les deux seules entrées de `LEGACY_JDBC_ADAPTERS` ; la liste ne grandit pas, et elle ne se raccourcit que si
l'une de ces deux classes est un jour migrée.

**Migré par ce point.** Les dix autres adaptateurs, tous passés en JPA (entité `*JpaEntity`, dépôt Spring Data, adaptateur
`Jpa*`), sans changement de comportement visible :
- le calendrier du tenant (`JdbcTenantCalendar` → `JpaTenantCalendarRepositoryAdapter`) ; le verrou du calendrier (§121)
  est un `@Lock(PESSIMISTIC_WRITE)` sur la ligne de réglage ; les périodes s'insèrent par lots de 500 ;
- les réglages d'échéance, de réponse et les groupes de liens de workflow ;
- l'index des e-mails (`recordUnlessShadowing` en `insert … on conflict do nothing` natif) ;
- les identités du portail (usage unique d'une tentative = `update` conditionnel, nombre de lignes modifiées) ;
- le compteur de références (`SequentialReferenceGenerator` : un `insert … on conflict do update … returning` natif, atomique)
  et les valeurs uniques des dossiers (verrous consultatifs par requête native, `on conflict do nothing`) ;
- les exécutions de pré-vérification (`JpaPrecheckRunRepositoryAdapter` ; la file `for update skip locked` est une requête
  native ; l'unicité d'une relance par dossier passe par un verrou consultatif plutôt que par une violation de clé, qui
  avorterait la transaction).

Les tests d'intégration existants passent sans modification de leurs attentes ; ceux des adaptateurs qui n'en avaient pas
ont reçu des tests de caractérisation. Une différence de fond : l'enregistrement des réglages (échéance, réponse, groupes
de liens) est un « chercher puis enregistrer » : deux premières écritures simultanées peuvent désormais entrer en conflit.

**Hors périmètre.** Les accès JDBC qui ne sont pas de la persistance applicative : Liquibase, la création du schéma et de
la source de données (`DataSource`, Hikari), Flowable, le PostgreSQL embarqué des tests.

**Impacte.** `.project-skills/myrmacta-conventions/SKILL.md` (pile et anti-patterns), `AGENTS.md` (« Jamais »),
`ArchitectureRulesTest`, `infrastructure/persistence/deadline/` (calendrier), docs/decisions/121 (noms de classes).
