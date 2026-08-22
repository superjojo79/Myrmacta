---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
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
- [ ] Résolution exacte : `variantKey` + `locale` fournis et présents → template exact retourné
- [ ] Fallback locale : `locale` absent en base pour ce variant → repli sur le template sans locale
- [ ] Fallback variant : `variantKey` absent en base → repli sur le template par défaut (sans variant)
- [ ] Cascade complète : ni variant ni locale ne matchent → repli sur le template par défaut du `templateKey`
- [ ] Aucun template actif ne matche après cascade → `TemplateNotFoundException`
- [ ] `templateKey` blanc/null → `IllegalArgumentException` (via `DocumentTemplateSupport.requireText`)

### `ArchitectureRulesTest` (règle ajoutée)
- [ ] `usecase_impl_implements_port_in` : toute classe `..usecase..*Impl` implémente au moins une interface de `..port.in..` — vert sur le code existant (aucune classe en violation)

### `SpringDataDossierRepository` / `JpaDossierRepositoryAdapter` (§37, modifié)
- [ ] `findClosedForRetention(tenantId, limit)` ne retourne que les dossiers du tenant demandé (test avec 2 tenants, dossiers clôturés dans chacun)
- [ ] Non-régression : `EvaluateDossierRetentionUseCaseImpl` (seul appelant) continue de fonctionner avec la nouvelle signature — test existant adapté, pas de comportement observable changé pour un tenant unique

## Integration Tests

### `FlowableLockDimensioningTest` (nouveau — H2 + threads réels, D-FLOWABLE-LOCK-TEST)
- [ ] N threads réels tentent d'acquérir le même job Flowable simultanément → un seul thread obtient le verrou (`lockOwner` unique observé)
- [ ] Le verrou expiré (`lockExpirationTime` dépassé) redevient acquérable par un autre thread

### `ConcurrentWorkflowReservationTest` (nouveau — H2 + threads réels, D-PARALLELISM-TEST)
- [ ] N threads réels appellent `AttachWorkflowUseCaseImpl.reserve()` simultanément sur le même dossier → une seule réservation réussit, les autres lèvent `WorkflowAlreadyRunningException` (pas de `DataIntegrityViolationException` qui fuite)
- [ ] Après clôture (`abortReserved`) de la réservation active, une nouvelle réservation concurrente redevient possible

### `LiquibasePartialFailureIT` (nouveau — H2, D-LIQUIBASE-TEST)
- [ ] Changelog de test avec un changeset en échec après N changesets réussis → l'exécution de `liquibase.update()` lève une exception, les N changesets précédents restent appliqués (pas de rollback silencieux inattendu)
- [ ] Relancer `liquibase.update()` sur le même schéma (nouveau "démarrage") reprend au changeset en échec et termine avec succès une fois le changeset corrigé (`DATABASECHANGELOG` empêche la ré-application des changesets déjà réussis)

### `TenantSchemaIsolationIT` (nouveau — Testcontainers Postgres, D-CONTAINER-REUSE)
- [ ] Deux schémas tenants créés sur le même conteneur Postgres, `search_path` fixé par connexion → une requête sur le schéma A ne retourne jamais de lignes du schéma B
- [ ] ⚠️ Non exécuté dans ce bac à sable (Docker absent) — écrit et compilé ; exécution/vérification en CI (`ubuntu-latest`, Docker natif)

### `SequentialReferenceMultiPodIT` (nouveau — Testcontainers Postgres, D-CONTAINER-REUSE)
- [ ] N threads réels simulant N pods appellent `SequentialReferenceGenerator` concurremment sur le même `(tenant_id, dossier_type_code, year)` → séquence générée sans doublon et sans trou
- [ ] ⚠️ Non exécuté dans ce bac à sable (Docker absent) — écrit et compilé ; exécution/vérification en CI

## End-to-End Tests

Non applicable — feature backend pure, aucun flux utilisateur ni
interface modifiés. Pas de scénario Playwright associé.

## Test Data

- `PostgresTestContainerSupport` (nouveau, `src/test/java/com/flowforge/support/`) : conteneur Postgres singleton statique, schéma nettoyé entre méthodes de test (`@BeforeEach`), pas de redémarrage de conteneur.
- Changelog Liquibase d'injection d'échec dédié : `src/test/resources/db/changelog/failure-injection/changelog-failure.yaml` (voir design doc, section « Data Models »).
- Pas de nouvelle donnée de seed en dehors des fixtures créées par chaque test lui-même (cohérent avec le style existant de la suite).

## Test Reporting & Coverage

- Commande : `./mvnw verify` (Jacoco `BUNDLE` LINE ≥ 85%, BRANCH ≥ 75% après cette feature).
- Les 2 tests Testcontainers sont **exclus de la preuve de vert locale** dans ce bac à sable (Docker absent) — leur statut réel (écrit/compilé vs exécuté/vert) sera consigné explicitement dans le testing doc et l'implementation doc à la fin de la Phase 5, pas affirmé par défaut.
- Tous les autres tests listés ci-dessus doivent être verts et vérifiés dans cette session (TDD : rouge avant l'implémentation correspondante quand c'est possible).

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
