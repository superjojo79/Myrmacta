---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires :** 100 % du code domaine et adaptateurs in-memory introduits par cette feature
  (`StartWorkflowUseCaseImpl`, modèle, repositories/publishers in-memory).
- **ArchUnit :** 3 règles toujours exécutées en `./mvnw test`.
- **Intégration Spring :** smoke optionnel sur `DomainConfig` (chargement des beans) —
  sans datasource.
- **E2E / UI :** hors scope (pas de HTTP).

Alignement critères de succès requirements : use case testable sans Spring ; ArchUnit vert ;
`./mvnw test` vert.

## Unit Tests
**What individual components need testing?**

### StartWorkflowUseCaseImpl
- [x] Démarre un workflow : statut `RUNNING`, `save` appelé, événement publié
- [x] Propage processKey / tenantId depuis la commande
- [x] Échoue proprement si le port repository lève une exception (pas de publish orphelin si save échoue — ordre save puis publish)

### WorkflowInstance (modèle)
- [x] `create` produit une instance `RUNNING` avec id non nul
- [x] `complete` depuis `RUNNING` → `COMPLETED`
- [x] `complete` hors `RUNNING` → exception domaine

### InMemoryWorkflowRepository
- [x] `save` puis `findById` retourne l’instance
- [x] `findById` inconnu → `Optional.empty()`

### InMemoryWorkflowEventPublisher
- [x] `publish` enregistre l’événement (observable en test)
- [x] Plusieurs publications conservées dans l’ordre

## Integration Tests
**How do we test component interactions?**

- [x] `DomainConfig` : contexte Spring minimal (ou test de configuration) expose
      `StartWorkflowUseCase` câblé aux beans in-memory — **sans**
      DataSource / Flowable / Security
- [x] Parcours câblé : `useCase.start(cmd)` via bean Spring → instance retrouvable via repository bean
- [ ] Pas de tests d’API HTTP (hors scope)

## End-to-End Tests
**What user flows need validation?**

- [ ] N/A — pas de parcours utilisateur UI/HTTP dans cette feature

## Architecture Tests (ArchUnit)
- [x] Domaine ne dépend pas de `org.springframework..`, `jakarta.persistence..`, `org.flowable..`
- [x] Domaine ne dépend pas de `..infrastructure..`
- [x] Classes concrètes dans `..usecase..` suffixées `Impl`
- [x] Après introduction de `*Impl`, retirer ou restreindre `allowEmptyShould(true)` là où ce n’est plus nécessaire

## Test Data
**What data do we use for testing?**

- Commandes fixtures : `processKey=demo-process`, `tenantId=tenant-a`
- Pas de base de données ; maps concurrentes in-memory dans les adaptateurs de test/prod-stub
- Mocks Mockito acceptés en unitaire pur en alternative aux in-memory pour le use case

## Test Reporting & Coverage
**How do we verify and communicate test results?**

```bash
./mvnw test
# optionnel plus tard : ./mvnw test jacoco:report
```

- Seuil immédiat : **0 failure** Surefire (unitaires + ArchUnit).
- Jacoco / seuil % : à activer en phase testing (phase 8) si absent du pom.

## Manual Testing
**What requires human validation?**

- Revue de la structure de packages et du rename `com.flowforge`
- Lecture de `DomainConfig` pour valider le pattern de câblage documenté

## Performance Testing
**How do we validate performance?**

- N/A pour cette feature (pas d’IO externe métier).
- Critère qualitatif : test unitaire use case sans Spring context.

## Bug Tracking
**How do we manage issues?**

- Échecs ArchUnit / unitaires = bloquants PR.
- Régressions de convention (`*Impl`, fuite framework dans domain) traitées comme bugs d’architecture priorité haute.
