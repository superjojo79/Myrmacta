---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: completed
created: 2026-08-03
updated: 2026-08-03
design_aligned: 2026-08-03
phase4: 2026-08-03
phase5: 2026-08-03
phase6: 2026-08-03
---

# Project Planning & Task Breakdown

Plan **phase 4–6** — dérivé des docs `requirements` / `design`
(`reviewed`) et `testing` (`draft` design_aligned).

**Phase 5–6 done** — T1–T13. Traçage `task` CLI : **indisponible**.
Preuve T12 : `./mvnw verify` → **271 / 0**, Jacoco instructions **85,52 %**.

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Domaine & ports** : models, exceptions, ports admin/trigger/execute/cipher
- [x] **M2 — Persistence & crypto** : Liquibase + JPA + EnvMasterKeyCipher + idempotency
- [x] **M3 — Registry & builtins trigger** : registry, JsonPath, http-rest TRIGGER, TriggerRuntime
- [x] **M4 — Security & HTTP** : 3 chaînes, webhook harden, admin CRUD, dual execute
- [x] **M5 — flowable-signal** : SERVICE + listener TRIGGER + ServiceTask + smoke BPMN
- [x] **M6 — Preuves & docs** : IT complètes, Jacoco ≥ 80 %, doc implementation

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine & ports

- [x] **T1. Models domaine connector**
  - **Outcome :** models + enums + exceptions + `ConnectorDefinition` /
    `TriggerConnector` / `ServiceConnector`.
  - **Depends on :** —
  - **Validation :** `ConnectorModelsTest` 12/0.
  - **Tests liés :** ConnectorModelsTest.

- [x] **T2. Ports in/out + properties**
  - **Outcome :** ports repo/cipher/idempotency + manage/execute/trigger ;
    `ConnectorProperties` + yaml `app.connector.*`.
  - **Depends on :** T1
  - **Validation :** ConnectorPropertiesTest + ConnectorPortsContractTest.
  - **Tests liés :** Unit cipher/idempotency/harden (T4, T9).

### M2 — Persistence & crypto

- [x] **T3. Liquibase connector tables**
  - **Outcome :** `003-connector-tables.yaml` ; unique partiel PG /
    `active_name` H2 ; DEK + webhook_idempotency.
  - **Depends on :** T1
  - **Validation :** `ConnectorSchemaIT` 2/0.
  - **Tests liés :** IT Liquibase/JPA.

- [x] **T4. JPA adapters + EnvMasterKeyCipher + idempotency**
  - **Outcome :** `ConnectorInstanceJpaEntity` / adapter (encrypt on save,
    decrypt on load, soft-delete) ; `EnvMasterKeyCipher` + `JpaTenantDekStore` ;
    `JpaWebhookIdempotencyStoreAdapter` TTL 24h ; master-key test properties.
  - **Depends on :** T2, T3
  - **Validation :** EnvMasterKeyCipherTest 4/0 ; ConnectorPersistenceIT 3/0.
  - **Tests liés :** Unit ConnectorSecretCipher ; IT Liquibase/JPA.

### M3 — Registry & builtins trigger

- [x] **T5. ConnectorRegistry + SimpleJsonPathMapper + http-rest TRIGGER**
  - **Outcome :** `ConnectorCatalog` / `ConnectorRegistry` ; mapper `$` / `.` /
    `[n]` ; `HttpRestTriggerConnector` mapping déclaratif → 1..N events ;
    reject `IP_ALLOWLIST`.
  - **Depends on :** T1, T2
  - **Validation :** SimpleJsonPathMapperTest 5/0 ; Registry 2/0 ; HttpRest 5/0.
  - **Tests liés :** Unit Mapping ; SimpleJsonPathMapper ; Registry.

- [x] **T6. ConnectorTriggerRuntime**
  - **Outcome :** create dossier / existing ref (`AccessContext.system`) ;
    batch TX-ready ; `OutboxRepository.savePending` only — **no** Flowable start.
  - **Depends on :** T2, T5
  - **Validation :** ConnectorTriggerRuntimeImplTest 5/0 ; ArchUnit OK.
  - **Tests liés :** Unit ConnectorTriggerRuntime.

### M4 — Security & HTTP

- [x] **T7. Security chains Order 1/2/3**
  - **Outcome :** `spring-boot-starter-oauth2-resource-server` ;
    M2M `@Order(1)` `/api/m2m/**` (JWT + tenant_id + `API_SERVICE`) ;
    webhook `@Order(2)` `/api/webhooks/**` permitAll ; BFF `@Order(3)`.
  - **Depends on :** —
  - **Validation :** contexte Security boot ; MockMvc admin/webhook.
  - **Tests liés :** MockMvc execute dual ; Security Testing chaînes.

- [x] **T8. Webhook pipeline**
  - **Outcome :** max-body (413) + rate-limit (429) ;
    `HmacWebhookAuthenticator` / `SharedSecretWebhookAuthenticator` ;
    `WebhookController` + `Idempotency-Key` requis ; auth KO → **401**.
  - **Depends on :** T4, T6, T7
  - **Validation :** WebhookAuthenticatorTest + WebhookControllerTest.
  - **Tests liés :** Unit Webhook authenticators ; MockMvc webhook.

- [x] **T9. Admin API + dual execute**
  - **Outcome :** definitions + CRUD soft-delete/restore ; secrets masqués ;
    BFF + M2M execute ; même use case.
  - **Depends on :** T4, T5, T7
  - **Validation :** ConnectorAdminApiTest ; ExecuteConnectorUseCaseImplTest.
  - **Tests liés :** MockMvc admin ; Security Testing.

### M5 — flowable-signal

- [x] **T10. FlowableSignalConnector SERVICE + ConnectorServiceTask**
  - **Outcome :** builtin BOTH ; `signalEventReceivedWithTenantId` ;
    JavaDelegate `connectorServiceTask` (`connectorInstanceId`).
  - **Depends on :** T5, T4, RuntimeService
  - **Validation :** FlowableSignalConnectorTest 5/0 ; ConnectorServiceTaskTest 3/0.
  - **Tests liés :** Unit FlowableSignal SERVICE.

- [x] **T11. FlowableSignalEventListener TRIGGER + smoke BPMN**
  - **Outcome :** listener `ACTIVITY_SIGNALED` → runtime ; ignore
    disabled/non-match ; `signal-smoke.bpmn20.xml` + SignalSmokeIT.
  - **Depends on :** T6, T10
  - **Validation :** FlowableSignalEventListenerTest 2/0 ; SignalSmokeIT 1/0.
  - **Tests liés :** Unit listener ; Flowable smoke.

### M6 — Preuves & docs

- [x] **T12. Suite tests + régressions**
  - **Outcome :** `./mvnw verify` vert ; Jacoco ≥ 80 % ; ArchUnit ;
    dossiers / workflow / auth non régressés.
  - **Depends on :** T7–T11
  - **Validation :** 271 tests / 0 fail ; Jacoco instructions **85,52 %**.
  - **Tests liés :** tous.

- [x] **T13. Doc implementation + follow-ups**
  - **Outcome :** notes impl (chaînes, crypto, dual URL, listener) ;
    non-goals documentés ; DEFERRED hors scope.
  - **Depends on :** T12
  - **Validation :** relecture docs ai/*.
  - **Tests liés :** —

## Dependencies
**What needs to happen in what order?**

```text
T1 → T2 → T3 → T4
T1 → T5 → T6
T7 ∥ (T1–T6)
T4,T6,T7 → T8
T4,T5,T7 → T9
T5,T4 → T10 → T11
T8,T9,T11 → T12 → T13
```

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Conflit SecurityFilterChain execute BFF/M2M | Dual URL (DD-EXECUTE-ROUTE) ; matcher `/api/m2m/**` Order 1 |
| Unique partiel H2 | MODE=PostgreSQL + `active_name` |
| Type événement Flowable 8 (O5) | Clos — `ACTIVITY_SIGNALED` + smoke |
| Secrets en clair logs/API | Mask adapter + tests |
| Flaky rate-limit / clock | compteur injectable en tests |
| Périmètre smtp/s3/SSRF | non-goals — non implémentés |

## Resources Needed

- Docs requirements/design `reviewed` ; testing `draft` design_aligned
- Worktree `.worktrees/feature-connecteurs` ; `./mvnw` ; Java 25
- Preuve T12 : `JAVA_HOME=…/jdk-25.0.4+7 ./mvnw verify`
