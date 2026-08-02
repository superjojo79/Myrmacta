---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-02
updated: 2026-08-02
---

# Testing Strategy

## Phase 7 note
IT happy-path API / persistence / smoke OK. Cases non cochés ci-dessous =
backlog **phase 8** (401/403, 409 collision, outbox/orphan units, config
non-admin).

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
- [x] `reserve` crée lien avec statut `initial`, `sequence` incrémentée,
      `processInstanceId` null, met à jour `currentWorkflowLinkId`
- [x] Second `reserve` pendant lien actif → `WorkflowAlreadyRunningException`
- [x] `confirmStarted` pose `processInstanceId`
- [ ] `reserveOrResume` reprend lien RESERVED même dossier+process
- [ ] `reserveOrResume` no-op conceptuel si ACTIVE + processInstanceId
- [ ] `reserveOrResume` → dead-letter path si autre workflow ACTIVE
      (exception collision)

### WorkflowTypeConfig / UpdateWorkflowLinkStatus
- [ ] Statut inconnu pour `workflowKey` → rejet
- [ ] Statut `initial` unique / présent à la config
- [x] `outcome != NONE` → `endedAt` renseigné, dossier libéré pour nouveau lien
- [ ] `outcome == NONE` → lien reste actif

### OutboxRelay / OutboxRepository (fakes)
- [ ] Happy path → `PROCESSED` après start+confirm
- [ ] Échec transient → `attempts++`, reste `PENDING`
- [ ] `attempts >= max` → `DEAD`
- [ ] `WorkflowAlreadyRunningException` → `DEAD` immédiat
- [ ] Rejeu : déjà confirmé → `markProcessed` sans second start

### WorkflowEvent sealed
- [ ] Publisher accepte Started/Completed/Failed
- [ ] Exhaustivité switch adaptateur test

### CompleteTaskUseCase (si logique domaine testable)
- [ ] Task inexistante → erreur métier
- [ ] Variables transmises au port moteur
- [ ] Clés réservées dans variables complete → rejet (aligné D-VARS)

### ManualStartOrchestrator / DD-TX / D-FAIL / D-IDEM
- [ ] Happy path : reserve→start→confirm → ACTIVE + idempotency row
- [ ] Replay même `Idempotency-Key` → même link, pas de second start
- [ ] Start Flowable KO → abortReserved ORPHAN_START_FAILED + erreur API
- [ ] Clés réservées dans body → 400 avant reserve

### ReservedReconcileJob (D-ORPHAN)
- [ ] Instance trouvée par businessKey=linkId → confirmStarted
- [ ] Pas d’instance + retry start OK → confirm
- [ ] Timeout sans instance → ORPHAN_TIMEOUT, dossier libéré

## Integration Tests
**How do we test component interactions?**

### Persistence H2
- [x] Changelog crée `dossier_workflow_link`, `outbox_event`,
      `workflow_type_config`
- [x] Index unique partiel : 2 liens `ended_at IS NULL` → échec contrainte
- [x] Round-trip config jsonb statusDefinitions
- [x] Outbox save/lock/markProcessed

### Flowable smoke
- [x] Déploiement process classpath (ex. `smoke-process.bpmn20.xml`)
- [x] Start via RuntimeService avec `tenantId` = `TenantProperties.id()`
- [x] Variables `dossierId` / `dossierWorkflowLinkId` présentes
- [x] Complete task utilisateur du smoke → process avance / termine
- [x] Delegate statut lien (si câblé dans smoke) met à jour le lien

### API MockMvc + OIDC
- [x] POST start : EDIT OK ; CSRF requis ; idempotency replay 200
- [ ] POST start : sans grant 403 ; anonyme 401
- [ ] Collision second start avec clé différente → 409 ProblemDetail
- [x] GET history : ordre `sequence`, VIEW OK
- [x] POST complete task : happy path
- [x] CRUD/GET `workflow-type-configs` : admin OK
- [ ] CRUD/GET `workflow-type-configs` : non-admin mutation restreinte

### Relais IT
- [ ] Insérer PENDING → après tick/invoke relais → instance Flowable +
      lien confirmé + PROCESSED
- [ ] Payload processKey inconnu → attempts / DEAD selon politique

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
- Clock / scheduler : invocation directe `relayPendingEvents()` en test
  (pas d’attente flaky).

## Coverage Targets

| Zone | Cible |
|---|---|
| Nouveau code domaine workflow / attach / outbox | ≥ 80 % lignes (Jacoco projet) |
| Suite globale | Jacoco ≥ 80 % maintenu |
| Branches relais (happy/retry/dead/idempotence) | toutes exercées |

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
