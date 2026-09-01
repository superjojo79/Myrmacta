---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy

> Cases cochées en Phase 5 (`dev-implementation`) au fil du TDD, `./mvnw verify` vert (1430 tests,
> ArchUnit compris) après M1–M3 — Phase 8 (`dev-testing`) fera un balayage Jacoco classe par classe
> pour chercher des lacunes réelles au-delà de ces scénarios déjà couverts.

## Test Coverage Goals
**What level of testing do we aim for?**

- 100% des branches nouvelles/modifiées (gardes blank + callback MDC) — cohérent avec le seuil
  Jacoco du bundle (LINE ≥85%/BRANCH ≥75%).
- Aucune régression sur les tests `requireNonNull` existants des 4 records ciblés.
- Aucun test frontend nouveau — feature confirmée backend seul.

## Unit Tests
**What individual components need testing?**

### Records domaine — validation blank (D-BLANK-VALIDATION-SCOPE)
- [x] T-BLANK-1 : `WorkflowTypeConfig` — `workflowKey` blank (`""`, `"   "`) → `IllegalArgumentException`
- [x] T-BLANK-2 : `TriggerOutboxPayload` — `processKey` blank → `IllegalArgumentException`
- [x] T-BLANK-3 : `WorkflowStartedEvent` — `processKey` blank → `IllegalArgumentException`
- [x] T-BLANK-4 : `DossierType` — `workflowKey` blank → `IllegalArgumentException`
- [x] T-BLANK-5 (régression, ×4 records) : `workflowKey`/`processKey` `null` → `NullPointerException`
      (comportement `requireNonNull` inchangé)

### ProcessPurgeOutboxUseCaseImpl — callback MDC (D-MDC-DOSSIER-MECHANISM)
- [x] T-MDC-1 : `onItemStart.apply(dossierId)` appelé exactement une fois par item traité, avec le
      `dossierId` correct, avant `purgeDossier`
- [x] T-MDC-2 : le `Runnable` retourné par `onItemStart` est exécuté (`run()`) même quand l'item
      échoue (`RuntimeException` catchée → `markFailed`) — via un `finally`
- [x] T-MDC-3 : callback non invoqué pour un item skippé par `shouldSkipClock` ou non `claim()`é
- [x] T-MDC-4 (régression) : `ArchitectureRulesTest.domain_has_no_framework_dependency` reste vert
      (pas de nouveau test dédié — la règle existante suffit)
- [x] T-MDC-5 (régression signature) : `ProcessPurgeOutboxUseCaseImplTest` mis à jour pour le
      3ᵉ paramètre (`processBatch(Instant, int, Function<UUID, Runnable>)`), tests existants
      (happy path, skip, échec) continuent de passer avec un callback no-op par défaut

### PurgeOutboxJob — intégration callback → MdcScope
- [x] T-JOB-1 : `dossierId` présent dans le MDC pendant le traitement de l'item correspondant
      (callback `dossierId -> { MdcScope ...; return scope::close; }`)
- [x] T-JOB-2 : `dossierId` retiré du MDC après l'item (le `Runnable` fermeur a été appelé),
      `tenantId` toujours présent (comportement `MdcScope` empilé, cf. `MdcScope.and`/`close`)
- [x] T-JOB-3 (régression signature) : `PurgeOutboxJobTest` mis à jour — mock
      `processBatch(any(Instant.class), eq(50), any())`

### PurgeOutboxApiTest — régression signature
- [x] T-API-1 (régression) : `PurgeOutboxApiTest` ligne ~142, appel direct
      `processPurgeOutboxUseCase.processBatch(Instant.now(), 2000)` mis à jour vers la signature à
      3 arguments (callback no-op), test toujours vert

## Integration Tests
**How do we test component interactions?**

- [x] T-INT-1 : `PurgeOutboxJob` avec un batch de 2+ items distincts — chacun son propre `dossierId`
      en MDC au bon moment, pas de fuite entre items (le fermeur du 1er item ne doit pas effacer le
      `dossierId` du 2ᵉ si mal séquencé)
- [x] T-INT-2 (régression globale) : `ArchitectureRulesTest` complet vert, pas seulement la règle
      domaine ciblée

## End-to-End Tests
**What user flows need validation?**

- Aucun — feature backend seul, aucun flux utilisateur visible modifié.

## Test Data
**What data do we use for testing?**

- Records domaine construits directement en test unitaire (pas de fixture DB nécessaire pour les
  gardes blank).
- `PurgeOutboxRecord`/`Dossier` de test déjà existants dans `ProcessPurgeOutboxUseCaseImplTest`,
  réutilisés pour vérifier le callback (T-MDC-1 à T-MDC-3).

## Test Reporting & Coverage
**How do we verify and communicate test results?**

- `./mvnw verify` (Jacoco) — vérifier que les nouvelles branches (gardes blank, callback) sont
  couvertes ; inspection `target/site/jacoco/<pkg>/<Class>.java.html` si le bundle ne suffit pas à
  localiser un gap.
- Pas de commande frontend concernée.

## Manual Testing
**What requires human validation?**

- Aucun — pas de surface UI/UX modifiée.

## Performance Testing
**How do we validate performance?**

- Non applicable — pas de changement de volumétrie ni de chemin chaud identifié.

## Bug Tracking
**How do we manage issues?**

- Suivi standard du dépôt (TODO.md §49, PR de la feature) — pas de process dédié supplémentaire.
