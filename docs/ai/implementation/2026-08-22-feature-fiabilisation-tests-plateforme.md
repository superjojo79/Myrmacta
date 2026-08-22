---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: done
created: 2026-08-22
updated: 2026-08-22
feature: fiabilisation-tests-plateforme
design_ref: docs/ai/design/2026-08-22-feature-fiabilisation-tests-plateforme.md
planning_ref: docs/ai/planning/2026-08-22-feature-fiabilisation-tests-plateforme.md
---

# Implementation Guide — `fiabilisation-tests-plateforme`

15 tâches planifiées (T1.1–T5.4) exécutées en TDD. `./mvnw verify` final :
**1050 tests, 0 échec**, LINE 93.7% (seuil 85%), BRANCH 75.0% (seuil 75%).

## Development Setup

- **JDK 25** requis (`pom.xml` `<java.version>25</java.version>`) — absent
  au démarrage de ce bac à sable (seul JDK 21 préinstallé), installé via
  `apt-get install openjdk-25-jdk-headless`.
- **Docker** absent dans ce bac à sable (`docker info` échoue sur
  `/var/run/docker.sock`) — les 2 tests Testcontainers compilent et
  s'exécutent en `SKIPPED` propre (`Assumptions.assumeTrue`), pas en échec.
  CI (`ubuntu-latest`) a Docker natif.
- `org.testcontainers:testcontainers-junit-jupiter` /
  `testcontainers-postgresql` ajoutés à `pom.xml` — artefacts **renommés**
  en Testcontainers 2.x (préfixe `testcontainers-`, ex.
  `org.testcontainers:junit-jupiter` n'existe plus), version gérée par le
  BOM `spring-boot-dependencies` (`testcontainers.version=2.0.5`).

## Code Structure

- `src/test/java/com/flowforge/support/PostgresTestContainerSupport.java` —
  conteneur Postgres singleton **lazy** (pas de `static { start() }` :
  chargement de la classe ne doit jamais tenter Docker) + `isDockerAvailable()`
  à vérifier en premier via `Assumptions.assumeTrue` dans chaque test
  Testcontainers.
- `src/test/resources/processes/async-lock-dimensioning-test.bpmn20.xml` —
  process BPMN de test dédié (tâche async), jamais déployé en production
  (sous `src/test/resources`).
- `src/test/resources/db/changelog/failure-injection/` — 2 changelogs de
  test (`changelog-failure.yaml` cassé, `changelog-failure-fixed.yaml`
  corrigé, même `logicalFilePath` pour que Liquibase les traite comme un
  seul changelog logique lors de la reprise).

## Implementation Notes

### Core Features

- **Verrous Flowable sous charge réelle** (`FlowableLockDimensioningTest`) —
  exercice direct de `AcquireJobsCmd` (la commande que l'`AsyncExecutor` de
  Flowable exécute réellement en production) depuis N threads réels ;
  `flowable.async-executor-activate=false` pour un scénario contrôlé.
  `ResetExpiredJobsCmd` utilisé pour simuler un verrou expiré (pas une
  simulation ad hoc — le vrai mécanisme à 2 étapes de Flowable).
- **Réservation workflow concurrente réelle**
  (`ConcurrentWorkflowReservationTest`) — a révélé un bug réel (voir
  Design Deviations ci-dessous), corrigé avant que le test passe.
- **Testcontainers ciblé** — `TenantSchemaIsolationIT` (étanchéité
  `search_path`) et `SequentialReferenceMultiPodIT` (8 pods simulés,
  `TransactionTemplate` par pod pour répliquer fidèlement le
  `@Transactional` de production, sans quoi les 3 instructions SQL de
  `generate()` tourneraient en 3 auto-commits indépendants).
- **Migration Liquibase en échec partiel** (`LiquibasePartialFailureIT`) —
  portée mono-schéma (voir Design Deviations), H2 pur.
- **ArchUnit** — règle `usecase_impl_implements_port_in` ajoutée.
- **Cascade de résolution de templates**
  (`ResolveDocumentTemplateUseCaseImplTest`) — JPA réel (H2), pas de mock :
  la cascade est implémentée dans la requête native `findBestActive`
  (classement SQL), pas dans le use case.
- **§37** — `tenantId` explicite sur `findClosedForRetention`.
- **Seuils Jacoco** — voir section Coverage ci-dessous.

### Patterns & Best Practices

- **H2 vs Testcontainers, critère appliqué systématiquement** : Testcontainers
  seulement quand le mécanisme testé dépend réellement de la sémantique
  Postgres native (verrouillage ligne MVCC, `search_path`) ; H2 + vrais
  threads/transactions sinon (le mécanisme est du SQL portable — verrous
  Flowable, contrainte unique, Liquibase). Documenté décision par décision
  dans le design doc.
- **Deadlock à éviter** : `ExecutorService.invokeAll(tasks)` **bloque**
  jusqu'à ce que toutes les tâches terminent — si les tâches attendent un
  `CountDownLatch` compté après l'appel à `invokeAll`, c'est un deadlock
  garanti. Toujours `pool.submit(...)` (non-bloquant) pour obtenir les
  `Future` avant de déclencher le `CountDownLatch`.
- **`AcquireJobsCmd`** : l'exception d'optimistic locking (`FlowableOptimisticLockingException`)
  se produit au *flush* (fin de commande), pas pendant `execute()` — elle
  se propage donc en dehors de `managementService.executeCommand(...)`
  pour le thread perdant, plutôt que d'être absorbée en interne par
  Flowable. À capturer explicitement côté appelant.

## Design Deviations

1. **Bug réel trouvé et corrigé** (`ConcurrentWorkflowReservationTest`,
   hors périmètre initial mais nécessaire pour faire passer le test tel
   que spécifié dans le testing doc) : `JpaDossierWorkflowLinkRepositoryAdapter.save()`
   ne traduisait pas `DataIntegrityViolationException` (violation de
   `uq_dossier_workflow_link_active` par un `reserve()` concurrent perdant)
   en `WorkflowAlreadyRunningException`. Conséquence en production :
   `OutboxRelayScheduler.processOneInternal()` catch spécifiquement
   `WorkflowAlreadyRunningException` → `markDead` ; une
   `DataIntegrityViolationException` brute tombait dans le catch générique
   `RuntimeException` → `recordFailure` (retry), traitant à tort une
   collision de réservation comme un échec transitoire. Corrigé en
   suivant le pattern déjà établi par `DossierRelationJpaAdapter` (même
   traduction d'exception).
2. **Portée du test Liquibase revue** (D-LIQUIBASE-TEST, Phase 3) :
   `analysis doc/02-multi-tenant.md` montre que l'architecture réelle est
   « un schéma par instance, migré une fois au démarrage », pas un job
   partagé migrant plusieurs schémas — la prémisse « multi-schéma » du
   TODO §28 ne correspond à rien en production. Le test couvre l'échec
   partiel **mono-schéma** + reprise idempotente, le risque réel.
3. **Écart de couverture BRANCH découvert et comblé en 3 vagues** — non
   planifié dans le testing doc initial (M1–M5 ne listaient pas ces
   classes). `./mvnw verify` complet a montré BRANCH réel à 73.0%, sous
   le seuil D-COVERAGE (75%) décidé en Phase 1. Question posée à
   l'utilisateur (`AskUserQuestion`) plutôt que de baisser silencieusement
   le seuil ou de reporter la règle : réponse = combler par des tests
   ciblés. 8 classes jamais testées comblées (`M2mJwtAuthoritiesConverter`,
   `WebhookIdempotencyJpaEntity`/`WorkflowIdempotencyJpaEntity.IdempotencyId`
   — `equals`/`hashCode` de clés composites `@IdClass` jamais exercés,
   `StorageConfig` — beans S3 jamais instanciés par le contexte de test
   filesystem-only, `JpaWebhookIdempotencyStoreAdapter`,
   `UpdateDossierStatusDelegate`, `JpaDocumentTemplateRepositoryAdapter`
   — `listByTenantId`/`pageByTenantId`/`save` jamais exercés car
   `DocumentTemplateUseCasesTest` mocke entièrement le port). Progression :
   73.0% → 74.7% → 75.0%.

## Testcontainers — statut d'exécution honnête (D-TESTCONTAINERS-EXEC)

| Test | Écrit / compile | Exécuté et vert **dans ce bac à sable** | Exécuté et vert **en CI** |
|---|---|---|---|
| `TenantSchemaIsolationIT` | ✅ | ❌ — `SKIPPED` propre (Docker absent, `Assumptions.assumeTrue`) | À vérifier au premier passage CI (`ubuntu-latest`, Docker natif) |
| `SequentialReferenceMultiPodIT` | ✅ | ❌ — `SKIPPED` propre (idem) | À vérifier au premier passage CI |

Tous les **autres** tests de cette feature (H2, pas de dépendance Docker)
sont exécutés et verts dans ce bac à sable — confirmé par `./mvnw verify`
final (1050 tests, 0 échec). Aucune affirmation de « vert » n'est faite
pour les 2 tests ci-dessus au-delà de leur compilation et de leur SKIP
propre, conformément à D-TESTCONTAINERS-EXEC (Phase 1).

## Coverage

`pom.xml` Jacoco `BUNDLE` : `LINE` minimum relevé de 0.80 → **0.85** ;
nouvelle règle `BRANCH` minimum **0.75** (n'existait pas). État final
(`./mvnw verify`, 1050 tests) : LINE 93.7%, BRANCH 75.0% — les deux
seuils respectés avec une marge fine sur BRANCH (calculée exactement au
centième près lors de la 3ᵉ vague de comblement).

## Files Changed

**Production :**
- `pom.xml` — dépendances Testcontainers, seuils Jacoco
- `src/main/java/com/flowforge/domain/dossier/model/WorkflowAlreadyRunningException.java` — constructeur avec cause
- `src/main/java/com/flowforge/domain/dossier/port/out/DossierRepository.java` — §37
- `src/main/java/com/flowforge/domain/dossier/usecase/EvaluateDossierRetentionUseCaseImpl.java` — §37
- `src/main/java/com/flowforge/infrastructure/persistence/dossier/JpaDossierRepositoryAdapter.java` — §37
- `src/main/java/com/flowforge/infrastructure/persistence/dossier/SpringDataDossierRepository.java` — §37
- `src/main/java/com/flowforge/infrastructure/persistence/workflow/JpaDossierWorkflowLinkRepositoryAdapter.java` — traduction d'exception (Design Deviation 1)

**Tests (nouveaux, sauf mention) :**
- `src/test/java/com/flowforge/ArchitectureRulesTest.java` (modifié, +1 règle)
- `src/test/java/com/flowforge/infrastructure/flowable/FlowableLockDimensioningTest.java`
- `src/test/java/com/flowforge/domain/dossier/usecase/ConcurrentWorkflowReservationTest.java`
- `src/test/java/com/flowforge/support/PostgresTestContainerSupport.java`
- `src/test/java/com/flowforge/infrastructure/tenant/TenantSchemaIsolationIT.java`
- `src/test/java/com/flowforge/infrastructure/persistence/dossier/SequentialReferenceMultiPodIT.java`
- `src/test/java/com/flowforge/infrastructure/migration/LiquibasePartialFailureIT.java`
- `src/test/java/com/flowforge/domain/document/usecase/ResolveDocumentTemplateUseCaseImplTest.java`
- `src/test/java/com/flowforge/infrastructure/persistence/dossier/DossierPersistenceTest.java` (modifié, +1 test §37)
- `src/test/java/com/flowforge/domain/dossier/usecase/EvaluateDossierRetentionUseCaseImplTest.java` (modifié, signature §37)
- `src/test/java/com/flowforge/infrastructure/security/M2mJwtAuthoritiesConverterTest.java`
- `src/test/java/com/flowforge/infrastructure/persistence/connector/WebhookIdempotencyJpaEntityTest.java`
- `src/test/java/com/flowforge/infrastructure/config/StorageConfigTest.java`
- `src/test/java/com/flowforge/infrastructure/persistence/connector/JpaWebhookIdempotencyStoreAdapterTest.java`
- `src/test/java/com/flowforge/infrastructure/persistence/workflow/WorkflowIdempotencyJpaEntityTest.java`
- `src/test/java/com/flowforge/infrastructure/flowable/UpdateDossierStatusDelegateTest.java`
- `src/test/java/com/flowforge/infrastructure/persistence/document/JpaDocumentTemplateRepositoryAdapterTest.java`

**Ressources de test :**
- `src/test/resources/processes/async-lock-dimensioning-test.bpmn20.xml`
- `src/test/resources/db/changelog/failure-injection/changelog-failure.yaml`
- `src/test/resources/db/changelog/failure-injection/changelog-failure-fixed.yaml`

## Integration Points

Aucune API externe modifiée. `DossierRepository.findClosedForRetention`
(port `out`) change de signature — seul appelant interne
(`EvaluateDossierRetentionUseCaseImpl`), mis à jour atomiquement.

## Error Handling

Voir Design Deviation 1 — traduction d'exception de persistance en
exception domaine, cohérente avec le pattern déjà établi ailleurs dans le
codebase (`DossierRelationJpaAdapter`).

## Performance Considerations

Pas de changement de performance en production (seuls des tests
ajoutés/le seuil de couverture relevé, plus un `try/catch` sans coût
notable). Coût CI : ~2-5s de démarrage de conteneur Postgres (une seule
fois, singleton partagé) pour les 2 tests Testcontainers.

## Security Notes

Aucun changement de sécurité/autorisation. Le changelog de test
`failure-injection/` est isolé (répertoire dédié, jamais référencé par
`db.changelog-master.yaml`), sans risque d'interférer avec la migration
de production.
