---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: done
created: 2026-08-22
updated: 2026-08-22
feature: fiabilisation-tests-plateforme
design_ref: docs/ai/design/2026-08-22-feature-fiabilisation-tests-plateforme.md
---

# Testing Strategy — `fiabilisation-tests-plateforme`

Cette feature *est* une feature de tests : il n'y a pas de couche
applicative supplémentaire à tester séparément — chaque item ci-dessous
est le livrable lui-même, pas un test d'un livrable distinct. Dérivé du
design (`Component Breakdown`, `Requirements Coverage`).

## Test Coverage Goals

- Objectif de couverture global : Jacoco `BUNDLE` LINE ≥ 85%, BRANCH ≥
  75% (D-COVERAGE, requirements Phase 1+2) — `./mvnw verify` doit rester
  vert après ajout de tous les tests listés ici.
- Chaque nouveau test doit être **exécuté et vert dans ce bac à sable**
  sauf les 2 tests Testcontainers (`TenantSchemaIsolationIT`,
  `SequentialReferenceMultiPodIT`), pour lesquels Docker est absent —
  statut d'exécution réel documenté explicitement plus bas
  (D-TESTCONTAINERS-EXEC), pas de vert fabriqué.
- Alignement avec les 10 success criteria du requirements doc : chaque
  ligne du tableau ci-dessous référence le success criterion qu'elle
  couvre.

## Unit Tests

### `ResolveDocumentTemplateUseCaseImplTest` (nouveau)
- [x] Résolution exacte : `variantKey` + `locale` fournis et présents → template exact retourné
- [x] Fallback locale : `locale` absent en base pour ce variant → repli sur le template sans locale
- [x] Fallback variant : `variantKey` absent en base → repli sur le template par défaut (sans variant)
- [x] Cascade complète : ni variant ni locale ne matchent → repli sur le template par défaut du `templateKey`
- [x] Aucun template actif ne matche après cascade → `TemplateNotFoundException`
- [x] `templateKey` blanc/null → `TemplateValidationException` (scénario révisé : `DocumentTemplateSupport.requireText` lève `TemplateValidationException`, pas `IllegalArgumentException` comme prévu initialement)

### `ArchitectureRulesTest` (règle ajoutée)
- [x] `usecase_impl_implements_port_in` : toute classe `..usecase..*Impl` implémente au moins une interface de `..port.in..` — vert sur le code existant (aucune classe en violation)

### `SpringDataDossierRepository` / `JpaDossierRepositoryAdapter` (§37, modifié)
- [x] `findClosedForRetention(tenantId, limit)` ne retourne que les dossiers du tenant demandé (test avec 2 tenants, dossiers clôturés dans chacun)
- [x] Non-régression : `EvaluateDossierRetentionUseCaseImpl` (seul appelant) continue de fonctionner avec la nouvelle signature — test existant adapté, pas de comportement observable changé pour un tenant unique

## Integration Tests

### `FlowableLockDimensioningTest` (nouveau — H2 + threads réels, D-FLOWABLE-LOCK-TEST)
- [x] N threads réels tentent d'acquérir le même job Flowable simultanément → un seul thread obtient le verrou (`lockOwner` unique observé)
- [x] Le verrou expiré (`lockExpirationTime` dépassé) redevient acquérable par un autre thread (via le vrai `ResetExpiredJobsCmd` de Flowable, pas une simulation ad hoc)

### `ConcurrentWorkflowReservationTest` (nouveau — H2 + threads réels, D-PARALLELISM-TEST)
- [x] N threads réels appellent `AttachWorkflowUseCaseImpl.reserve()` simultanément sur le même dossier → une seule réservation réussit, les autres lèvent `WorkflowAlreadyRunningException` (pas de `DataIntegrityViolationException` qui fuite) — **a révélé un bug réel** (voir implementation doc, Design Deviations) corrigé avant que ce test passe
- [x] Après clôture (`abortReserved`) de la réservation active, une nouvelle réservation concurrente redevient possible

### `LiquibasePartialFailureIT` (nouveau — H2, D-LIQUIBASE-TEST)
- [x] Changelog de test avec un changeset en échec après N changesets réussis → l'exécution de `liquibase.update()` lève une exception, les N changesets précédents restent appliqués (pas de rollback silencieux inattendu)
- [x] Relancer `liquibase.update()` sur le même schéma (nouveau "démarrage") reprend au changeset en échec et termine avec succès une fois le changeset corrigé (`DATABASECHANGELOG` empêche la ré-application des changesets déjà réussis)

### `TenantSchemaIsolationIT` (nouveau — Testcontainers Postgres, D-CONTAINER-REUSE)
- [x] Deux schémas tenants créés sur le même conteneur Postgres, `search_path` fixé par connexion → une requête sur le schéma A ne retourne jamais de lignes du schéma B (scénario écrit, voir statut d'exécution ci-dessous)
- ⚠️ Non exécuté dans ce bac à sable (Docker absent) — écrit et compilé, `SKIPPED` proprement via `Assumptions.assumeTrue`/`isDockerAvailable()` (confirmé : `./mvnw verify` complet ne bloque ni n'échoue) ; exécution/vérification en CI (`ubuntu-latest`, Docker natif)

### `SequentialReferenceMultiPodIT` (nouveau — Testcontainers Postgres, D-CONTAINER-REUSE)
- [x] N threads réels simulant N pods appellent `SequentialReferenceGenerator` concurremment sur le même `(tenant_id, dossier_type_code, year)` → séquence générée sans doublon et sans trou (scénario écrit, voir statut d'exécution ci-dessous)
- ⚠️ Non exécuté dans ce bac à sable (Docker absent) — écrit et compilé, `SKIPPED` proprement ; exécution/vérification en CI

### Addendum — écart de couverture BRANCH comblé (découvert en Phase 5, hors scénarios initiaux)

`./mvnw verify` complet a montré BRANCH réel à 73.0%, sous le seuil
D-COVERAGE (75%). 8 classes jamais testées comblées (question posée à
l'utilisateur avant d'élargir le périmètre, voir implementation doc) :
- [x] `M2mJwtAuthoritiesConverterTest` — toutes les branches du convertisseur JWT M2M
- [x] `WebhookIdempotencyJpaEntityTest` — `equals`/`hashCode` de la clé composite `Pk`
- [x] `WorkflowIdempotencyJpaEntityTest` — `equals`/`hashCode` de la clé composite `IdempotencyId`
- [x] `StorageConfigTest` — beans S3 (`s3Client`, `s3DocumentStoragePort`) jamais instanciés par le contexte de test filesystem-only
- [x] `JpaWebhookIdempotencyStoreAdapterTest` — claim, course PK concurrente, expiration, in-flight, complete
- [x] `UpdateDossierStatusDelegateTest` — `JavaDelegate` Flowable jamais testé
- [x] `JpaDocumentTemplateRepositoryAdapterTest` — `listByTenantId`/`pageByTenantId` (filtre nul/vide vs présent), `save` chemin update

## End-to-End Tests

Non applicable — feature backend pure, aucun flux utilisateur ni
interface modifiés. Pas de scénario Playwright associé.

## Test Data

- `PostgresTestContainerSupport` (nouveau, `src/test/java/com/flowforge/support/`) : conteneur Postgres singleton statique, schéma nettoyé entre méthodes de test (`@BeforeEach`), pas de redémarrage de conteneur.
- Changelog Liquibase d'injection d'échec dédié : `src/test/resources/db/changelog/failure-injection/changelog-failure.yaml` (voir design doc, section « Data Models »).
- Pas de nouvelle donnée de seed en dehors des fixtures créées par chaque test lui-même (cohérent avec le style existant de la suite).

## Test Reporting & Coverage

- Commande : `./mvnw verify`. **État final :** 1050 tests, 0 échec ; LINE 93.7% (seuil 85%) ; BRANCH 75.0% (seuil 75%).
- Les 2 tests Testcontainers sont **exclus de la preuve de vert locale** dans ce bac à sable (Docker absent), `SKIPPED` proprement — statut réel consigné dans l'implementation doc, pas affirmé « vert » sans preuve.
- Tous les autres tests listés ci-dessus sont verts et vérifiés dans cette session (TDD : rouge avant l'implémentation correspondante à chaque fois que c'était possible, notamment pour `ConcurrentWorkflowReservationTest` qui a d'abord échoué avec `DataIntegrityViolationException` avant la correction de l'adaptateur).

## Manual Testing

Non applicable (aucune UI).

## Performance Testing

`FlowableLockDimensioningTest` et `SequentialReferenceMultiPodIT` sont
des tests de charge légers ciblés (dimensionnement du verrouillage sous
contention réelle), pas des tests de performance globaux — aucun
benchmark de débit n'est dans le périmètre (hors file, requirements
Phase 1+2).

## Bug Tracking

Aucun processus dédié pour cette feature — anomalies traitées comme
tout défaut de test découvert en TDD (corrigé avant de marquer la tâche
terminée).

## Requirements → Tests traceability

| Success criterion (requirements doc) | Test(s) |
|---|---|
| Test `search_path` isolation | `TenantSchemaIsolationIT` |
| Smoke test multi-pods séquence | `SequentialReferenceMultiPodIT` |
| Test verrous Flowable sous charge | `FlowableLockDimensioningTest` |
| Test migration Liquibase échec partiel | `LiquibasePartialFailureIT` |
| Règle ArchUnit `usecase.*Impl` → `port.in` | `ArchitectureRulesTest.usecase_impl_implements_port_in` |
| Test cascade templates | `ResolveDocumentTemplateUseCaseImplTest` |
| Test « un seul workflow actif » parallélisme réel | `ConcurrentWorkflowReservationTest` |
| §37 `tenantId` explicite | `SpringDataDossierRepository`/`JpaDossierRepositoryAdapter` tests |
| Seuils Jacoco LINE 85% / BRANCH 75% | `pom.xml` + `./mvnw verify` global |
| TODO.md §26/§28/§37 mis à jour | Hors tests — mise à jour doc post-implémentation |
