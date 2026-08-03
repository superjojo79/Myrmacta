---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: in-progress
created: 2026-08-03
updated: 2026-08-03
design_aligned: 2026-08-03
phase4: 2026-08-03
phase5: 2026-08-03
---

# Project Planning & Task Breakdown

Plan initial **phase 4** — dérivé des docs `requirements` / `design`
(`reviewed`) et `testing` (`draft` design_aligned).

**Phase 5 en cours** — T1–T6 done. Next : T7.
Traçage `task` CLI : **indisponible**.
Preuve T5–T6 + ArchUnit : 29 tests ciblés / 0 fail.

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Domaine & ports** : models, exceptions, ports admin/trigger/execute/cipher
- [x] **M2 — Persistence & crypto** : Liquibase + JPA + EnvMasterKeyCipher + idempotency
- [x] **M3 — Registry & builtins trigger** : registry, JsonPath, http-rest TRIGGER, TriggerRuntime
- [ ] **M4 — Security & HTTP** : 3 chaînes, webhook harden, admin CRUD, dual execute
- [ ] **M5 — flowable-signal** : SERVICE + listener TRIGGER + ServiceTask + smoke BPMN
- [ ] **M6 — Preuves & docs** : IT complètes, Jacoco ≥ 80 %, doc implementation

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

- [ ] **T7. Security chains Order 1/2/3**
  - **Outcome :** ajouter `spring-boot-starter-oauth2-resource-server` ;
    M2M `@Order(1)` matcher `/api/m2m/**` (JWT + tenant_id +
    `API_SERVICE`) ; webhook `@Order(2)` `/api/webhooks/**` (stateless,
    permitAll Spring — auth métier ensuite) ; BFF reste `@Order(3)`.
  - **Depends on :** —
  - **Validation :** contexte Security boot ; MockMvc chaînes.
  - **Tests liés :** MockMvc execute dual ; Security Testing chaînes.

- [ ] **T8. Webhook pipeline**
  - **Outcome :** filters max-body (413) + rate-limit (429) ;
    `HmacWebhookAuthenticator` (`X-Signature-256`) ;
    `SharedSecretWebhookAuthenticator` (`X-Webhook-Token`) ;
    `WebhookController` : load instance, auth, `Idempotency-Key` **requis**
    (400 si absent), runtime, store idempotency, **202** ; disabled /
    soft-deleted / unknown → **404** ; auth KO → **401** ProblemDetail.
  - **Depends on :** T4, T6, T7
  - **Validation :** unit authenticators/idempotency/harden + MockMvc.
  - **Tests liés :** Unit Webhook authenticators ; Idempotency ; Rate
    limit/max body ; MockMvc webhook ; E2E webhook→outbox.

- [ ] **T9. Admin API + dual execute**
  - **Outcome :** `GET /api/connector-definitions` ;
    CRUD `/api/connectors` soft-delete/restore PATCH ; secrets masqués ;
    nom dupliqué → 409 ; `IP_ALLOWLIST` → 400 ;
    `POST /api/connectors/{id}/execute` (BFF `TENANT_ADMIN`+CSRF) ;
    `POST /api/m2m/connectors/{id}/execute` (M2M) ; même use case ;
    ProblemDetail ; `@PreAuthorize`.
  - **Depends on :** T4, T5, T7 ; execute use case (T10 peut finaliser
    signal — admin/execute shell OK avec stub SERVICE en attendant)
  - **Validation :** MockMvc admin + execute dual.
  - **Tests liés :** MockMvc admin ; MockMvc execute ; Security Testing.

### M5 — flowable-signal

- [ ] **T10. FlowableSignalConnector SERVICE + ConnectorServiceTask**
  - **Outcome :** builtin BOTH ; `execute` → `signalEventReceived` ;
    JavaDelegate `connectorServiceTask` (variable `connectorInstanceId`) ;
    refus disabled / mauvais capability.
  - **Depends on :** T5, T4, RuntimeService
  - **Validation :** unit SERVICE mock RuntimeService ; smoke partiel.
  - **Tests liés :** Unit FlowableSignal SERVICE ; Flowable smoke.

- [ ] **T11. FlowableSignalEventListener TRIGGER + smoke BPMN**
  - **Outcome :** listener global filtré `signalName` → runtime (T6) ;
    ignore soft-deleted/disabled ; process classpath smoke émettant /
    prouvant le chemin ; O5 type événement tranché en code.
  - **Depends on :** T6, T10
  - **Validation :** unit listener ; IT smoke signal → outbox.
  - **Tests liés :** Unit FlowableSignalEventListener ; Flowable smoke ;
    E2E outbox.

### M6 — Preuves & docs

- [ ] **T12. Suite tests + régressions**
  - **Outcome :** scenarios testing doc verts ; Jacoco ≥ 80 % ; ArchUnit ;
    dossiers / workflow / auth non régressés ; master-key test documentée.
  - **Depends on :** T7–T11
  - **Validation :** `./mvnw verify`.
  - **Tests liés :** tous.

- [ ] **T13. Doc implementation + follow-ups**
  - **Outcome :** notes impl (chaînes, crypto, dual URL, listener) ;
    non-goals (smtp/s3/scheduler, http-rest SERVICE, Vault, IP allowlist) ;
    pointer DEFERRED / TODO §30 restant.
  - **Depends on :** T12
  - **Validation :** relecture.
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

- **Next 3 tasks (phase 5) :** T7 → T8 → T9.
- Risque sequencing : T9 execute peut appeler use case avant T10 si
  `flowable-signal` pas encore là — enchaîner T10 avant de finaliser
  execute IT signal, ou stub temporaire.
- Dep Maven resource-server dès T7.
- JDK 25 requis (`JAVA_HOME`).

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Conflit SecurityFilterChain execute BFF/M2M | Dual URL déjà tranché (DD-EXECUTE-ROUTE) ; matcher `/api/m2m/**` only Order 1 |
| Unique partiel H2 | MODE=PostgreSQL ; doc écart si besoin |
| Type événement Flowable 8 (O5) | Spike court dans T11 + smoke |
| Secrets en clair logs/API | Mask adapter + tests Security |
| Flaky rate-limit / clock | compteur injectable / horloge fixe en tests |
| Périmètre smtp/s3/SSRF | non-goals — ne pas implémenter |

## Resources Needed

- Docs requirements/design `reviewed` ; testing `draft` design_aligned
- Analyse `06-connecteurs.md` ; décisions 3/4/5/14/15/16/17/18
- Outbox + dossier use cases déjà livrés (`workflow-engine`, `dossiers`)
- Worktree `.worktrees/feature-connecteurs` ; `./mvnw` ; Java 25

## Traceability testing → tasks

| Scénario testing | Tasks |
|---|---|
| Mapping déclaratif / JsonPath | T5 |
| ConnectorTriggerRuntime | T6 |
| Cipher Env / DEK | T4 |
| Webhook authenticators | T8 |
| Idempotency | T4, T8 |
| Rate limit / max body | T8 |
| FlowableSignal SERVICE | T10 |
| FlowableSignalEventListener | T11 |
| Registry builtins | T5, T10 |
| Liquibase / JPA encrypt | T3, T4 |
| MockMvc admin soft-delete/restore/409 | T9 |
| MockMvc webhook 202/401/400/404/413/429 | T8 |
| MockMvc execute dual URL | T7, T9, T10 |
| Flowable smoke signal | T10, T11 |
| Webhook → outbox → relais | T6, T8, T12 |
| Security Testing / ArchUnit / Jacoco | T7, T9, T12 |

## Notes phase 4

- Planning skeleton remplacé ; **13 tâches** (T1–T13), **6 milestones**.
- Pas d’estimation calendaire — séquencement technique ci-dessus.
- Suite : **Phase 5 Execute Plan** (`dev-implementation` + `tdd`).
