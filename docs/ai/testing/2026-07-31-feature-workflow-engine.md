---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
verified: 2026-08-02
created: 2026-08-02
updated: 2026-08-02
---

# Testing Strategy

## Phase 8 — résultats

`JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test` → **171 tests,
0 fail** ; Jacoco instructions **89,1 %** (≥ 80 %).

Fix TDD livré avec la couverture : `reserveOrResume` reprend un lien
**ACTIVE** même `processDefinitionKey` (rejeu outbox → `markProcessed`
sans second start).

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires domaine :** AttachWorkflow (reserve / resume / confirm /
  collision), validation `WorkflowTypeConfig` / statuts, sealed events,
  CompleteTask règles d’accès si purement domaine, outbox state machine
  (attempts → DEAD).
- **Unitaires infra :** relais (`processOne` happy / retry / dead /
  idempotence), mappers JPA, delegates statut (outcome terminal).
- **IT H2 MODE=PostgreSQL :** Liquibase tables lien/outbox/config ; index
  unique actif ; démarrage Flowable smoke BPMN ; API MockMvc + OIDC mock
  (start / history / complete / config).
- **Régression :** dossiers, auth BFF, ArchUnit, Jacoco ≥ 80 %, Tenant*.
- **Hors scope :** Testcontainers PG, charge multi-répliques Flowable,
  E2E navigateur, Keycloak réel.

## Unit Tests
**What individual components need testing?**

### AttachWorkflowUseCaseImpl
Fichier : `src/test/java/com/flowforge/domain/dossier/usecase/AttachWorkflowUseCaseImplTest.java`

- [x] `reserve` crée lien avec statut `initial`, `sequence` incrémentée,
      `processInstanceId` null, met à jour `currentWorkflowLinkId`
      (`WorkflowPersistenceTest`)
- [x] Second `reserve` pendant lien actif → `WorkflowAlreadyRunningException`
- [x] `confirmStarted` pose `processInstanceId`
- [x] `reserveOrResume` reprend lien RESERVED même dossier+process
- [x] `reserveOrResume` no-op conceptuel si ACTIVE + processInstanceId
- [x] `reserveOrResume` → dead-letter path si autre workflow ACTIVE
      (exception collision)

### WorkflowTypeConfig / UpdateWorkflowLinkStatus
Fichiers : `WorkflowTypeConfigTest.java`,
`UpdateWorkflowLinkStatusUseCaseImplTest.java`

- [x] Statut inconnu pour `workflowKey` → rejet
- [x] Statut `initial` unique / présent à la config
- [x] `outcome != NONE` → `endedAt` renseigné, dossier libéré pour nouveau lien
- [x] `outcome == NONE` → lien reste actif

### OutboxRelay / OutboxRepository (fakes)
Fichiers : `OutboxRelaySchedulerTest.java`, `WorkflowPersistenceTest.java`

- [x] Happy path → `PROCESSED` après start+confirm
- [x] Échec transient → `attempts++`, reste `PENDING`
- [x] `attempts >= max` → `DEAD`
- [x] `WorkflowAlreadyRunningException` → `DEAD` immédiat
- [x] Rejeu : déjà confirmé → `markProcessed` sans second start

### WorkflowEvent sealed
Fichiers : `WorkflowEventTest.java`, `InMemoryWorkflowEventPublisherTest.java`

- [x] Publisher accepte Started/Completed/Failed
- [x] Exhaustivité switch adaptateur test

### CompleteTaskUseCase (si logique domaine testable)
Fichier : `CompleteTaskUseCaseImplTest.java`

- [ ] Task inexistante → erreur métier *(délégé au moteur / API — hors UC)*
- [x] Variables transmises au port moteur
- [x] Clés réservées dans variables complete → rejet (aligné D-VARS)

### ManualStartOrchestrator / DD-TX / D-FAIL / D-IDEM
Fichier : `ManualStartOrchestratorTest.java` (+ API reserved vars)

- [x] Happy path : reserve→start→confirm → ACTIVE + idempotency row
- [x] Replay même `Idempotency-Key` → même link, pas de second start
- [x] Start Flowable KO → abortReserved ORPHAN_START_FAILED + erreur API
- [x] Clés réservées dans body → 400 avant reserve

### ReservedReconcileJob (D-ORPHAN)
Fichier : `ReservedReconcileJobTest.java`

- [x] Instance trouvée par businessKey=linkId → confirmStarted
- [x] Pas d’instance + retry start OK → confirm
- [x] Timeout sans instance → ORPHAN_TIMEOUT, dossier libéré

## Integration Tests
**How do we test component interactions?**

### Persistence H2
Fichier : `WorkflowPersistenceTest.java`

- [x] Changelog crée `dossier_workflow_link`, `outbox_event`,
      `workflow_type_config`
- [x] Index unique partiel : 2 liens `ended_at IS NULL` → échec contrainte
- [x] Round-trip config jsonb statusDefinitions
- [x] Outbox save/lock/markProcessed

### Flowable smoke
Fichier : `WorkflowApiTest.java` (parcours start→complete)

- [x] Déploiement process classpath (ex. `smoke-process.bpmn20.xml`)
- [x] Start via RuntimeService avec `tenantId` = `TenantProperties.id()`
- [x] Variables `dossierId` / `dossierWorkflowLinkId` présentes
- [x] Complete task utilisateur du smoke → process avance / termine
- [x] Delegate statut lien (si câblé dans smoke) met à jour le lien

### API MockMvc + OIDC
Fichier : `WorkflowApiTest.java`

- [x] POST start : EDIT OK ; CSRF requis ; idempotency replay 200
- [x] POST start : sans grant 403 ; anonyme 401
- [x] Collision second start avec clé différente → 409 ProblemDetail
- [x] GET history : ordre `sequence`, VIEW OK
- [x] POST complete task : happy path
- [x] CRUD/GET `workflow-type-configs` : admin OK
- [x] CRUD/GET `workflow-type-configs` : non-admin mutation restreinte

### Relais IT
Fichier : `OutboxRelayIT.java`

- [x] Insérer PENDING → après tick/invoke relais → instance Flowable +
      lien confirmé + PROCESSED
- [x] Payload processKey inconnu → attempts / DEAD selon politique

## End-to-End Tests
**What user journeys need testing?**

- Hors scope navigateur. Parcours API IT ci-dessus couvrent le journey
  « config → start → task → history ».

## Test Data & Fixtures
**What test data do we need?**

- Fixture `DossierType` + dossier `DRAFT`/`EN_COURS` existants (réutiliser
  helpers dossiers).
- `WorkflowTypeConfig` seed pour `smokeProcess` avec au moins un statut
  `initial` et un terminal `COMPLETED`.
- BPMN smoke DD-SMOKE : start → userTask → delegate statut terminal → end.
- Utilisateurs mock : `TENANT_ADMIN`, `WORKFLOW_ADMIN`, user EDIT, user VIEW,
  anonyme.
- Clock / scheduler : invocation directe `processOne` / `reconcile` en test
  (pas d’attente flaky).

## Coverage Targets

| Zone | Cible | Preuve phase 8 |
|---|---|---|
| Nouveau code domaine workflow / attach / outbox | ≥ 80 % lignes (Jacoco projet) | **89,1 %** instructions |
| Suite globale | Jacoco ≥ 80 % maintenu | OK |
| Branches relais (happy/retry/dead/idempotence) | toutes exercées | Units + IT |

## Alignment with requirements

| Critère succès requirements | Couverture test |
|---|---|
| Flowable actif + tables | IT démarrage + persistence |
| Start API + history + complete | API MockMvc |
| Relais idempotent / DLQ | Unit + IT relais |
| Stubs prod retirés | ArchUnit / smoke DomainConfig |
| Non-régression dossiers/auth | Suites existantes CI |

## Mocks & Boundaries

- Pas de Keycloak réel — `oidcLogin()` / users mock.
- Pas de broker ; outbox = table.
- Producteur connecteurs mocké par insert repository en IT.
- PG réel / charge async multi-pods : différé TODO 28.
