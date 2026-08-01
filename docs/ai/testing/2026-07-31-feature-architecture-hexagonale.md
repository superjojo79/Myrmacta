---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
verified: 2026-07-31
updated: 2026-08-01
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires :** 100 % du code domaine et adaptateurs in-memory introduits par cette feature
  (`StartWorkflowUseCaseImpl`, modèle, repositories/publishers in-memory).
- **ArchUnit :** 3 règles toujours exécutées en `./mvnw test`.
- **Intégration Spring :** smoke `DomainConfigTest` — sans datasource.
- **E2E / UI :** hors scope (pas de HTTP).
- **Jacoco :** seuil bundle **≥ 80 %** lignes (`./mvnw verify`).

Alignement critères de succès requirements : use case testable sans Spring ; ArchUnit vert ;
`./mvnw test` / `verify` verts.

## Unit Tests
**What individual components need testing?**

### StartWorkflowUseCaseImpl
Fichier : `src/test/java/com/flowforge/domain/workflow/usecase/StartWorkflowUseCaseImplTest.java`

- [x] Démarre un workflow : statut `RUNNING`, `save` appelé, événement publié
- [x] Propage processKey / tenantId depuis la commande
- [x] Échoue proprement si le port repository lève une exception (pas de publish orphelin si save échoue — ordre save puis publish)

### WorkflowInstance (modèle)
Fichier : `src/test/java/com/flowforge/domain/workflow/model/WorkflowInstanceTest.java`

- [x] `create` produit une instance `RUNNING` avec id non nul
- [x] `complete` depuis `RUNNING` → `COMPLETED`
- [x] `complete` hors `RUNNING` → exception domaine
- [x] `create(null)` → `NullPointerException`

### InMemoryWorkflowRepository
Fichier : `src/test/java/com/flowforge/infrastructure/persistence/workflow/InMemoryWorkflowRepositoryTest.java`

- [x] `save` puis `findById` retourne l’instance
- [x] `findById` inconnu → `Optional.empty()`
- [x] `save` écrase une entrée existante (même id)

### InMemoryWorkflowEventPublisher
Fichier : `src/test/java/com/flowforge/infrastructure/flowable/InMemoryWorkflowEventPublisherTest.java`

- [x] `publish` enregistre l’événement (observable en test)
- [x] Plusieurs publications conservées dans l’ordre

## Integration Tests
**How do we test component interactions?**

Fichier : `src/test/java/com/flowforge/infrastructure/config/DomainConfigTest.java`

- [x] `DomainConfig` : contexte Spring expose `StartWorkflowUseCase` câblé aux beans
      in-memory — **sans** DataSource / Flowable / Security
- [x] Parcours câblé : `useCase.start(cmd)` via bean Spring → instance retrouvable via repository bean
- [x] Pas de tests d’API HTTP (hors scope — N/A)

## End-to-End Tests
**What user flows need validation?**

- [x] N/A — pas de parcours utilisateur UI/HTTP dans cette feature

## Architecture Tests (ArchUnit)
Fichier : `src/test/java/com/flowforge/ArchitectureRulesTest.java`

- [x] Domaine ne dépend pas de `org.springframework..`, `jakarta.persistence..`, `org.flowable..`
- [x] Domaine ne dépend pas de `..infrastructure..`
- [x] Classes concrètes dans `..usecase..` suffixées `Impl`
- [x] Après introduction de `*Impl`, `allowEmptyShould(true)` retiré sur la règle `usecase`

## Test Data
**What data do we use for testing?**

- Commandes fixtures : `processKey=demo-process`, `tenantId=tenant-a`
- Pas de base de données ; `ConcurrentHashMap` / `CopyOnWriteArrayList` in-memory
- Mocks Mockito pour le use case unitaire

## Test Reporting & Coverage
**How do we verify and communicate test results?**

```bash
./mvnw test          # Surefire + rapport Jacoco
./mvnw verify        # + jacoco:check (≥ 80 % lignes bundle)
```

### Evidence phase 8 (2026-07-31)

| Commande | Résultat |
|---|---|
| `./mvnw test` | **15** tests, 0 failure, BUILD SUCCESS |
| Jacoco lignes (bundle) | **95.7 %** (45/47) — seul `FlowForgeApplication.main` non couvert |
| Packages feature (domain.workflow + in-memory + DomainConfig) | **100 %** lignes |

Rapport : `target/site/jacoco/index.html`

## Manual Testing
**What requires human validation?**

- [x] Revue structure packages / rename `com.flowforge` (faite en phases 5–7)
- [x] Lecture `DomainConfig` + doc implementation

## Performance Testing
**How do we validate performance?**

- N/A pour cette feature (pas d’IO externe métier).
- Critère qualitatif : test unitaire use case sans Spring context — OK.

## Bug Tracking
**How do we manage issues?**

- Échecs ArchUnit / unitaires / `jacoco:check` = bloquants PR.
- Régressions de convention (`*Impl`, fuite framework dans domain) = bugs d’architecture priorité haute.

## Phase 8 notes

- Écarts de couverture : aucun sur le code de feature ; `main()` non testé (acceptable).
- Deux tests ajoutés en phase 8 : NPE `create(null)` ; overwrite repository.
- Jacoco activé dans `pom.xml` (seuil 80 %).
- **Lifecycle :** phase 9 terminée — prêt à merger.
