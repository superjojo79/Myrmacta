---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: ready
created: 2026-08-03
updated: 2026-08-03
design_aligned: 2026-08-03
phase4: 2026-08-03
---

# Project Planning & Task Breakdown

Plan initial **phase 4** — dérivé des docs `requirements` / `design`
(`reviewed`) et `testing` (`draft` design_aligned). Prêt pour phase 5.

Traçage `task` CLI : **indisponible**.

## Milestones
**What are the major checkpoints?**

- [ ] **M1 — Domaine & ports** : models, exceptions, ports admin/trigger/execute/cipher
- [ ] **M2 — Persistence & crypto** : Liquibase, JPA soft-delete, EnvMasterKeyCipher, idempotency store
- [ ] **M3 — Registry & builtins trigger** : registry, JsonPath, http-rest TRIGGER, TriggerRuntime
- [ ] **M4 — Security & HTTP** : 3 chaînes, webhook harden, admin CRUD, dual execute
- [ ] **M5 — flowable-signal** : SERVICE + listener TRIGGER + ServiceTask + smoke BPMN
- [ ] **M6 — Preuves & docs** : IT complètes, Jacoco ≥ 80 %, doc implementation

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine & ports

- [ ] **T1. Models domaine connector**
  - **Outcome :** `ConnectorInstanceData`, `WorkflowTriggerEvent`,
    `ConfigField`, `ConnectorCapability`, `WebhookAuthType`
    (HMAC / SHARED_SECRET only) ; exceptions
    (`UnknownConnectorException`, `WebhookAuthenticationException`,
    `ConnectorValidationException`, …).
  - **Depends on :** —
  - **Validation :** compile ; ArchUnit domaine pur.
  - **Tests liés :** — (consommé T3–T8).

- [ ] **T2. Ports in/out + properties**
  - **Outcome :** ports repository instance ; use cases admin
    (create/update/get/list/softDelete/restore) ; `ConnectorTriggerRuntime`
    (port ou classe domaine) ; `ExecuteConnectorUseCase` ;
    `ConnectorSecretCipher` (port) ; `WebhookIdempotencyStore` (port) ;
    `@ConfigurationProperties` `app.connector.crypto.*` +
    `app.connector.webhook.*` (max-body, rate-limit, idempotency-ttl).
  - **Depends on :** T1
  - **Validation :** compile ; bind yaml test.
  - **Tests liés :** Unit cipher/idempotency/harden (T4, T9).

### M2 — Persistence & crypto

- [ ] **T3. Liquibase connector tables**
  - **Outcome :** changelog
    `connector_instance` (+ `deleted_at`) + unique partiel
    `(tenant_id, name) WHERE deleted_at IS NULL` ; `tenant_connector_dek` ;
    `webhook_idempotency` ; enregistré dans master.
  - **Depends on :** T1
  - **Validation :** IT schema H2 MODE=PostgreSQL.
  - **Tests liés :** IT Liquibase/JPA.

- [ ] **T4. JPA adapters + EnvMasterKeyCipher + idempotency**
  - **Outcome :** `ConnectorInstanceJpaEntity` / adapter (encrypt on save,
    decrypt on load, filter soft-delete, `findIncludingDeleted` pour
    restore) ; `EnvMasterKeyCipher` + DEK wrap ; adapter idempotency TTL
    24h au read ; clé test dans `application-test.yml`.
  - **Depends on :** T2, T3
  - **Validation :** IT round-trip secrets chiffrés ; unit cipher.
  - **Tests liés :** Unit ConnectorSecretCipher ; IT Liquibase/JPA ;
    Security « pas de secret clair ».

### M3 — Registry & builtins trigger

- [ ] **T5. ConnectorRegistry + SimpleJsonPathMapper + http-rest TRIGGER**
  - **Outcome :** discovery `@Component` definitions ; mapper `$` / `.` /
    `[n]` ; `HttpRestTriggerConnector` mapping déclaratif → 1..N events ;
    reject `IP_ALLOWLIST` à validate.
  - **Depends on :** T1, T2
  - **Validation :** unit mapper + mapping + registry.
  - **Tests liés :** Unit Mapping ; SimpleJsonPathMapper ; Registry.

- [ ] **T6. ConnectorTriggerRuntime**
  - **Outcome :** create dossier / existing ref (`AccessContext.system`) ;
    batch TX ; `OutboxRepository.savePending` uniquement — **aucun** start
    Flowable ; `createdBy` = `trigger:{instanceId}`.
  - **Depends on :** T2, T5 ; use cases dossier existants
  - **Validation :** unit verify outbox + no start.
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

- **Next 3 tasks (phase 5) :** T1 → T2 → T3 (puis T4 ∥ T5).
- Risque sequencing : T9 execute peut appeler use case avant T10 si
  `flowable-signal` pas encore là — enchaîner T10 avant de finaliser
  execute IT signal, ou stub temporaire.
- Dep Maven resource-server dès T7.

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
