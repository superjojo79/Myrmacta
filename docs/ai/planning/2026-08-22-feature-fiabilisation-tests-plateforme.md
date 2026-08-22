---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: done
created: 2026-08-22
updated: 2026-08-22
feature: fiabilisation-tests-plateforme
design_ref: docs/ai/design/2026-08-22-feature-fiabilisation-tests-plateforme.md
testing_ref: docs/ai/testing/2026-08-22-feature-fiabilisation-tests-plateforme.md
---

# Project Planning & Task Breakdown — `fiabilisation-tests-plateforme`

## Milestones

- [x] M1: Fondations — dépendance Testcontainers + `PostgresTestContainerSupport` + règle ArchUnit (rapide, débloque les tests Postgres)
- [x] M2: Tests H2 + threads réels (verrous Flowable, réservation workflow concurrente)
- [x] M3: Tests Testcontainers Postgres (isolation `search_path`, séquence multi-pods)
- [x] M4: Liquibase échec partiel + cascade templates + §37
- [x] M5: Seuils Jacoco + vérification globale + mise à jour docs (TODO.md, FILE-ATTENTE.md)
- [x] M6 (ajouté en cours de route) : comblement de l'écart de couverture BRANCH (73.0% → 75.0%), voir « Scope Changes » plus bas

## Task Breakdown

### M1: Fondations

- [x] T1.1 — Ajouter `org.testcontainers:postgresql` + `org.testcontainers:junit-jupiter` (scope `test`) dans `pom.xml`, BOM Testcontainers si absent
  - Dépend de : rien
  - Validation : `./mvnw dependency:tree` résout sans conflit, `./mvnw compile` toujours vert
- [x] T1.2 — `PostgresTestContainerSupport` (`src/test/java/com/flowforge/support/`) : conteneur Postgres singleton statique + nettoyage de schéma entre tests (D-CONTAINER-REUSE)
  - Dépend de : T1.1
  - Validation : compile ; usage réel vérifié par T3.1/T3.2 (non exécuté ici faute de Docker — voir testing doc)
- [x] T1.3 — `ArchitectureRulesTest.usecase_impl_implements_port_in` (`..usecase..*Impl` → implémente une interface de `..port.in..`)
  - Dépend de : rien
  - Validation : `./mvnw test -Dtest=ArchitectureRulesTest` vert sur le code existant (couvre le success criterion « règle ArchUnit »)

### M2: Tests H2 + threads réels

- [x] T2.1 — `FlowableLockDimensioningTest` (D-FLOWABLE-LOCK-TEST) : N threads réels, contention sur acquisition de job Flowable, assertions sur `lockOwner`
  - Dépend de : rien (H2 existant)
  - Validation : test exécuté et vert dans ce bac à sable ; couvre scénario testing doc « verrous Flowable sous charge »
- [x] T2.2 — `ConcurrentWorkflowReservationTest` (D-PARALLELISM-TEST) : N threads réels sur `AttachWorkflowUseCaseImpl.reserve()`, une seule réservation gagne, `WorkflowAlreadyRunningException` pour les autres
  - Dépend de : rien
  - Validation : test exécuté et vert ; couvre « un seul workflow actif » sous parallélisme réel

### M3: Tests Testcontainers Postgres

- [x] T3.1 — `TenantSchemaIsolationIT` : 2 schémas tenants réels, `search_path` par connexion, preuve d'étanchéité
  - Dépend de : T1.1, T1.2
  - Validation : écrit et compile ; **non exécuté** dans ce bac à sable (Docker absent, D-TESTCONTAINERS-EXEC) — à vérifier en CI
- [x] T3.2 — `SequentialReferenceMultiPodIT` : N threads simulant N pods sur `SequentialReferenceGenerator`, absence de doublon/trou
  - Dépend de : T1.1, T1.2
  - Validation : écrit et compile ; **non exécuté** ici, même contrainte que T3.1

### M4: Liquibase, cascade templates, §37

- [x] T4.1 — Changelog de test dédié avec changeset en échec délibéré (D-LIQUIBASE-TEST)
  - Dépend de : rien
  - Validation : changelog isolé, n'affecte pas le changelog principal `db.changelog-master.yaml`
- [x] T4.2 — `LiquibasePartialFailureIT` : échec partiel mono-schéma + reprise idempotente au redémarrage
  - Dépend de : T4.1
  - Validation : test exécuté et vert dans ce bac à sable (H2, pas de dépendance Docker)
- [x] T4.3 — `ResolveDocumentTemplateUseCaseImplTest` : cascade complète (exact → fallback locale → fallback variant → défaut → `TemplateNotFoundException`)
  - Dépend de : rien
  - Validation : test exécuté et vert
- [x] T4.4 — §37 : `SpringDataDossierRepository.findClosedForRetention` + `DossierRepository` (port) + `JpaDossierRepositoryAdapter` — paramètre `tenantId` explicite
  - Dépend de : rien
  - Validation : test de non-régression sur `EvaluateDossierRetentionUseCaseImpl` + nouveau test d'isolation multi-tenant, tous verts

### M5: Seuils, vérification globale, docs

- [x] T5.1 — Relever seuils Jacoco (`pom.xml`) : LINE 85%, ajout règle BRANCH 75%, niveau BUNDLE
  - Dépend de : T2.1, T2.2, T4.2, T4.3, T4.4 (la couverture doit déjà avoir grimpé avant de resserrer le seuil, sinon échec immédiat)
  - Validation : `./mvnw verify` vert avec les nouveaux seuils
- [x] T5.2 — `./mvnw verify` complet (suite existante + nouveaux tests, hors les 2 IT Testcontainers non exécutables ici)
  - Dépend de : tout ce qui précède
  - Validation : 0 régression sur les ~986 tests existants
- [x] T5.3 — Mise à jour `TODO.md` §26 (narrower : 2 points déjà résolus documentés, 1 point clos par T3.2), §28 (clos), §37 (clos) ; `FILE-ATTENTE.md` daté
  - Dépend de : T5.2
  - Validation : revue manuelle, cohérence avec l'état réel du code après implémentation
- [x] T5.4 — Implementation doc : statut d'exécution honnête des 2 tests Testcontainers (écrit/compilé vs exécuté/vert), conformément à D-TESTCONTAINERS-EXEC
  - Dépend de : T3.1, T3.2
  - Validation : aucune affirmation de « vert » non prouvée dans ce bac à sable

## Dependencies

- **Docker absent dans ce bac à sable** : bloque l'exécution réelle de T3.1/T3.2 ici (pas leur écriture/compilation) — dépendance externe (CI `ubuntu-latest`), pas un blocage du travail lui-même.
- T5.1 (seuils Jacoco) dépend de l'ensemble des nouveaux tests M2/M4 pour ne pas faire échouer `verify` en resserrant le seuil avant que la couverture ait effectivement augmenté.
- Aucune dépendance externe hors Testcontainers (nouvelle dépendance Maven, pas de service externe).

## Timeline & Estimates

Feature de fiabilisation technique, pas de contrainte de délai externe
communiquée par l'utilisateur — séquencement par risque/dépendance
(M1 → M2/M4 en parallèle logique → M3 → M5), pas d'estimation en jours
(hors périmètre demandé pour cette feature).

## Risks & Mitigation

- **Risque** : les tests H2 + threads réels (T2.1, T2.2) peuvent être
  flaky si l'orchestration des threads (départ simultané, attente de
  fin) est mal maîtrisée. **Réalisé partiellement, corrigé avant livraison** :
  `ExecutorService.invokeAll(tasks)` bloque jusqu'à ce que toutes les
  tâches terminent — un vrai deadlock s'est produit dans les deux premiers
  jets (les tâches attendaient un `CountDownLatch` compté *après* l'appel
  à `invokeAll`, qui ne revient jamais). Corrigé en remplaçant par
  `pool.submit(...)` (non bloquant) suivi du `countDown()`. Les deux tests
  sont ensuite passés du premier coup à chaque run ultérieur.
- **Risque** : resserrer les seuils Jacoco (T5.1) avant que la
  couverture ait réellement augmenté ferait échouer `verify`.
  **Réalisé, largement sous-estimé** : la couverture BRANCH réelle après
  M1-M4 était 73.0% (pas les ~74% estimés en Phase 1 — erreur de lecture
  des colonnes `jacoco.csv` lors du diagnostic initial), sous le seuil
  75%. Question posée à l'utilisateur plutôt que d'ajuster
  silencieusement ; réponse = combler par des tests ciblés. 3 vagues
  successives (8 classes jamais testées) pour atteindre 75.0% exactement.
- **Risque** : les 2 tests Testcontainers ne peuvent pas être prouvés
  verts dans ce bac à sable — risque de fausse impression de complétude.
  **Mitigation appliquée** : D-TESTCONTAINERS-EXEC + T5.4, étiquetage
  explicite partout (planning, testing, implementation docs) ; en plus du
  compile, `Assumptions.assumeTrue(isDockerAvailable())` garantit un
  `SKIPPED` propre plutôt qu'un blocage — vérifié : `./mvnw verify`
  complet ne bloque ni n'échoue sur ces 2 classes.
- **Risque** : le changelog de test Liquibase (T4.1) pourrait
  accidentellement interférer avec le changelog principal si mal
  isolé. **Mitigation appliquée** : changelog dans un répertoire dédié
  (`failure-injection/`), jamais référencé depuis
  `db.changelog-master.yaml`, chargé uniquement par le test lui-même.

## Resources Needed

- Aucune ressource externe/humaine supplémentaire — travail solo IA sur
  la branche `claude/queue-next-feature-8r054i`.
- Dépendance Maven nouvelle : `org.testcontainers:testcontainers-junit-jupiter`,
  `org.testcontainers:testcontainers-postgresql` (noms réels — les
  artefacts non préfixés `testcontainers-` cités en Phase 4 n'existent
  plus en Testcontainers 2.x).
- CI GitHub Actions (`ubuntu-latest`, Docker natif) comme seul
  environnement capable de prouver le vert des tests Testcontainers —
  aucune modification du pipeline dans ce lot (non-goal acté).
- JDK 25 absent du bac à sable au démarrage (requis par `pom.xml`),
  installé via `apt-get` — pas anticipé en planning.

## Scope Changes

M6 ajouté en cours de Phase 5, hors des 5 milestones planifiés :
comblement de l'écart de couverture BRANCH (73.0% → 74.7% → 75.0%,
8 classes jamais testées : `M2mJwtAuthoritiesConverter`,
`WebhookIdempotencyJpaEntity`/`WorkflowIdempotencyJpaEntity.IdempotencyId`,
`StorageConfig`, `JpaWebhookIdempotencyStoreAdapter`,
`UpdateDossierStatusDelegate`, `JpaDocumentTemplateRepositoryAdapter`).
Question posée à l'utilisateur avant d'élargir le périmètre plutôt que
décidé unilatéralement (baisser le seuil, ou reporter la règle BRANCH,
étaient les alternatives proposées).

## Summary

Les 5 milestones planifiés (M1-M5, 15 tâches) sont livrés, plus un M6 non
planifié (comblement de couverture, actée avec l'utilisateur). État
final : `./mvnw verify` — 1050 tests, 0 échec, LINE 93.7% (seuil 85%),
BRANCH 75.0% (seuil 75%). Un bug réel de production trouvé et corrigé en
cours de TDD (`DataIntegrityViolationException` brute au lieu de
`WorkflowAlreadyRunningException`, traitée à tort comme un échec
transitoire par `OutboxRelayScheduler`). Les 2 tests Testcontainers sont
écrits, compilent, et `SKIPPED` proprement dans ce bac à sable (Docker
absent) — restent à confirmer verts au premier passage CI. TODO.md
§26/§28/§37 clos ; FILE-ATTENTE.md daté. Aucune tâche restante côté
planning ; prochaine étape : Check Implementation (Phase 7), puis
dev-testing (Phase 8, déjà largement couvert car cette feature *est* la
stratégie de test) et dev-review (Phase 9) avant ouverture de PR.
